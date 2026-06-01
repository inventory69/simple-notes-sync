package dev.dettmer.simplenotes.utils

/**
 * 🆕 v2.7.0 (Folders): Validierung & Sanitisierung von Ordnernamen.
 *
 * Single-Level-Ordner: keine Pfadtrenner, keine relativen Segmente. Wird sowohl im
 * Erstell-Dialog (User-Eingabe) als auch als Sanitizer für per Server-PROPFIND entdeckte
 * Verzeichnisnamen verwendet.
 */
object FolderNameValidator {
    const val MAX_LENGTH = 64
    private const val MIN_PRINTABLE_CODE = 0x20

    private val FORBIDDEN = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /** True, wenn [name] ein gültiger, vom User eingegebener Ordnername ist. */
    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return false
        if (trimmed == "." || trimmed == "..") return false
        return trimmed.none { it in FORBIDDEN || it.code < MIN_PRINTABLE_CODE }
    }

    /**
     * Macht einen vom Server (PROPFIND) entdeckten Verzeichnisnamen sicher verwendbar.
     * Gibt null zurück, wenn nach Bereinigung kein gültiger Name übrig bleibt.
     */
    fun sanitize(raw: String): String? {
        val decoded = raw.trim().trim('/')
        if (decoded.isEmpty()) return null
        val cleaned = decoded
            .filterNot { it in FORBIDDEN || it.code < MIN_PRINTABLE_CODE }
            .take(MAX_LENGTH)
            .trim()
        return cleaned.takeIf { it.isNotEmpty() && it != "." && it != ".." }
    }
}
