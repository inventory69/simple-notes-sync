package dev.dettmer.simplenotes.ui.editor

import android.content.SharedPreferences
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/** Round-trip test for the persisted calendar-parsing strategy. */
class CalendarParsingStrategyPrefsTest {
    private var stored: String? = null
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true) {
        every { putString(Constants.KEY_CALENDAR_PARSING_STRATEGY, any()) } answers {
            stored = secondArg()
            self as SharedPreferences.Editor
        }
    }
    private val prefs = mockk<SharedPreferences> {
        every { getString(Constants.KEY_CALENDAR_PARSING_STRATEGY, Strategy.RAW.name) } answers { stored ?: Strategy.RAW.name }
        every { edit() } returns editor
    }

    @Test
    fun `default strategy is RAW when nothing stored`() {
        assertEquals(Strategy.RAW, prefs.calendarParsingStrategy())
    }

    @Test
    fun `set persists and reads back`() {
        prefs.setCalendarParsingStrategy(Strategy.LABEL_PREFIX)
        assertEquals(Strategy.LABEL_PREFIX, prefs.calendarParsingStrategy())
    }
}
