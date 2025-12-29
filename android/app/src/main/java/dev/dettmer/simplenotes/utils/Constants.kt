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
    
    // 🔥 v1.1.2: Last Successful Sync Monitoring
    const val KEY_LAST_SUCCESSFUL_SYNC = "last_successful_sync_time"
    const val KEY_LAST_SYNC_WARNING_SHOWN = "last_sync_warning_shown_time"
    const val SYNC_WARNING_THRESHOLD_MS = 24 * 60 * 60 * 1000L  // 24h
    
    // 🔥 NEU: Sync Interval Configuration
    const val PREF_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
    const val DEFAULT_SYNC_INTERVAL_MINUTES = 30L
    
    // WorkManager
    const val SYNC_WORK_TAG = "notes_sync"
    const val SYNC_DELAY_SECONDS = 5L
    
    // Notifications
    const val NOTIFICATION_CHANNEL_ID = "notes_sync_channel"
    const val NOTIFICATION_ID = 1001
}
