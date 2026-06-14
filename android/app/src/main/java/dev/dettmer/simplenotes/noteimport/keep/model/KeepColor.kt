package dev.dettmer.simplenotes.noteimport.keep.model

import dev.dettmer.simplenotes.utils.Logger

/**
 * Lookup-Tabelle Keep-Farbname → `#RRGGBB`.
 *
 * v2.5.0: vom Mapper genutzt, um `Note.color` zu setzen.
 * Werte angelehnt an Keep-Web-UI (Material-Tönungen aus 2025-Export).
 *
 * Unbekannte / `"DEFAULT"`-Farben → `null` (kein Override der App-Standardfarbe).
 *
 * **Vollständige Keep-Palette:** laut gkeepapi (reverse-engineered Keep-API,
 * [ColorValue-Enum](https://github.com/kiwiz/gkeepapi)) sind die 12 gültigen
 * Keep-Farbnamen: DEFAULT, RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, CERULEAN,
 * PURPLE, PINK, BROWN, GRAY.  `CERULEAN` ist der offizielle API-Name für
 * "Dunkelblau"; `DARK_BLUE` taucht in echten Takeout-Exporten vermutlich nicht
 * auf, wird aber defensiv als Alias geführt.
 *
 * Wenn ein zukünftiges Takeout einen unbekannten Farbnamen liefert, schlägt
 * [toHex] ein `Logger.w` aus — damit fallen Lücken im Logcat sofort auf.
 */
object KeepColor {
    /**
     * Kanonische Keep-API-Farbnamen → App-Palette-Hex (1:1-Zuordnung).
     *
     * Reihenfolge entspricht der Keep-Palette (gkeepapi `ColorValue`-Enum).
     */
    private val CANONICAL = mapOf(
        "DEFAULT" to null,
        "RED" to "#F28B82",
        "ORANGE" to "#FBBC04",
        "YELLOW" to "#FFF475",
        "GREEN" to "#CCFF90",
        "TEAL" to "#A7FFEB",
        "BLUE" to "#CBF0F8",
        "CERULEAN" to "#AECBFA", // Keep-API-Name für "Dunkelblau"
        "PURPLE" to "#D7AEFB",
        "PINK" to "#FDCFE8",
        "BROWN" to "#E6C9A8",
        "GRAY" to "#E8EAED"
    )

    /**
     * Defensive Aliase für Namen, die in echten Takeout-Exporten
     * vermutlich nicht vorkommen, aber vorsorglich abgedeckt werden.
     *
     * - `DARK_BLUE`: kein offizieller Keep-API-Name; könnte in sehr alten
     *   oder manuell erstellten Exporten auftauchen. Wird auf den
     *   CERULEAN-Slot (#AECBFA) abgebildet.
     */
    private val ALIAS = mapOf(
        "DARK_BLUE" to "#AECBFA"
    )

    private val MAP: Map<String, String?> = CANONICAL + ALIAS

    /**
     * @param keepColorName Roh-Wert aus `KeepNote.color`. Case-insensitiv.
     * @return Hex `#RRGGBB`, oder `null` wenn unbekannt / `"DEFAULT"`.
     */
    fun toHex(keepColorName: String?): String? {
        if (keepColorName.isNullOrBlank()) return null
        val normalized = keepColorName.uppercase()
        if (!MAP.containsKey(normalized)) {
            Logger.w(
                TAG,
                "Unknown Keep color '$keepColorName' — falling back to no color. " +
                    "Consider adding it to KeepColor.ALIAS."
            )
            return null
        }
        return MAP[normalized]
    }

    private const val TAG = "KeepColor"
}
