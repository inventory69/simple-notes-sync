package dev.dettmer.simplenotes.utils

import android.content.Context
import dev.dettmer.simplenotes.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DAYS_THRESHOLD = 7L
private const val TRUNCATE_SUFFIX_LENGTH = 3

inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    if (this == null) {
        default
    } else {
        try {
            enumValueOf(this)
        } catch (_: IllegalArgumentException) {
            default
        }
    }

// Timestamp to readable format (with context for i18n)
fun Long.toReadableTime(context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> context.getString(R.string.time_just_now)
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff).toInt()
            context.resources.getQuantityString(R.plurals.time_minutes_ago, minutes, minutes)
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff).toInt()
            context.resources.getQuantityString(R.plurals.time_hours_ago, hours, hours)
        }
        diff < TimeUnit.DAYS.toMillis(DAYS_THRESHOLD) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff).toInt()
            context.resources.getQuantityString(R.plurals.time_days_ago, days, days)
        }
        else -> {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            sdf.format(Date(this))
        }
    }
}

// Truncate long strings
fun String.truncate(maxLength: Int): String {
    return if (length > maxLength) {
        substring(0, maxLength - TRUNCATE_SUFFIX_LENGTH) + "..."
    } else {
        this
    }
}

/**
 * Schreibt [text] in eine Temp-Datei im selben Verzeichnis und benennt sie dann um. Bricht das
 * Schreiben ab (voller Speicher, Prozess-Kill), bleibt die bisherige Datei unangetastet, und ein
 * paralleler Leser sieht immer eine vollständige Fassung. `writeText` kürzt die Datei dagegen
 * zuerst auf null, ein Abbruch danach hinterlässt sie leer.
 *
 * ponytail: eigener Temp-Name je Aufruf, damit sich zwei Schreiber nicht in die Quere kommen. Nach
 * einem Kill mittendrin bleibt die `.tmp` liegen; aufräumen erst, wenn das je stört.
 */
@Throws(IOException::class)
fun File.writeTextAtomic(text: String) {
    val tmp = File.createTempFile("$name.", ".tmp", parentFile)
    try {
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(this)) throw IOException("Rename failed: ${tmp.name} -> $name")
    } finally {
        tmp.delete() // nach dem Umbenennen ein No-op
    }
}
