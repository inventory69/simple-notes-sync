package dev.dettmer.simplenotes.models

/**
 * Sync-Status einer Notiz
 * 
 * v1.4.0: Initial (LOCAL_ONLY, SYNCED, PENDING, CONFLICT)
 * v1.8.0: DELETED_ON_SERVER hinzugefügt
 */
enum class SyncStatus {
    LOCAL_ONLY,        // Noch nie gesynct
    SYNCED,            // Erfolgreich gesynct
    PENDING,           // Wartet auf Sync
    CONFLICT,          // Konflikt erkannt
    DELETED_ON_SERVER  // 🆕 v1.8.0: Server hat gelöscht, lokal noch vorhanden
}
