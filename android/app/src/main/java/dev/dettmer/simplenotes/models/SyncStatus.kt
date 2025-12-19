package dev.dettmer.simplenotes.models

enum class SyncStatus {
    LOCAL_ONLY,   // Noch nie gesynct
    SYNCED,       // Erfolgreich gesynct
    PENDING,      // Wartet auf Sync
    CONFLICT      // Konflikt erkannt
}
