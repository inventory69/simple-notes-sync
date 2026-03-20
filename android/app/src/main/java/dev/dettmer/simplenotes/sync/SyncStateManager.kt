package dev.dettmer.simplenotes.sync

import androidx.core.content.edit
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🔄 v1.3.1: Zentrale Verwaltung des Sync-Status
 * 🆕 v1.8.0: Komplett überarbeitet - SyncProgress ist jetzt das einzige Banner-System
 *
 * SyncProgress (StateFlow) steuert den gesamten Sync-Lebenszyklus:
 * PREPARING → [UPLOADING] → [DOWNLOADING] → [IMPORTING_MARKDOWN] → COMPLETED/ERROR → IDLE
 *
 * SyncStatus (LiveData) wird nur noch intern für Mutex/Silent-Tracking verwendet.
 */
object SyncStateManager {
    private const val TAG = "SyncStateManager"

    /** 🆕 v1.8.2: Maximale Dauer eines Syncs bevor er als "stuck" gilt (5 Minuten) */
    private const val SYNC_TIMEOUT_MS = 5 * 60 * 1000L
    private const val QUOTA_STOP_MAX_AGE_MS = 86400000L // 24 hours

    /**
     * Mögliche Sync-Zustände (intern für Mutex + PullToRefresh)
     */
    enum class SyncState {
        IDLE,
        SYNCING,
        SYNCING_SILENT,
        COMPLETED,
        ERROR
    }

