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
    const val DEFAULT_DISPLAY_MODE = "grid"  // v1.8.0: Grid als Standard-Ansicht
    const val GRID_COLUMNS = 2
    const val GRID_SPACING_DP = 8

    // 🆕 v1.9.0 (F05): Custom App Title
    const val KEY_CUSTOM_APP_TITLE = "custom_app_title"
    const val DEFAULT_CUSTOM_APP_TITLE = ""  // Empty = use default "Simple Notes"
    const val MAX_CUSTOM_APP_TITLE_LENGTH = 30

    // 🆕 v1.9.0: Configurable WebDAV Sync Folder
    const val KEY_SYNC_FOLDER_NAME = "sync_folder_name"
    const val DEFAULT_SYNC_FOLDER_NAME = "notes"  // Backward compatible default
    const val MAX_SYNC_FOLDER_NAME_LENGTH = 50

    // 🆕 v1.10.0: Configurable connection timeout
    const val KEY_CONNECTION_TIMEOUT_SECONDS = "connection_timeout_seconds"
    const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 8  // 8s default, good for mobile
    const val MIN_CONNECTION_TIMEOUT_SECONDS = 3
    const val MAX_CONNECTION_TIMEOUT_SECONDS = 30

    // 🆕 v1.9.0: Autosave with debounce
    const val KEY_AUTOSAVE_ENABLED = "autosave_enabled"
    const val DEFAULT_AUTOSAVE_ENABLED = true
    const val AUTOSAVE_DEBOUNCE_MS = 3_000L  // 3 seconds after last edit
    const val AUTOSAVE_INDICATOR_DURATION_MS = 2_000L  // indicator visible duration
    const val AUTOSAVE_INDICATOR_FADE_MS = 400  // fade animation duration (ms)

    // 🆕 v1.10.0: Undo/Redo
    const val UNDO_STACK_MAX_SIZE = 50
    const val UNDO_SNAPSHOT_DEBOUNCE_MS = 500L  // Group keystrokes into single undo step
    const val SNAPSHOT_RESTORE_GUARD_DELAY_MS = 50L  // Delay before clearing isRestoringSnapshot
    const val CHECKLIST_SCROLL_LAYOUT_DELAY_MS = 50L  // Wait for item layout before scroll check

    // 🆕 v1.10.0: Sync Banner auto-hide delays
    const val BANNER_DELAY_COMPLETED_MS = 2_000L
    const val BANNER_DELAY_INFO_MS = 2_500L
    const val BANNER_DELAY_ERROR_MS = 4_000L
    // Minimum display duration for active sync phases (PREPARING/UPLOADING/…) — prevents too-brief flashes
    const val BANNER_PHASE_MIN_MS = 400L

    // ⚡ v1.8.0: Parallel Connections (Downloads + Uploads)
    // 🔧 v1.9.0: Unified setting for both downloads and uploads
    const val KEY_MAX_PARALLEL_CONNECTIONS = "max_parallel_downloads"  // Keep old key for migration
    const val DEFAULT_MAX_PARALLEL_CONNECTIONS = 5
    const val MIN_PARALLEL_CONNECTIONS = 1
    const val MAX_PARALLEL_CONNECTIONS = 5  // v1.9.0: Reduced from 10 (uploads cap at 6)
    const val MAX_PARALLEL_UPLOADS_CAP = 6  // Hard cap for upload concurrency
    
    // 🔀 v1.8.0: Sortierung
    const val KEY_SORT_OPTION = "sort_option"
    const val KEY_SORT_DIRECTION = "sort_direction"
    const val DEFAULT_SORT_OPTION = "updatedAt"
    const val DEFAULT_SORT_DIRECTION = "desc"

    // 🆕 v1.9.0 (F06): Filter
    const val KEY_NOTE_FILTER = "note_filter"
    const val DEFAULT_NOTE_FILTER = "all"  // NoteFilter.ALL.prefsValue

    // 📋 v1.8.0: Post-Update Changelog
    const val KEY_LAST_SHOWN_CHANGELOG_VERSION = "last_shown_changelog_version"
    
    // 🆕 v1.8.1 (IMPL_08): Globaler Sync-Cooldown (über alle Trigger hinweg)
    const val KEY_LAST_GLOBAL_SYNC_TIME = "last_global_sync_timestamp"
    const val MIN_GLOBAL_SYNC_INTERVAL_MS = 30_000L  // 30 Sekunden
    
    // 🆕 v1.8.1 (IMPL_08B): onSave-Sync Worker-Tag (bypassed globalen Cooldown)
    const val SYNC_ONSAVE_TAG = "onsave"
}
