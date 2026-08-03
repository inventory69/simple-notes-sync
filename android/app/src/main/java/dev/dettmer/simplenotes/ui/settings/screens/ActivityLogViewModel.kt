package dev.dettmer.simplenotes.ui.settings.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.LogAnonymizer
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vorformatierter Eintrag fürs UI — keine Datumsformatierung mehr während der Recomposition. */
data class ActivityUiEntry(
    val entry: ActivityLog.Entry,
    val timeLabel: String,
    val dayStartMs: Long
)

/**
 * 🆕 Issue #128 Teil 3: ViewModel für den Aktivitätsprotokoll-Screen.
 *
 * Das Protokoll ist rein lokal — es verlässt das Gerät nur, wenn der Nutzer es aktiv teilt
 * (Teilen-Button, durch `LogAnonymizer`). Wer wissen will, was das andere Gerät getan hat,
 * öffnet dort denselben Screen; jedes Gerät führt sein eigenes Protokoll immer mit.
 *
 * Einträge werden per Tail-Read in wachsenden Seiten geladen, nie die ganze Datei.
 */
class ActivityLogViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ActivityLogViewModel"
        const val PAGE_SIZE = 200
    }

    private val context: Context get() = getApplication()
    private val storage = NotesStorage(application)
    private val folderStore = FolderStore(application)
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var maxLines = PAGE_SIZE

    private val _entries = MutableStateFlow<List<ActivityUiEntry>>(emptyList())
    val entries: StateFlow<List<ActivityUiEntry>> = _entries.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // Nur für den Tap-Ziel-Check (existiert die Notiz noch?) — Titel kommt aus dem Log-Eintrag,
    // kein Storage-Lookup pro Zeile nötig (Edge Case 2). Einmal geladen statt pro sichtbarer Zeile.
    private val _existingNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val existingNoteIds: StateFlow<Set<String>> = _existingNoteIds.asStateFlow()

    init {
        load()
        viewModelScope.launch(Dispatchers.IO) {
            _existingNoteIds.value = runCatching { storage.loadAllNotes().map { it.id }.toSet() }
                .getOrDefault(emptySet())
        }
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val limit = maxLines
            val raw = ActivityLog.readTail(context, limit)
            _hasMore.value = raw.size >= limit
            _entries.value = raw.map(::toUiEntry).sortedByDescending { it.entry.ts }
        }
    }

    /** Mehr Einträge nachladen (einfaches Verdoppeln statt eines stateful Cursors). */
    fun loadMore() {
        if (!_hasMore.value) return
        maxLines *= 2
        load()
    }

    fun clearLocal() {
        viewModelScope.launch(Dispatchers.IO) {
            ActivityLog.clearLocal(context)
            maxLines = PAGE_SIZE
            _hasMore.value = true
            _entries.value = emptyList()
        }
    }

    /**
     * Baut eine anonymisierte Kopie für den Teilen-Button (gleiches Muster wie
     * `SettingsViewModel.prepareLogsForSharing` — Original bleibt unangetastet).
     */
    suspend fun prepareShareFile(): File? = withContext(Dispatchers.IO) {
        val logFile = ActivityLog.getLogFile(context) ?: return@withContext null
        if (logFile.length() == 0L) return@withContext null
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        val username = CredentialStore.getUsername(context)
        val titles = runCatching { storage.loadAllNotes().map { it.title } }.getOrDefault(emptyList())
        val folders = runCatching { folderStore.loadMeta().map { it.name } }.getOrDefault(emptyList())
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val targetDir = File(context.cacheDir, "shared_logs").apply { mkdirs() }
        runCatching {
            File(targetDir, "simplenotes-activity-$stamp.txt").apply {
                writeText(LogAnonymizer.anonymize(logFile.readText(), serverUrl, username, titles, folders))
            }
        }.onFailure { Logger.w(TAG, "could not anonymize activity log for sharing: ${it.message}") }.getOrNull()
    }

    private fun toUiEntry(entry: ActivityLog.Entry): ActivityUiEntry {
        val cal = Calendar.getInstance().apply {
            timeInMillis = entry.ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ActivityUiEntry(
            entry = entry,
            timeLabel = timeFormat.format(Date(entry.ts)),
            dayStartMs = cal.timeInMillis
        )
    }
}

/** Locale-formatiertes Datum für einen Tages-Header (z. B. "26. Juli 2026"). */
fun formatDayHeader(dayStartMs: Long): String =
    DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(Date(dayStartMs))
