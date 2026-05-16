package dev.dettmer.simplenotes.noteimport.keep.parser

import dev.dettmer.simplenotes.noteimport.keep.model.KeepNote
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipEntry

/**
 * v2.5.0 — Wandelt ein JSON-Entry (optional plus paralleles HTML-Entry)
 * in ein [KeepNote] um.
 *
 * Vertrag:
 *  - Gibt `null` zurück, wenn das JSON ungültig oder strukturell unbrauchbar
 *    ist (z.B. fehlende Pflicht-Timestamps interpretierbar, aber JSON nicht
 *    parsbar). **Loggt** den Grund via `Logger.w`.
 *  - Wirft **keine** Exception nach außen — der Use-Case soll pro Entry
 *    weitermachen können.
 */
internal interface KeepEntryParser {
    fun parse(jsonEntry: KeepZipEntry, htmlEntry: KeepZipEntry?): KeepNote?
}
