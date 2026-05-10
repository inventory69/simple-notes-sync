package dev.dettmer.simplenotes.noteimport.keep.parser

/**
 * v2.5.0 — Konvertiert Keep-Mikrosekunden-Timestamps in Millisekunden.
 *
 * Eigene Datei, weil Tests #17-19 (Faktor / 0 / Long-Range-Edge) den
 * Faktor isoliert verifizieren müssen, ohne den Parser bemühen zu müssen.
 */
internal object TimestampMapper {
    private const val USEC_PER_MS: Long = 1_000L

    /**
     * Mikrosekunden → Millisekunden. Negativwerte (in der Praxis nicht
     * vorgesehen) werden durchgereicht; `null` ⇒ `0L`.
     */
    fun usecToMs(usec: Long?): Long {
        if (usec == null) return 0L
        // Long-Division ist immer sicher: max. Long / 1000 bleibt im Long-Bereich.
        return usec / USEC_PER_MS
    }
}
