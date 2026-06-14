package dev.dettmer.simplenotes.ui.settings.keepimport

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.noteimport.keep.KeepImportOptions
import dev.dettmer.simplenotes.noteimport.keep.KeepImportProgress
import dev.dettmer.simplenotes.noteimport.keep.KeepImportUseCase
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepPreScanResult
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipReader
import dev.dettmer.simplenotes.ui.main.MainViewModel
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v2.5.0 — Treiber für den Keep-Import-UI-Flow (Analyseplan §3.4 + §4.2).
 *
 * Verantwortlichkeiten:
 *  - State-Maschine via [KeepImportUiState].
 *  - Asynchroner PreScan beim `onZipPicked` (Reader liefert
 *    [KeepPreScanResult] für den Configuring-Dialog).
 *  - Confirmation-Step bei ZIPs >200 MB (Analyseplan §7.2).
 *  - Use-Case-Aufruf in `viewModelScope` mit `Dispatchers.IO`; Progress-
 *    Callback aktualisiert `_state` (StateFlow ist thread-safe).
 *  - Snackbar-Events nach Variante B — emittiert über `_showSnackbar`,
 *    der Host (Settings-Activity) sammelt die Events und ruft den globalen
 *    Snackbar-Host auf.
 *  - **Kein** Sync-Trigger — kommt in Commit #14.
 */
