package dev.dettmer.simplenotes.sync

/**
 * Mark-and-Sweep-Entscheidung für Bild-Attachments. Pure Funktion (kein I/O) — Assets sind
 * vollständig aus dem Notiz-Korpus ableitbar, es gibt keinen `deletions.json`-Eintrag für sie.
 */
object AssetGc {
    /** Schutzfrist gegen Cross-Device-Races: Asset A hochgeladen, Notiz-Upload von Gerät B
     * mit der Referenz auf A noch unterwegs — A darf nicht als Orphan gelten, bevor B fertig ist. */
    const val GRACE_PERIOD_MS = 72 * 60 * 60 * 1000L

    data class Targets(val localToDelete: Set<String>, val remoteToDelete: Set<String>)

    /**
     * @param referenced Alle `.assets/<name>`-Referenzen aus dem kompletten Notiz-Korpus
     *   (inkl. Trash + Archiv, siehe [dev.dettmer.simplenotes.utils.AssetReferences]).
     * @param localMtimes Name → letzte Änderungszeit (ms epoch) aller lokal vorhandenen Asset-Dateien.
     * @param serverMtimes Name → Server-Last-Modified (ms epoch, null wenn unbekannt) aller
     *   Dateien im `-assets/`-Ordner (aus einem einzelnen PROPFIND).
     * @param allowRemoteSweep Analog `ALL_DELETED_GUARD_THRESHOLD`: der Aufrufer setzt dies auf
     *   `false`, wenn die Notizliste leer ist oder die Download-Phase fehlgeschlagen ist — sonst
     *   würde ein kaputter Sync-Zyklus serverseitige Assets aller Geräte fälschlich löschen.
     */
    fun computeTargets(
        referenced: Set<String>,
        localMtimes: Map<String, Long>,
        serverMtimes: Map<String, Long?>,
        now: Long,
        allowRemoteSweep: Boolean,
        graceMs: Long = GRACE_PERIOD_MS
    ): Targets {
        val localToDelete = localMtimes
            .filterKeys { it !in referenced }
            .filterValues { now - it > graceMs }
            .keys

        val remoteToDelete = if (!allowRemoteSweep) {
            emptySet()
        } else {
            serverMtimes
                .filterKeys { it !in referenced }
                .filter { (_, mtime) -> mtime != null && now - mtime > graceMs }
                .keys
        }

        return Targets(localToDelete, remoteToDelete)
    }
}
