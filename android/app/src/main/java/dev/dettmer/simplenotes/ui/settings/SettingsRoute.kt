package dev.dettmer.simplenotes.ui.settings

/**
 * Navigation routes for Settings screens
 * v1.5.0: Jetpack Compose Settings Redesign
 */
sealed class SettingsRoute(val route: String) {
    data object Main : SettingsRoute("settings_main")
    data object Language : SettingsRoute("settings_language")
    data object Server : SettingsRoute("settings_server")
    data object Sync : SettingsRoute("settings_sync")
    data object Markdown : SettingsRoute("settings_markdown")
    data object Backup : SettingsRoute("settings_backup")
    data object About : SettingsRoute("settings_about")
    data object Debug : SettingsRoute("settings_debug")
    data object Display : SettingsRoute("settings_display")  // 🎨 v1.7.0
    data object Import : SettingsRoute("settings_import")    // 🆕 Issue #21
}