    /**
     * Interne Sync-Informationen (für Mutex-Management + Silent-Tracking)
     */
    data class SyncStatus(
        val state: SyncState = SyncState.IDLE,
        val message: String? = null,
        val source: String? = null,
        val silent: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Intern: Mutex + PullToRefresh State
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // 🆕 v1.8.0: Einziges Banner-System - SyncProgress
    private val _syncProgress = MutableStateFlow(SyncProgress.IDLE)
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val lock = Any()

    /**
     * Prüft ob gerade ein Sync läuft (inkl. Silent-Sync)
     */
    val isSyncing: Boolean
        get() {
            val state = _syncStatus.value.state
            return state == SyncState.SYNCING || state == SyncState.SYNCING_SILENT
        }

    /**
     * Versucht einen Sync zu starten.
     * Bei silent=false: Setzt sofort PREPARING-Phase → Banner erscheint instant
     * Bei silent=true: Setzt silent-Flag → kein Banner wird angezeigt
     */
    fun tryStartSync(source: String, silent: Boolean = false): Boolean {
        synchronized(lock) {
            if (isSyncing) {
                // 🆕 v1.8.2: Timeout-Check für verwaiste Sync-States
                val syncStartTime = _syncProgress.value.startTime
                val elapsed = System.currentTimeMillis() - syncStartTime

                if (syncStartTime > 0 && elapsed > SYNC_TIMEOUT_MS) {
                    Logger.e(
                        TAG,
                        "⏰ Stale sync detected (${elapsed / 1000}s old from: ${_syncStatus.value.source}) - force-resetting"
                    )
                    _syncStatus.value = SyncStatus()
                    _syncProgress.value = SyncProgress.IDLE
                    // Fall-through: Neuer Sync wird unten gestartet
                } else {
                    Logger.d(TAG, "⚠️ Sync already in progress, rejecting from: $source")
                    return false
                }
            }

            val syncState = if (silent) SyncState.SYNCING_SILENT else SyncState.SYNCING
            Logger.d(TAG, "🔄 Starting sync from: $source (silent=$silent)")

            _syncStatus.value = SyncStatus(
                state = syncState,
                source = source,
                silent = silent
            )

            // 🆕 v1.8.0: Sofort PREPARING-Phase setzen (Banner erscheint instant)
            _syncProgress.value = SyncProgress(
                phase = SyncPhase.PREPARING,
                silent = silent,
                startTime = System.currentTimeMillis()
            )

            return true
        }
    }

    /**
     * Markiert Sync als erfolgreich abgeschlossen
     * Bei Silent-Sync: direkt auf IDLE (kein Banner)
     * Bei normalem Sync: COMPLETED mit Nachricht (auto-hide durch UI)
     */
    fun markCompleted(message: String? = null) {
        synchronized(lock) {
            val current = _syncStatus.value
            val wasSilent = current.silent
            val currentSource = current.source

            Logger.d(TAG, "✅ Sync completed from: $currentSource (silent=$wasSilent)")

            if (wasSilent) {
                // Silent-Sync: Direkt auf IDLE - kein Banner
                _syncStatus.value = SyncStatus()
                _syncProgress.value = SyncProgress.IDLE
            } else {
                // Normaler Sync: COMPLETED mit Nachricht anzeigen
                _syncStatus.value = SyncStatus(state = SyncState.COMPLETED, message = message, source = currentSource)
                _syncProgress.value = SyncProgress(
                    phase = SyncPhase.COMPLETED,
                    resultMessage = message
                )
            }
        }
    }

    /**
     * 🛡️ v1.8.2 (IMPL_24): Silent-Sync auf sichtbar promoten
     * Wird aufgerufen wenn User Pull-to-Refresh macht während ein Silent-Sync läuft.
     * Zeigt dem User das Sync-Banner mit aktuellem Progress.
     *
     * @return true wenn ein Silent-Sync promoted wurde, false wenn kein Sync läuft
     */
    fun promoteToVisible(): Boolean {
        synchronized(lock) {
            val current = _syncStatus.value

            if (current.state != SyncState.SYNCING_SILENT) return false

            Logger.d(TAG, "📢 Promoting silent sync to visible (user pulled to refresh)")

            _syncStatus.value = current.copy(
                state = SyncState.SYNCING,
                silent = false
            )

            // Progress-Banner sichtbar machen
            val currentProgress = _syncProgress.value
            _syncProgress.value = currentProgress.copy(silent = false)

            return true
        }
    }

    /**
     * Markiert Sync als fehlgeschlagen
     * Bei Silent-Sync: Fehler trotzdem anzeigen (wichtig für User)
     */
    fun markError(errorMessage: String?) {
        synchronized(lock) {
            val current = _syncStatus.value
            val wasSilent = current.silent
            val currentSource = current.source

            Logger.e(TAG, "❌ Sync failed from: $currentSource - $errorMessage")

            _syncStatus.value = SyncStatus(state = SyncState.ERROR, message = errorMessage, source = currentSource)

            // Fehler immer anzeigen (auch bei Silent-Sync)
            _syncProgress.value = SyncProgress(
                phase = SyncPhase.ERROR,
                resultMessage = errorMessage,
                silent = false // Fehler nie silent
            )
        }
    }

    /**
     * Setzt alles zurück auf IDLE
     */
    fun reset() {
        synchronized(lock) {
            _syncStatus.value = SyncStatus()
            _syncProgress.value = SyncProgress.IDLE
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.8.0: Detailliertes Progress-Tracking (während syncNotes())
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Aktualisiert den detaillierten Sync-Fortschritt
     * Behält silent-Flag und startTime der aktuellen Session bei
     */
    fun updateProgress(phase: SyncPhase, current: Int = 0, total: Int = 0, currentFileName: String? = null) {
        synchronized(lock) {
            val existing = _syncProgress.value
            _syncProgress.value = SyncProgress(
                phase = phase,
                current = current,
                total = total,
                currentFileName = currentFileName,
                silent = existing.silent,
                startTime = existing.startTime
            )
        }
    }

    /**
     * Inkrementiert den Fortschritt um 1
     * Praktisch für Schleifen: nach jedem tatsächlichen Download
     */
    fun incrementProgress(currentFileName: String? = null) {
        synchronized(lock) {
            val current = _syncProgress.value
            _syncProgress.value = current.copy(
                current = current.current + 1,
                currentFileName = currentFileName
            )
        }
    }

    /**
     * Setzt Progress zurück auf IDLE (am Ende von syncNotes())
     * Wird NICHT für COMPLETED/ERROR verwendet - nur für Cleanup
     */
    fun resetProgress() {
        // Nicht zurücksetzen wenn COMPLETED/ERROR - die UI braucht den State noch für auto-hide
        synchronized(lock) {
            val current = _syncProgress.value
            if (current.phase != SyncPhase.COMPLETED && current.phase != SyncPhase.ERROR) {
                _syncProgress.value = SyncProgress.IDLE
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.8.1 (IMPL_08): Globaler Sync-Cooldown
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prüft ob seit dem letzten erfolgreichen Sync-Start genügend Zeit vergangen ist.
     * Wird von ALLEN Sync-Triggern als erste Prüfung aufgerufen.
     *
     * @return true wenn ein neuer Sync erlaubt ist
     */
    fun canSyncGlobally(prefs: android.content.SharedPreferences): Boolean {
        val lastGlobalSync = prefs.getLong(dev.dettmer.simplenotes.utils.Constants.KEY_LAST_GLOBAL_SYNC_TIME, 0)
        val now = System.currentTimeMillis()
        val elapsed = now - lastGlobalSync

        if (elapsed < dev.dettmer.simplenotes.utils.Constants.MIN_GLOBAL_SYNC_INTERVAL_MS) {
            val remainingSec = (dev.dettmer.simplenotes.utils.Constants.MIN_GLOBAL_SYNC_INTERVAL_MS - elapsed) / 1000
            dev.dettmer.simplenotes.utils.Logger.d(TAG, "⏳ Global sync cooldown active - wait ${remainingSec}s")
            return false
        }
        return true
    }

    /**
     * Markiert den aktuellen Zeitpunkt als letzten Sync-Start (global).
     * Aufzurufen wenn ein Sync tatsächlich startet (nach allen Checks).
     */
    fun markGlobalSyncStarted(prefs: android.content.SharedPreferences) {
        prefs.edit {
            putLong(dev.dettmer.simplenotes.utils.Constants.KEY_LAST_GLOBAL_SYNC_TIME, System.currentTimeMillis())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.8.1 (IMPL_12): Info-Meldungen über das Banner-System
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Zeigt eine kurzfristige Info-Meldung im Banner an.
     * Wird für nicht-sync-bezogene Benachrichtigungen verwendet
     * (z.B. Server-Delete-Ergebnisse).
     *
     * ACHTUNG: Wenn gerade ein Sync läuft (isSyncing), wird die Meldung
     * ignoriert — der Sync-Progress hat Vorrang.
     *
     * Auto-Hide erfolgt über ComposeMainActivity (2.5s).
     */
    fun showInfo(message: String) {
        synchronized(lock) {
            // Nicht während aktivem Sync anzeigen — Sync-Fortschritt hat Vorrang
            if (isSyncing) {
                Logger.d(TAG, "ℹ️ Info suppressed during sync: $message")
                return
            }

            _syncProgress.value = SyncProgress(
                phase = SyncPhase.INFO,
                resultMessage = message,
                silent = false // INFO ist nie silent
            )

            Logger.d(TAG, "ℹ️ Showing info: $message")
        }
    }

    /**
     * Zeigt eine Fehlermeldung im Banner an, auch außerhalb eines Syncs.
     * Für nicht-sync-bezogene Fehler (z.B. Server-Delete fehlgeschlagen).
     *
     * Auto-Hide erfolgt über ComposeMainActivity (4s).
     */
    fun showError(message: String?) {
        synchronized(lock) {
            // Nicht während aktivem Sync anzeigen
            if (isSyncing) {
                Logger.d(TAG, "❌ Error suppressed during sync: $message")
                return
            }

            _syncProgress.value = SyncProgress(
                phase = SyncPhase.ERROR,
                resultMessage = message,
                silent = false
            )

            Logger.e(TAG, "❌ Showing error: $message")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 🆕 v1.10.0-P2: WorkManager Quota / App Standby stop tracking
    // ═══════════════════════════════════════════════════════════════════════════

    private var lastQuotaStopReason: String? = null
    private var lastQuotaStopTimestamp: Long = 0L

    /**
     * Records that a sync was stopped due to quota/standby restrictions (API 31+).
     * The UI queries this on next resume to show a one-time hint.
     */
    fun recordQuotaStop(reason: String) {
        lastQuotaStopReason = reason
        lastQuotaStopTimestamp = System.currentTimeMillis()
        Logger.w(TAG, "⚠️ Sync stopped by system quota/standby: $reason")
    }

    /**
     * Returns the last quota-stop reason if it occurred within the last 24 hours.
     * Show-once: calling this clears the flag. Returns null if no recent stop.
     */
    fun consumeQuotaStopNotification(): String? {
        val reason = lastQuotaStopReason ?: return null
        val elapsed = System.currentTimeMillis() - lastQuotaStopTimestamp
        if (elapsed > QUOTA_STOP_MAX_AGE_MS) return null
        lastQuotaStopReason = null
        lastQuotaStopTimestamp = 0L
        return reason
    }
}
