package dev.dettmer.simplenotes.noteimport.keep.model

/**
 * Lookup-Tabelle Keep-Farbname → `#RRGGBB`.
 *
 * v2.5.0: vom Mapper (Commit #7) genutzt, um `Note.color` zu setzen.
 * Werte angelehnt an Keep-Web-UI (Material-Tönungen aus 2025-Export).
 *
 * Unbekannte / `"DEFAULT"`-Farben → `null` (kein Override der App-Standardfarbe).
 */
object KeepColor {
    private val MAP = mapOf(
        "DEFAULT" to null,
        "RED" to "#F28B82",
        "ORANGE" to "#FBBC04",
        "YELLOW" to "#FFF475",
        "GREEN" to "#CCFF90",
        "TEAL" to "#A7FFEB",
        "BLUE" to "#CBF0F8",
        "DARK_BLUE" to "#AECBFA",
        "PURPLE" to "#D7AEFB",
        "PINK" to "#FDCFE8",
        "BROWN" to "#E6C9A8",
        "GRAY" to "#E8EAED"
    )

    /**
     * @param keepColorName Roh-Wert aus `KeepNote.color`. Case-insensitiv.
     * @return Hex `#RRGGBB`, oder `null` wenn unbekannt / `"DEFAULT"`.
     */
    fun toHex(keepColorName: String?): String? {
        if (keepColorName.isNullOrBlank()) return null
        return MAP[keepColorName.uppercase()]
    }
}