class KeepImportViewModel internal constructor(
    application: Application,
    private val useCase: KeepImportUseCase,
    private val zipReader: KeepZipReader,
    private val syncScheduler: dev.dettmer.simplenotes.sync.SyncScheduler =
        dev.dettmer.simplenotes.sync.SyncScheduler(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<KeepImportUiState>(KeepImportUiState.Idle)
    val state: StateFlow<KeepImportUiState> = _state.asStateFlow()

    /**
     * Variante B: replay=0, extraBufferCapacity=1 → typisches "fire-and-forget"-
     * Event ohne Re-Emit bei Konfigurationsänderungen.
     */
    private val _showSnackbar = MutableSharedFlow<MainViewModel.SnackbarData>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val showSnackbar: SharedFlow<MainViewModel.SnackbarData> = _showSnackbar.asSharedFlow()

    /** Aktiver Import-Job — für Cancel benötigt. */
    private var importJob: Job? = null

    // ───────────────────── User-Eingangspunkte ─────────────────────

    /**
     * Wird vom SAF-File-Picker-Result aufgerufen. Setzt sofort
     * `Configuring(scanning=true)` und startet den PreScan async.
     */
    fun onZipPicked(uri: Uri) {
        _state.value = KeepImportUiState.Configuring(zipUri = uri, preScan = null, scanning = true)
        viewModelScope.launch(ioDispatcher) {
            val scan: KeepPreScanResult? = try {
                zipReader.preScan(uri)
            } catch (e: Exception) {
                Logger.w(TAG, "preScan failed for $uri: ${e.message}")
                null
            }
            // Nur aktualisieren wenn der User den Dialog noch nicht geschlossen hat
            val current = _state.value
            if (current is KeepImportUiState.Configuring && current.zipUri == uri) {
                if (scan == null) {
                    val msg = appString(R.string.keep_import_snackbar_error)
                    _state.value = KeepImportUiState.Error(message = msg)
                    emitSnackbar(msg, retryUri = uri)
                } else {
                    _state.value = current.copy(preScan = scan, scanning = false)
                }
            }
        }
    }

    /**
     * Configuring-Dialog: User hat Optionen bestätigt. Bei großem ZIP
     * (>200 MB) wechselt der Flow erst in [KeepImportUiState.ConfirmLargeZip].
     */
    fun onConfigConfirmed(options: KeepImportOptionsHolder) {
        val current = _state.value as? KeepImportUiState.Configuring ?: return
        val scan = current.preScan ?: return // Sollte UI bereits verhindern
        if (scan.sizeBytes > LARGE_ZIP_THRESHOLD_BYTES) {
            _state.value = KeepImportUiState.ConfirmLargeZip(
                zipUri = current.zipUri,
                preScan = scan,
                options = options
            )
        } else {
            startImport(current.zipUri, scan, options)
        }
    }

    fun onLargeZipConfirmed() {
        val current = _state.value as? KeepImportUiState.ConfirmLargeZip ?: return
        startImport(current.zipUri, current.preScan, current.options)
    }

    fun onLargeZipDeclined() {
        _state.value = KeepImportUiState.Idle
    }

    fun onCancel() {
        importJob?.cancel()
        importJob = null
        _state.value = KeepImportUiState.Idle
        emitSnackbar(appString(R.string.keep_import_snackbar_cancelled))
    }

    fun onResultDismissed() {
        _state.value = KeepImportUiState.Idle
    }

    // ───────────────────── Interner Pipeline-Aufruf ─────────────────────

    private fun startImport(
        uri: Uri,
        preScan: KeepPreScanResult,
        options: KeepImportOptionsHolder
    ) {
        // Initialer Progress-Snapshot, damit der Running-Dialog sofort etwas anzeigt.
        val initialTotal = preScan.matchingCount(options.includeArchived, options.includeTrashed)
        _state.value = KeepImportUiState.Running(
            progress = KeepImportProgress(processed = 0, total = initialTotal, currentName = ""),
            cancellable = true
        )

        importJob = viewModelScope.launch(ioDispatcher) {
            try {
                val summary = useCase.import(
                    zipUri = uri,
                    options = KeepImportOptions(
                        includeArchived = options.includeArchived,
                        includeTrashed = options.includeTrashed,
                        conflictStrategy = options.conflictStrategy
                    ),
                    preScan = preScan,
                    onProgress = { p ->
                        // StateFlow ist thread-safe — kein expliziter Main-Switch nötig.
                        _state.value = KeepImportUiState.Running(progress = p, cancellable = true)
                    }
                )
                _state.value = KeepImportUiState.Done(summary = summary)
                emitSnackbar(
                    appString(
                        R.string.keep_import_snackbar_done,
                        summary.imported,
                        summary.skipped
                    )
                )
                // 🆕 v2.5.0: einmaliger Sync-Trigger am Ende (Analyseplan §7.1 Annahme #4).
                // Nur wenn tatsächlich neue/ersetzte Notizen anstehen — sonst ist der
                // Sync ein No-op und wir vermeiden unnötige Network-Requests.
                if (summary.imported > 0 || summary.replaced > 0) {
                    try {
                        syncScheduler.triggerOnSaveSync(reason = "keepImport")
                    } catch (e: Exception) {
                        Logger.w(TAG, "post-import sync trigger failed: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                // Sauber durchreichen — onCancel() hat den State bereits gesetzt.
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Keep import failed: ${e.message}")
                val msg = e.message ?: appString(R.string.keep_import_snackbar_error)
                _state.value = KeepImportUiState.Error(message = msg)
                emitSnackbar(appString(R.string.keep_import_snackbar_error), retryUri = uri)
            } finally {
                importJob = null
            }
        }
    }

    // ───────────────────── Helpers ─────────────────────

    private fun emitSnackbar(message: String, retryUri: Uri? = null) {
        val data = if (retryUri != null) {
            MainViewModel.SnackbarData(
                message = message,
                actionLabel = appString(R.string.keep_import_snackbar_retry_action),
                onAction = { onZipPicked(retryUri) }
            )
        } else {
            MainViewModel.SnackbarData(message = message)
        }
        // tryEmit reicht (extraBufferCapacity=1).
        if (!_showSnackbar.tryEmit(data)) {
            Logger.w(TAG, "snackbar emit dropped: '$message'")
        }
    }

    private fun appString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    /**
     * Hilfs-Wrapper für Tests/Off-thread-Zugriff: stellt sicher, dass
     * `runBlocking`-Tests konsistent auf einen `_state`-Update warten können.
     */
    suspend fun awaitIdleForTests() = withContext(Dispatchers.IO) { /* no-op marker */ }

    companion object {
        private const val TAG = "KeepImportViewModel"
        const val LARGE_ZIP_THRESHOLD_BYTES: Long = 200L * 1024 * 1024 // 200 MB (Analyseplan §7.2)
    }
}
