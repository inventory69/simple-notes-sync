package dev.dettmer.simplenotes.models

/**
 * Sync-Status einer Notiz
 *
 * v1.4.0: Initial (LOCAL_ONLY, SYNCED, PENDING, CONFLICT)
 * v1.8.0: DELETED_ON_SERVER hinzugefügt
 * v2.16.0: [holdsLocalEdit] — Download-Guard gegen stilles Überschreiben
 */
enum class SyncStatus {
    LOCAL_ONLY, // Noch nie gesynct
    SYNCED, // Erfolgreich gesynct
    PENDING, // Wartet auf Sync
    CONFLICT, // Konflikt erkannt
    DELETED_ON_SERVER // 🆕 v1.8.0: Server hat gelöscht, lokal noch vorhanden
}

/**
 * 🆕 v2.16.0: Status, die eine noch nicht hochgeladene lokale Fassung halten.
 *
 * Der Download darf eine solche Notiz nie durch die Server-Version ersetzen. Vorher prüften die
 * Konflikt-Zweige nur auf [SyncStatus.PENDING] — eine bereits als [SyncStatus.CONFLICT] markierte
 * Notiz fiel in den Else-Zweig und wurde beim nächsten Sync still überschrieben. Der
 * Desktop-Client prüft an derselben Stelle `Pending | Conflict`.
 */
val SyncStatus.holdsLocalEdit: Boolean
    get() = this == SyncStatus.PENDING || this == SyncStatus.CONFLICT
