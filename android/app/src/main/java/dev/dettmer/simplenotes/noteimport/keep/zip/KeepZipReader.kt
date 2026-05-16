package dev.dettmer.simplenotes.noteimport.keep.zip

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * v2.5.0 — Streamender Lese-Zugriff auf ein Keep-Takeout-ZIP.
 *
 * **Memory-Profil**: jeder Entry wird einzeln in den Heap gelesen
 * (Bytes des aktuellen Entries), kein Vor-Index sämtlicher Bytes.
 * Für die `.html`-Geschwister (Commit #7) wird ein dünner
 * `Map<basename, KeepZipEntry>` im Use-Case gehalten — nicht hier.
 */
interface KeepZipReader {

    /**
     * Klassifizierender Pass über das ZIP. Liest jeden JSON-Entry
     * vollständig, parst nur die für die Klassifikation nötigen Felder
     * (`isArchived`, `isTrashed`, `labels`, `sharees`, `attachments`),
     * und summiert. Die rohen Bytes werden **nicht** zurückgegeben.
     *
     * Wird vor dem [readEntries]-Pass aufgerufen.
     *
     * Nicht-Keep-Dateien (z.B. `Takeout/index.html`, `Labels.txt`,
     * Bilder) werden ignoriert. `Labels.txt` wird **nicht** als
     * Notiz mitgezählt — Labels werden ausschließlich über die
     * `labels[]`-Arrays in den JSONs ermittelt (Single-Source-of-Truth).
     */
    suspend fun preScan(uri: Uri): KeepPreScanResult

    /**
     * Streamt alle Keep-relevanten Entries (`.json` + `.html` im
     * `Keep/`-Verzeichnis). Andere Dateien (Bilder, Labels.txt,
     * index.html …) werden **nicht** emittiert.
     *
     * Reihenfolge: ZIP-Reihenfolge — der Aufrufer muss `.json`-Entries
     * mit ihren `.html`-Geschwistern selbst korrelieren (Commit #10).
     */
    suspend fun readEntries(uri: Uri): Flow<KeepZipEntry>
}
