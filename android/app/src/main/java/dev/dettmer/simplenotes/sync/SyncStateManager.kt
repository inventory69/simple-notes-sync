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
        SYNCING,        // Sync läuft gerade
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
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Private mutable LiveData
    private val _syncStatus = MutableLiveData(SyncStatus())
    
    // Public immutable LiveData für Observer
    val syncStatus: LiveData<SyncStatus> = _syncStatus
    
    // Lock für Thread-Sicherheit
    private val lock = Any()
    
    /**
     * Prüft ob gerade ein Sync läuft
     */
    val isSyncing: Boolean
        get() = _syncStatus.value?.state == SyncState.SYNCING
    
    /**
     * Versucht einen Sync zu starten.
     * @return true wenn Sync gestartet werden kann, false wenn bereits einer läuft
     */
    fun tryStartSync(source: String): Boolean {
        synchronized(lock) {
            if (isSyncing) {
                Logger.d(TAG, "⚠️ Sync already in progress, rejecting new sync from: $source")
                return false
            }
            
            Logger.d(TAG, "🔄 Starting sync from: $source")
            _syncStatus.postValue(
                SyncStatus(
                    state = SyncState.SYNCING,
                    message = "Synchronisiere...",
                    source = source
                )
            )
            return true
        }
    }
    
    /**
     * Markiert Sync als erfolgreich abgeschlossen
     */
    fun markCompleted(message: String? = null) {
        synchronized(lock) {
            val currentSource = _syncStatus.value?.source
            Logger.d(TAG, "✅ Sync completed from: $currentSource")
            _syncStatus.postValue(
                SyncStatus(
                    state = SyncState.COMPLETED,
                    message = message,
                    source = currentSource
                )
            )
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
