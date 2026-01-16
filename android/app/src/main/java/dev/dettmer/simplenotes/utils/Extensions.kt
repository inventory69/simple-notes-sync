package dev.dettmer.simplenotes.utils

import android.content.Context
import android.widget.Toast
import dev.dettmer.simplenotes.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DAYS_THRESHOLD = 7L
private const val TRUNCATE_SUFFIX_LENGTH = 3

// Toast Extensions
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

// Timestamp to readable format (legacy - without context, uses German)
fun Long.toReadableTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Gerade eben"
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            "Vor $minutes Min"
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            "Vor $hours Std"
        }
        diff < TimeUnit.DAYS.toMillis(DAYS_THRESHOLD) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "Vor $days Tagen"
        }
        else -> {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
            sdf.format(Date(this))
        }
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
            context.getString(R.string.time_minutes_ago, minutes)
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff).toInt()
            context.getString(R.string.time_hours_ago, hours)
        }
        diff < TimeUnit.DAYS.toMillis(DAYS_THRESHOLD) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff).toInt()
            context.getString(R.string.time_days_ago, days)
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
