package dev.dettmer.simplenotes.utils

import android.content.Context
import android.widget.Toast
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

// Timestamp to readable format
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

// Truncate long strings
fun String.truncate(maxLength: Int): String {
    return if (length > maxLength) {
        substring(0, maxLength - TRUNCATE_SUFFIX_LENGTH) + "..."
    } else {
        this
    }
}
