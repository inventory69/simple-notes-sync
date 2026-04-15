package dev.dettmer.simplenotes.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import dev.dettmer.simplenotes.R

/**
 * 🔧 v2.3.0: Shared helper to open battery optimization settings.
 * Extracted from ComposeSettingsActivity and ComposeMainActivity (REF-008).
 *
 * Audit: E-02, 3-02
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"

    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = "package:${activity.packageName}".toUri()
            activity.startActivity(intent)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to open battery optimization settings: ${e.message}")
            // Fallback: Open general battery settings list
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                activity.startActivity(intent)
            } catch (e2: Exception) {
                Logger.w(TAG, "Failed to open fallback battery settings: ${e2.message}")
                Toast.makeText(
                    activity,
                    activity.getString(R.string.battery_optimization_open_settings_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
