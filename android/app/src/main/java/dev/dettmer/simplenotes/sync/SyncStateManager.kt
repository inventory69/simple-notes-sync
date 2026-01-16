package dev.dettmer.simplenotes.sync

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.dettmer.simplenotes.utils.Logger

/**
 * 🔄 v1.3.1: Zentrale Verwaltung des Sync-Status
 * 
 * Verhindert doppelte Syncs und informiert die UI über den aktuellen Status.
 * Thread-safe Singleton mit LiveData für UI-Reaktivität.
 */
object SyncStateManager {
    
    private const val TAG = "SyncStateManager"
    
    /**
     * Mögliche Sync-Zustände
     */
    enum class SyncState {
        IDLE,           // Kein Sync aktiv
        SYNCING,        // Sync läuft gerade (Banner sichtbar)
        SYNCING_SILENT, // v1.5.0: Sync läuft im Hintergrund (kein Banner, z.B. onResume)
        COMPLETED,      // Sync erfolgreich abgeschlossen (kurz anzeigen)
        ERROR           // Sync fehlgeschlagen (kurz anzeigen)
    }
    
    /**
     * Detaillierte Sync-Informationen für UI
     */
    data class SyncStatus(
        val state: SyncState = SyncState.IDLE,
        val message: String? = null,
        val source: String? = null,  // "manual", "auto", "pullToRefresh", "background"
        val silent: Boolean = false, // v1.5.0: Wenn true, wird nach Completion kein Banner angezeigt
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Private mutable LiveData
    private val _syncStatus = MutableLiveData(SyncStatus())
    
    // Public immutable LiveData für Observer
    val syncStatus: LiveData<SyncStatus> = _syncStatus
    
    // Lock für Thread-Sicherheit
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
     * @param source Quelle des Syncs (für Logging)
     * @param silent v1.5.0: Wenn true, wird kein Banner angezeigt (z.B. bei onResume Auto-Sync)
     * @return true wenn Sync gestartet werden kann, false wenn bereits einer läuft
     */
    fun tryStartSync(source: String, silent: Boolean = false): Boolean {
        synchronized(lock) {
            if (isSyncing) {
                Logger.d(TAG, "⚠️ Sync already in progress, rejecting new sync from: $source")
                return false
            }
            
            val syncState = if (silent) SyncState.SYNCING_SILENT else SyncState.SYNCING
            Logger.d(TAG, "🔄 Starting sync from: $source (silent=$silent)")
            _syncStatus.postValue(
                SyncStatus(
                    state = syncState,
                    message = "Synchronisiere...",
                    source = source,
                    silent = silent  // v1.5.0: Merkt sich ob silent für markCompleted()
                )
            )
            return true
        }
    }
    
    /**
     * Markiert Sync als erfolgreich abgeschlossen
     * v1.5.0: Bei Silent-Sync direkt auf IDLE wechseln (kein Banner)
     */
    fun markCompleted(message: String? = null) {
        synchronized(lock) {
            val current = _syncStatus.value
            val currentSource = current?.source
            val wasSilent = current?.silent == true
            
            Logger.d(TAG, "✅ Sync completed from: $currentSource (silent=$wasSilent)")
            
            if (wasSilent) {
                // v1.5.0: Silent-Sync - direkt auf IDLE, kein Banner anzeigen
                _syncStatus.postValue(SyncStatus())
            } else {
                // Normaler Sync - COMPLETED State anzeigen
                _syncStatus.postValue(
                    SyncStatus(
                        state = SyncState.COMPLETED,
                        message = message,
                        source = currentSource
                    )
                )
            }
        }
    }
    
    /**
     * Markiert Sync als fehlgeschlagen
     */
    fun markError(errorMessage: String?) {
        synchronized(lock) {
            val currentSource = _syncStatus.value?.source
            Logger.e(TAG, "❌ Sync failed from: $currentSource - $errorMessage")
            _syncStatus.postValue(
                SyncStatus(
                    state = SyncState.ERROR,
                    message = errorMessage,
                    source = currentSource
                )
            )
        }
    }
    
    /**
     * Setzt Status zurück auf IDLE
     */
    fun reset() {
        synchronized(lock) {
            _syncStatus.postValue(SyncStatus())
        }
    }
    
    /**
     * Aktualisiert die Nachricht während des Syncs (z.B. Progress)
     */
    fun updateMessage(message: String) {
        synchronized(lock) {
            val current = _syncStatus.value ?: return
            if (current.state == SyncState.SYNCING) {
                _syncStatus.postValue(current.copy(message = message))
            }
        }
    }
}
