package dev.dettmer.simplenotes.sync

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
    private val _syncStatus = MutableLiveData(SyncStatus())
    val syncStatus: LiveData<SyncStatus> = _syncStatus
    
    // 🆕 v1.8.0: Einziges Banner-System - SyncProgress
    private val _syncProgress = MutableStateFlow(SyncProgress.IDLE)
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    
    private val lock = Any()
    
    /**
     * Prüft ob gerade ein Sync läuft (inkl. Silent-Sync)
     */
    val isSyncing: Boolean
        get() {
            val state = _syncStatus.value?.state
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
                Logger.d(TAG, "⚠️ Sync already in progress, rejecting from: $source")
                return false
            }
            
            val syncState = if (silent) SyncState.SYNCING_SILENT else SyncState.SYNCING
            Logger.d(TAG, "🔄 Starting sync from: $source (silent=$silent)")
            
            _syncStatus.postValue(
                SyncStatus(
                    state = syncState,
                    source = source,
                    silent = silent
                )
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
            val wasSilent = current?.silent == true
            val currentSource = current?.source
            
            Logger.d(TAG, "✅ Sync completed from: $currentSource (silent=$wasSilent)")
            
            if (wasSilent) {
                // Silent-Sync: Direkt auf IDLE - kein Banner
                _syncStatus.postValue(SyncStatus())
                _syncProgress.value = SyncProgress.IDLE
            } else {
                // Normaler Sync: COMPLETED mit Nachricht anzeigen
                _syncStatus.postValue(
                    SyncStatus(state = SyncState.COMPLETED, message = message, source = currentSource)
                )
                _syncProgress.value = SyncProgress(
                    phase = SyncPhase.COMPLETED,
                    resultMessage = message
                )
            }
        }
    }
    
    /**
     * Markiert Sync als fehlgeschlagen
     * Bei Silent-Sync: Fehler trotzdem anzeigen (wichtig für User)
     */
    fun markError(errorMessage: String?) {
        synchronized(lock) {
            val current = _syncStatus.value
            val wasSilent = current?.silent == true
            val currentSource = current?.source
            
            Logger.e(TAG, "❌ Sync failed from: $currentSource - $errorMessage")
            
            _syncStatus.postValue(
                SyncStatus(state = SyncState.ERROR, message = errorMessage, source = currentSource)
            )
            
            // Fehler immer anzeigen (auch bei Silent-Sync)
            _syncProgress.value = SyncProgress(
                phase = SyncPhase.ERROR,
                resultMessage = errorMessage,
                silent = false  // Fehler nie silent
            )
        }
    }
    
    /**
     * Setzt alles zurück auf IDLE
     */
    fun reset() {
        synchronized(lock) {
            _syncStatus.postValue(SyncStatus())
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
    fun updateProgress(
        phase: SyncPhase,
        current: Int = 0,
        total: Int = 0,
        currentFileName: String? = null
    ) {
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
}
