package dev.dettmer.simplenotes.utils

object Constants {
    // SharedPreferences
    const val PREFS_NAME = "simple_notes_prefs"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_AUTO_SYNC = "auto_sync_enabled"
    const val KEY_LAST_SYNC = "last_sync_timestamp"
    
    // 🔥 v1.1.2: Last Successful Sync Monitoring
    const val KEY_LAST_SUCCESSFUL_SYNC = "last_successful_sync_time"
    const val KEY_LAST_SYNC_WARNING_SHOWN = "last_sync_warning_shown_time"
    const val SYNC_WARNING_THRESHOLD_MS = 24 * 60 * 60 * 1000L  // 24h
    
    // 🔥 NEU: Sync Interval Configuration
    const val PREF_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
    const val DEFAULT_SYNC_INTERVAL_MINUTES = 30L
    
    // 🔥 v1.2.0: Markdown Export/Import
    const val KEY_MARKDOWN_EXPORT = "markdown_export_enabled"
    const val KEY_MARKDOWN_AUTO_IMPORT = "markdown_auto_import_enabled"
    
    // 🔥 v1.3.0: Performance & Multi-Device Sync
    const val KEY_ALWAYS_CHECK_SERVER = "always_check_server"
    const val KEY_ALWAYS_DELETE_FROM_SERVER = "always_delete_from_server"
    
    // 🔥 v1.3.1: Debug & Logging
    const val KEY_FILE_LOGGING_ENABLED = "file_logging_enabled"
    
    // 🔥 v1.6.0: Offline Mode Toggle
    const val KEY_OFFLINE_MODE = "offline_mode_enabled"
    
    // 🔥 v1.7.0: WiFi-Only Sync Toggle
    const val KEY_WIFI_ONLY_SYNC = "wifi_only_sync_enabled"
    const val DEFAULT_WIFI_ONLY_SYNC = false  // Standardmäßig auch mobil syncen
    
    // 🔥 v1.6.0: Configurable Sync Triggers
    const val KEY_SYNC_TRIGGER_ON_SAVE = "sync_trigger_on_save"
    const val KEY_SYNC_TRIGGER_ON_RESUME = "sync_trigger_on_resume"
    const val KEY_SYNC_TRIGGER_WIFI_CONNECT = "sync_trigger_wifi_connect"
    const val KEY_SYNC_TRIGGER_PERIODIC = "sync_trigger_periodic"
    const val KEY_SYNC_TRIGGER_BOOT = "sync_trigger_boot"
    
    // Sync Trigger Defaults (active after server configuration)
    const val DEFAULT_TRIGGER_ON_SAVE = true
    const val DEFAULT_TRIGGER_ON_RESUME = true
    const val DEFAULT_TRIGGER_WIFI_CONNECT = true
    const val DEFAULT_TRIGGER_PERIODIC = false
    const val DEFAULT_TRIGGER_BOOT = false
    
    // Throttling for onSave sync (5 seconds)
    const val MIN_ON_SAVE_SYNC_INTERVAL_MS = 5_000L
    const val PREF_LAST_ON_SAVE_SYNC_TIME = "last_on_save_sync_time"
    
    // WorkManager
    const val SYNC_WORK_TAG = "notes_sync"
    const val SYNC_DELAY_SECONDS = 5L
    
    // Notifications
    const val NOTIFICATION_CHANNEL_ID = "notes_sync_channel"
    const val NOTIFICATION_ID = 1001
    
    // 🎨 v1.7.0: Staggered Grid Layout
    const val KEY_DISPLAY_MODE = "display_mode" // "list" or "grid"
    const val DEFAULT_DISPLAY_MODE = "list"
    const val GRID_COLUMNS = 2
    const val GRID_SPACING_DP = 8
}
