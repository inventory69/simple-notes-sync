package dev.dettmer.simplenotes.noteimport.keep.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v2.5.0 — Tests #17, #18, #19 aus Analyseplan §5.1.
 */
class TimestampMapperTest {
    // #17 — Mikro→Milli expliziter Faktor 1_000
    @Test
    fun `usecToMs_factor1000`() {
        assertEquals(1_700_000_000_000L, TimestampMapper.usecToMs(1_700_000_000_000_000L))
        assertEquals(1L, TimestampMapper.usecToMs(1_000L))
    }

    // #18 — 0 / null → 0
    @Test
    fun `usecZero_returnsZero`() {
        assertEquals(0L, TimestampMapper.usecToMs(0L))
        assertEquals(0L, TimestampMapper.usecToMs(null))
    }

    // #19 — Long.MAX-nahe Werte überlaufen nicht
    @Test
    fun `usecVeryLarge_doesNotOverflow`() {
        val veryLarge = 9_999_999_999_999_999L
        // 9_999_999_999_999_999 / 1000 = 9_999_999_999_999
        assertEquals(9_999_999_999_999L, TimestampMapper.usecToMs(veryLarge))
        // Long.MAX_VALUE / 1000
        assertEquals(Long.MAX_VALUE / 1_000L, TimestampMapper.usecToMs(Long.MAX_VALUE))
    }
}
