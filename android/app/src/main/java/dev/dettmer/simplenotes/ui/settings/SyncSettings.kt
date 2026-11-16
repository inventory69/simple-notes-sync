package dev.dettmer.simplenotes.ui.settings

import androidx.core.content.edit
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sync-Screen ohne Markdown: Intervall, parallele Verbindungen, Auslöser, WLAN-only und
 * Benachrichtigungen. Alles reine Prefs, gelesen von Scheduler, Worker und NetworkMonitor.
 */
class SyncSettings(private val ctx: SettingsContext) {
    private val prefs = ctx.prefs

    private val _syncInterval = MutableStateFlow(readSyncInterval())
    val syncInterval: StateFlow<Long> = _syncInterval.asStateFlow()

    // 🔧 v1.9.0: eine Einstellung für Downloads und Uploads
    private val _maxParallelConnections = MutableStateFlow(readMaxParallelConnections())
    val maxParallelConnections: StateFlow<Int> = _maxParallelConnections.asStateFlow()

    // 🌟 v1.6.0: Configurable Sync Triggers
    private val onSave = BoolPref(prefs, Constants.KEY_SYNC_TRIGGER_ON_SAVE, Constants.DEFAULT_TRIGGER_ON_SAVE)
    val triggerOnSave: StateFlow<Boolean> = onSave.flow

    private val onResume = BoolPref(prefs, Constants.KEY_SYNC_TRIGGER_ON_RESUME, Constants.DEFAULT_TRIGGER_ON_RESUME)
    val triggerOnResume: StateFlow<Boolean> = onResume.flow

    private val wifiConnect = BoolPref(prefs, Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, Constants.DEFAULT_TRIGGER_WIFI_CONNECT)
    val triggerWifiConnect: StateFlow<Boolean> = wifiConnect.flow

    private val periodic = BoolPref(prefs, Constants.KEY_SYNC_TRIGGER_PERIODIC, Constants.DEFAULT_TRIGGER_PERIODIC)
    val triggerPeriodic: StateFlow<Boolean> = periodic.flow

    private val boot = BoolPref(prefs, Constants.KEY_SYNC_TRIGGER_BOOT, Constants.DEFAULT_TRIGGER_BOOT)
    val triggerBoot: StateFlow<Boolean> = boot.flow

    // 🎉 v1.7.0
    private val wifiOnly = BoolPref(prefs, Constants.KEY_WIFI_ONLY_SYNC, Constants.DEFAULT_WIFI_ONLY_SYNC)
    val wifiOnlySync: StateFlow<Boolean> = wifiOnly.flow

    // 🆕 v1.11.0
    private val notifications = BoolPref(prefs, Constants.KEY_NOTIFICATIONS_ENABLED, Constants.DEFAULT_NOTIFICATIONS_ENABLED)
    val notificationsEnabled: StateFlow<Boolean> = notifications.flow

    private val errorsOnly = BoolPref(prefs, Constants.KEY_NOTIFICATIONS_ERRORS_ONLY, Constants.DEFAULT_NOTIFICATIONS_ERRORS_ONLY)
    val notificationsErrorsOnly: StateFlow<Boolean> = errorsOnly.flow

    private val serverWarning =
        BoolPref(prefs, Constants.KEY_NOTIFICATIONS_SERVER_WARNING, Constants.DEFAULT_NOTIFICATIONS_SERVER_WARNING)
    val notificationsServerWarning: StateFlow<Boolean> = serverWarning.flow

    private val bools = listOf(onSave, onResume, wifiConnect, periodic, boot, wifiOnly, notifications, errorsOnly, serverWarning)

    fun setSyncInterval(minutes: Long) {
        prefs.edit { putLong(Constants.PREF_SYNC_INTERVAL_MINUTES, minutes) }
        _syncInterval.value = minutes
        ctx.scope.launch {
            val text = when (minutes) {
                15L -> ctx.string(R.string.toast_sync_interval_15min)
                60L -> ctx.string(R.string.toast_sync_interval_60min)
                else -> ctx.string(R.string.toast_sync_interval_30min)
            }
            ctx.toast(ctx.string(R.string.toast_sync_interval, text))
        }
    }

    fun setMaxParallelConnections(count: Int) {
        val validCount = count.coerceIn(Constants.MIN_PARALLEL_CONNECTIONS, Constants.MAX_PARALLEL_CONNECTIONS)
        prefs.edit { putInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, validCount) }
        _maxParallelConnections.value = validCount
    }

    fun setTriggerOnSave(enabled: Boolean) = set(onSave, enabled, "Trigger onSave")

    fun setTriggerOnResume(enabled: Boolean) = set(onResume, enabled, "Trigger onResume")

    fun setTriggerWifiConnect(enabled: Boolean) {
        set(wifiConnect, enabled, "Trigger WiFi-Connect")
        ctx.scope.launch { ctx.emit(SettingsViewModel.SettingsEvent.RestartNetworkMonitor) }
    }

    fun setTriggerPeriodic(enabled: Boolean) {
        set(periodic, enabled, "Trigger Periodic")
        ctx.scope.launch { ctx.emit(SettingsViewModel.SettingsEvent.RestartNetworkMonitor) }
    }

    fun setTriggerBoot(enabled: Boolean) = set(boot, enabled, "Trigger Boot")

    /** 🎉 v1.7.0: Sync nur im WLAN. */
    fun setWifiOnlySync(enabled: Boolean) = set(wifiOnly, enabled, "📡 WiFi-only sync")

    fun setNotificationsEnabled(enabled: Boolean) = set(notifications, enabled, "🔔 Notifications enabled")

    fun setNotificationsErrorsOnly(enabled: Boolean) = set(errorsOnly, enabled, "🔔 Notifications errors-only")

    fun setNotificationsServerWarning(enabled: Boolean) = set(serverWarning, enabled, "🔔 Notifications server warning")

    /** v1.9.0: Nach einem Restore mit App-Einstellungen alles aus den Prefs neu lesen. */
    fun reloadFromPrefs() {
        _syncInterval.value = readSyncInterval()
        _maxParallelConnections.value = readMaxParallelConnections()
        bools.forEach { it.reload() }
    }

    private fun set(pref: BoolPref, enabled: Boolean, label: String) {
        pref.set(enabled)
        Logger.d(TAG, "$label: $enabled")
    }

    private fun readSyncInterval() = prefs.getLong(Constants.PREF_SYNC_INTERVAL_MINUTES, Constants.DEFAULT_SYNC_INTERVAL_MINUTES)

    private fun readMaxParallelConnections() =
        prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS)
            .coerceIn(Constants.MIN_PARALLEL_CONNECTIONS, Constants.MAX_PARALLEL_CONNECTIONS)

    private companion object {
        const val TAG = "SyncSettings"
    }
}
