package dev.dettmer.simplenotes.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * 🔧 v2.3.0: Shared helper to open battery optimization settings.
 * Extracted from ComposeSettingsActivity and ComposeMainActivity (REF-008).
 *
 * Audit: E-02, 3-02
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"

    /**
     * Attempts to open battery optimization settings.
     * @return true if settings were opened, false if all attempts failed
     */
    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(activity: Activity): Boolean {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = "package:${activity.packageName}".toUri()
            activity.startActivity(intent)
            return true
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to open battery optimization settings: ${e.message}")
            // Fallback: Open general battery settings list
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                activity.startActivity(intent)
                return true
            } catch (e2: Exception) {
                Logger.w(TAG, "Failed to open fallback battery settings: ${e2.message}")
                return false
            }
        }
    }
}
