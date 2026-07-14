package dev.dettmer.simplenotes.ui.editor

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.toEnumOrDefault

enum class Strategy { RAW, POSITIONAL, PHONE_REGEX, LABEL_PREFIX, PHONE_EMAIL_REGEX }

data class Preview(val title: String, val location: String, val description: String, val attendees: String = "")

private val PHONE_REGEX = Regex("""\b\d[\d\s/-]{5,}\d\b""")
private val EMAIL_REGEX = Regex("""[\w.+-]+@[\w-]+\.[A-Za-z]{2,}""")

private fun segments(text: String) = text.split("/").map { it.trim() }.filter { it.isNotBlank() }

fun computePreview(text: String, strategy: Strategy): Preview = when (strategy) {
    Strategy.RAW -> Preview(title = text, location = "", description = "")

    Strategy.POSITIONAL -> {
        val parts = segments(text)
        Preview(
            title = parts.getOrElse(0) { "" },
            location = parts.getOrElse(1) { "" },
            description = parts.drop(2).joinToString("\n")
        )
    }

    Strategy.PHONE_REGEX -> {
        val phone = PHONE_REGEX.find(text)?.value?.trim()
        val cleaned = phone?.let { text.replace(it, "") } ?: text
        val parts = segments(cleaned)
        val descLines = parts.drop(2).let { if (phone != null) listOf("Tel: $phone") + it else it }
        Preview(
            title = parts.getOrElse(0) { "" },
            location = parts.getOrElse(1) { "" },
            description = descLines.joinToString("\n")
        )
    }

    Strategy.LABEL_PREFIX -> {
        var title = ""
        var location = ""
        var attendees = ""
        val description = mutableListOf<String>()
        segments(text).forEach { segment ->
            val prefix = segment.substringBefore(':', missingDelimiterValue = "").lowercase()
            val value = if (prefix.isNotEmpty()) segment.substringAfter(':').trim() else segment
            when (prefix) {
                "t", "tel", "telefon" -> description.add("Tel: $value")
                "a", "adresse", "str" -> location = value
                "n", "name", "titel" -> title = value
                "e", "email", "gast" -> attendees = value
                else -> description.add(segment)
            }
        }
        Preview(title = title, location = location, description = description.joinToString("\n"), attendees = attendees)
    }

    Strategy.PHONE_EMAIL_REGEX -> {
        val phone = PHONE_REGEX.find(text)?.value?.trim()
        val email = EMAIL_REGEX.find(text)?.value?.trim()
        var cleaned = text
        phone?.let { cleaned = cleaned.replace(it, "") }
        email?.let { cleaned = cleaned.replace(it, "") }
        val parts = segments(cleaned)
        val descLines = parts.drop(2).let { if (phone != null) listOf("Tel: $phone") + it else it }
        Preview(
            title = parts.getOrElse(0) { "" },
            location = parts.getOrElse(1) { "" },
            description = descLines.joinToString("\n"),
            attendees = email ?: ""
        )
    }
}

fun SharedPreferences.calendarParsingStrategy(): Strategy =
    getString(Constants.KEY_CALENDAR_PARSING_STRATEGY, Strategy.RAW.name).toEnumOrDefault(Strategy.RAW)

fun SharedPreferences.setCalendarParsingStrategy(strategy: Strategy) {
    edit { putString(Constants.KEY_CALENDAR_PARSING_STRATEGY, strategy.name) }
}
