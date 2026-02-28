package dev.dettmer.simplenotes.sync

/**
 * 🆕 v1.8.0: Detaillierter Sync-Fortschritt für UI
 * 
 * Einziges Banner-System für den gesamten Sync-Lebenszyklus:
 * - PREPARING: Sofort beim Klick, bleibt während Vor-Checks und Server-Prüfung
 * - UPLOADING / DOWNLOADING / IMPORTING_MARKDOWN: Nur bei echten Aktionen
 * - COMPLETED / ERROR: Ergebnis mit Nachricht + Auto-Hide
 * 
 * Ersetzt das alte duale Banner-System (SyncStatusBanner + SyncProgressBanner)
 */
data class SyncProgress(
    val phase: SyncPhase = SyncPhase.IDLE,
    val current: Int = 0,
    val total: Int = 0,
    val currentFileName: String? = null,
    val resultMessage: String? = null,
    val silent: Boolean = false,
    val startTime: Long = System.currentTimeMillis()
) {
    /**
     * Fortschritt als Float zwischen 0.0 und 1.0
     */
    val progress: Float
        get() = if (total > 0) current.toFloat() / total else 0f
    
    /**
     * Fortschritt als Prozent (0-100)
     */
    val percentComplete: Int
        get() = (progress * 100).toInt()
    
    /**
     * Vergangene Zeit seit Start in Millisekunden
     */
    val elapsedMs: Long
        get() = System.currentTimeMillis() - startTime
    
    /**
     * Geschätzte verbleibende Zeit in Millisekunden
     * Basiert auf durchschnittlicher Zeit pro Item
     */
    val estimatedRemainingMs: Long?
        get() {
            if (current == 0 || total == 0) return null
            val avgTimePerItem = elapsedMs / current
            val remaining = total - current
            return avgTimePerItem * remaining
        }
    
    /**
     * Ob das Banner sichtbar sein soll
     * Silent syncs zeigen nie ein Banner
     * 🆕 v1.8.1 (IMPL_12): INFO ist immer sichtbar (nicht vom silent-Flag betroffen)
     */
    val isVisible: Boolean
        get() = phase == SyncPhase.INFO || (!silent && phase != SyncPhase.IDLE)
    
    /**
     * Ob gerade ein aktiver Sync läuft (mit Spinner)
     */
    val isActiveSync: Boolean
        get() = phase in listOf(
            SyncPhase.PREPARING,
            SyncPhase.UPLOADING,
            SyncPhase.DOWNLOADING,
            SyncPhase.DELETING,
            SyncPhase.IMPORTING_MARKDOWN
        )
    
    companion object {
        val IDLE = SyncProgress(phase = SyncPhase.IDLE)
    }
}

/**
 * 🆕 v1.8.0: Sync-Phasen für detailliertes Progress-Tracking
 */
enum class SyncPhase {
    /** Kein Sync aktiv */
    IDLE,
    
    /** Sync wurde gestartet, Vor-Checks laufen (hasUnsyncedChanges, isReachable, Server-Verzeichnis) */
    PREPARING,
    
    /** Lädt lokale Änderungen auf den Server hoch */
    UPLOADING,
    
    /** Lädt Server-Änderungen herunter */
    DOWNLOADING,

    /** 🆕 v1.10.0-P2: Löscht Notizen vom Server (Batch-Deletion mit Fortschrittsanzeige) */
    DELETING,

    /** Importiert Markdown-Dateien vom Server */
    IMPORTING_MARKDOWN,
    
    /** Sync erfolgreich abgeschlossen */
    COMPLETED,
    
    /** Sync mit Fehler abgebrochen */
    ERROR,
    
    /** 🆕 v1.8.1 (IMPL_12): Kurzfristige Info-Meldung (nicht sync-bezogen) */
    INFO
}
