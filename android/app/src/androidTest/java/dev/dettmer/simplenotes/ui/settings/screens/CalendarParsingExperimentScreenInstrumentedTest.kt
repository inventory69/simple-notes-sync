package dev.dettmer.simplenotes.ui.settings.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dettmer.simplenotes.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test for the calendar-parsing experiment screen: drives the
 * real Compose UI (strategy selection + text input) and checks the preview output.
 * Uses test tags on the preview rows since the raw input and the parsed output
 * otherwise share substrings, which would make plain text matchers ambiguous.
 *
 * Run via adb:
 *   adb shell am instrument -w -r \
 *     -e class dev.dettmer.simplenotes.ui.settings.screens.CalendarParsingExperimentScreenInstrumentedTest \
 *     dev.dettmer.simplenotes.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class CalendarParsingExperimentScreenInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = context.getString(id)

    private fun inputField() = composeTestRule.onNode(hasSetTextAction())

    private fun setInput(text: String) {
        inputField().performTextClearance()
        inputField().performTextInput(text)
    }

    @Test fun default_strategy_is_raw_and_shows_whole_example_as_title() {
        composeTestRule.setContent { CalendarParsingExperimentScreen(onBack = {}) }

        composeTestRule.onNodeWithTag(TAG_PREVIEW_TITLE)
            .assertTextEquals(string(R.string.calendar_experiment_input_placeholder))
        composeTestRule.onNodeWithTag(TAG_PREVIEW_LOCATION).assertTextEquals("—")
    }

    @Test fun positional_strategy_splits_input_into_title_location_description() {
        composeTestRule.setContent { CalendarParsingExperimentScreen(onBack = {}) }

        setInput("Max Mustermann / Musterstr. 5 / Fenster reparieren")
        composeTestRule.onNodeWithText(string(R.string.calendar_experiment_strategy_positional)).performClick()

        composeTestRule.onNodeWithTag(TAG_PREVIEW_TITLE).assertTextEquals("Max Mustermann")
        composeTestRule.onNodeWithTag(TAG_PREVIEW_LOCATION).assertTextEquals("Musterstr. 5")
        composeTestRule.onNodeWithTag(TAG_PREVIEW_DESCRIPTION).assertTextEquals("Fenster reparieren")
    }

    @Test fun phone_email_regex_strategy_moves_phone_and_email_out_of_title() {
        composeTestRule.setContent { CalendarParsingExperimentScreen(onBack = {}) }

        setInput("Max Mustermann 0176 12345678 max@example.com / Musterstr. 5 / Auftrag")
        composeTestRule.onNodeWithText(string(R.string.calendar_experiment_strategy_phone_email_regex)).performClick()

        composeTestRule.onNodeWithTag(TAG_PREVIEW_TITLE).assertTextEquals("Max Mustermann")
        composeTestRule.onNodeWithTag(TAG_PREVIEW_DESCRIPTION).assertTextEquals("Tel: 0176 12345678\nAuftrag")
        composeTestRule.onNodeWithTag(TAG_PREVIEW_ATTENDEES).assertTextEquals("max@example.com")
    }

    @Test fun label_prefix_strategy_routes_guest_prefix_into_attendees() {
        composeTestRule.setContent { CalendarParsingExperimentScreen(onBack = {}) }

        setInput("N:Max Mustermann/T:0176 12345678/A:Musterstr. 5/E:max@example.com")
        composeTestRule.onNodeWithText(string(R.string.calendar_experiment_strategy_label_prefix)).performClick()

        composeTestRule.onNodeWithTag(TAG_PREVIEW_TITLE).assertTextEquals("Max Mustermann")
        composeTestRule.onNodeWithTag(TAG_PREVIEW_LOCATION).assertTextEquals("Musterstr. 5")
        composeTestRule.onNodeWithTag(TAG_PREVIEW_ATTENDEES).assertTextEquals("max@example.com")
    }
}
