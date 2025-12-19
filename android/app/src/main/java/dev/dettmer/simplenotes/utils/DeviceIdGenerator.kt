package dev.dettmer.simplenotes.utils

import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceIdGenerator {
    
    private const val PREF_NAME = "simple_notes_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Check if already generated
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            // Try Android ID
            deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            
            // Fallback to UUID if Android ID not available
            if (deviceId.isNullOrEmpty()) {
                deviceId = UUID.randomUUID().toString()
            }
            
            // Prefix for identification
            deviceId = "android-$deviceId"
            
            // Save for future use
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        
        return deviceId
    }
}
