package dev.dettmer.simplenotes.utils

object Constants {
    // SharedPreferences
    const val PREFS_NAME = "simple_notes_prefs"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_HOME_SSID = "home_ssid"
    const val KEY_AUTO_SYNC = "auto_sync_enabled"
    const val KEY_LAST_SYNC = "last_sync_timestamp"
    
    // WorkManager
    const val SYNC_WORK_TAG = "notes_sync"
    const val SYNC_DELAY_SECONDS = 5L
    
    // Notifications
    const val NOTIFICATION_CHANNEL_ID = "notes_sync_channel"
    const val NOTIFICATION_ID = 1001
}
