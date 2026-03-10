package dev.dettmer.simplenotes.ui.settings.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch

/**
 * Sync & Notification settings screen — Restructured for v1.11.0
 *
 * Three clear sections, each as a separate Composable for recomposition isolation:
 * 1. Sync Triggers (5 triggers + interval selector)
 * 2. Network (WiFi-only + Parallel Connections)
 * 3. Notifications (global toggle + sub-options + permission linking)
 */
@Composable
fun SyncSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToServerSettings: () -> Unit
) {
    val isServerConfigured = viewModel.isServerConfigured()

    SettingsScaffold(
        title = stringResource(R.string.sync_settings_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Offline Mode Warning ──
            if (!isServerConfigured) {
                SettingsInfoCard(
                    text = stringResource(R.string.sync_offline_mode_message),
                    isWarning = true
                )

                Button(
                    onClick = onNavigateToServerSettings,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.sync_offline_mode_button))
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ═══════════════════════════════════════════════════════════════
            // SECTION 1: SYNC TRIGGERS
            // ═══════════════════════════════════════════════════════════════

            SyncTriggersSection(
                viewModel = viewModel,
                isServerConfigured = isServerConfigured
            )

            SettingsDivider()

            // ═══════════════════════════════════════════════════════════════
            // SECTION 2: NETWORK
            // ═══════════════════════════════════════════════════════════════

            NetworkSection(
                viewModel = viewModel,
                isServerConfigured = isServerConfigured
            )

            SettingsDivider()

            // ═══════════════════════════════════════════════════════════════
            // SECTION 3: NOTIFICATIONS (v1.11.0)
            // ═══════════════════════════════════════════════════════════════

            NotificationSettingsSection(
                viewModel = viewModel,
                isServerConfigured = isServerConfigured
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Section Composables — each has its own collectAsState() scope
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Section 1: Sync Triggers
 * Contains all 5 trigger toggles and the periodic interval selector.
 * Own recomposition scope: changes to trigger states only recompose this section.
 */
@Composable
private fun SyncTriggersSection(
    viewModel: SettingsViewModel,
    isServerConfigured: Boolean
) {
    val triggerOnSave by viewModel.triggerOnSave.collectAsState()
    val triggerOnResume by viewModel.triggerOnResume.collectAsState()
    val triggerWifiConnect by viewModel.triggerWifiConnect.collectAsState()
    val triggerPeriodic by viewModel.triggerPeriodic.collectAsState()
    val triggerBoot by viewModel.triggerBoot.collectAsState()
    val syncInterval by viewModel.syncInterval.collectAsState()

    SettingsSectionHeader(text = stringResource(R.string.sync_section_triggers))

    SettingsSwitch(
        title = stringResource(R.string.sync_trigger_on_save_title),
        subtitle = stringResource(R.string.sync_trigger_on_save_subtitle),
        checked = triggerOnSave,
        onCheckedChange = { viewModel.setTriggerOnSave(it) },
        icon = Icons.Default.Save,
        enabled = isServerConfigured
    )

    SettingsSwitch(
        title = stringResource(R.string.sync_trigger_on_resume_title),
        subtitle = stringResource(R.string.sync_trigger_on_resume_subtitle),
        checked = triggerOnResume,
        onCheckedChange = { viewModel.setTriggerOnResume(it) },
        icon = Icons.Default.PhonelinkRing,
        enabled = isServerConfigured
    )

    SettingsSwitch(
        title = stringResource(R.string.sync_trigger_wifi_connect_title),
        subtitle = stringResource(R.string.sync_trigger_wifi_connect_subtitle),
        checked = triggerWifiConnect,
        onCheckedChange = { viewModel.setTriggerWifiConnect(it) },
        icon = Icons.Default.Wifi,
        enabled = isServerConfigured
    )

    SettingsSwitch(
        title = stringResource(R.string.sync_trigger_periodic_title),
        subtitle = stringResource(R.string.sync_trigger_periodic_subtitle),
        checked = triggerPeriodic,
        onCheckedChange = { viewModel.setTriggerPeriodic(it) },
        icon = Icons.Default.Schedule,
        enabled = isServerConfigured
    )

    // Interval selector (only visible when periodic is active)
    if (triggerPeriodic && isServerConfigured) {
        Spacer(modifier = Modifier.height(8.dp))

        val intervalOptions = listOf(
            RadioOption(
                value = 15L,
                title = stringResource(R.string.sync_interval_15min_title),
                subtitle = null
            ),
            RadioOption(
                value = 30L,
                title = stringResource(R.string.sync_interval_30min_title),
                subtitle = null
            ),
            RadioOption(
                value = 60L,
                title = stringResource(R.string.sync_interval_60min_title),
                subtitle = null
            )
        )

        SettingsRadioGroup(
            options = intervalOptions,
            selectedValue = syncInterval,
            onValueSelected = { viewModel.setSyncInterval(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    SettingsSwitch(
        title = stringResource(R.string.sync_trigger_boot_title),
        subtitle = stringResource(R.string.sync_trigger_boot_subtitle),
        checked = triggerBoot,
        onCheckedChange = { viewModel.setTriggerBoot(it) },
        icon = Icons.Default.SettingsInputAntenna,
        enabled = isServerConfigured
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Manual sync hint
    val manualHintText = if (isServerConfigured) {
        stringResource(R.string.sync_manual_hint)
    } else {
        stringResource(R.string.sync_manual_hint_disabled)
    }
    SettingsInfoCard(text = manualHintText)
}

/**
 * Section 2: Network
 * WiFi-only toggle and parallel connections setting.
 * Own recomposition scope: changes to network states only recompose this section.
 */
@Composable
private fun NetworkSection(
    viewModel: SettingsViewModel,
    isServerConfigured: Boolean
) {
    val wifiOnlySync by viewModel.wifiOnlySync.collectAsState()
    val maxParallelConnections by viewModel.maxParallelConnections.collectAsState()

    SettingsSectionHeader(text = stringResource(R.string.sync_section_network_performance))

    // WiFi-Only Toggle
    SettingsSwitch(
        title = stringResource(R.string.sync_wifi_only_title),
        subtitle = stringResource(R.string.sync_wifi_only_subtitle),
        checked = wifiOnlySync,
        onCheckedChange = { viewModel.setWifiOnlySync(it) },
        icon = Icons.Default.Wifi,
        enabled = isServerConfigured
    )

    if (wifiOnlySync && isServerConfigured) {
        SettingsInfoCard(
            text = stringResource(R.string.sync_wifi_only_hint)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 🔧 v1.9.0: Unified parallel connections (downloads + uploads)
    val parallelOptions = listOf(
        RadioOption(
            value = 1,
            title = "1 ${stringResource(R.string.sync_parallel_connections_unit)}",
            subtitle = stringResource(R.string.sync_parallel_connections_desc_1)
        ),
        RadioOption(
            value = 3,
            title = "3 ${stringResource(R.string.sync_parallel_connections_unit)}",
            subtitle = stringResource(R.string.sync_parallel_connections_desc_3)
        ),
        RadioOption(
            value = 5,
            title = "5 ${stringResource(R.string.sync_parallel_connections_unit)}",
            subtitle = stringResource(R.string.sync_parallel_connections_desc_5)
        )
    )

    SettingsRadioGroup(
        title = stringResource(R.string.sync_parallel_connections_title),
        options = parallelOptions,
        selectedValue = maxParallelConnections,
        onValueSelected = { viewModel.setMaxParallelConnections(it) }
    )
}

@Composable
private fun NotificationSettingsSection(
    viewModel: SettingsViewModel,
    isServerConfigured: Boolean
) {
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notificationsErrorsOnly by viewModel.notificationsErrorsOnly.collectAsState()
    val notificationsServerWarning by viewModel.notificationsServerWarning.collectAsState()

    // 🆕 v1.11.0-B: Permission-aware notification toggle
    val context = LocalContext.current
    val activity = context as? Activity

    // Live permission check — re-evaluated on each recomposition (screen open/resume)
    val hasNotificationPermission = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Pre-Android 13: no runtime permission needed
            }
        )
    }

    // Re-check permission on every ON_RESUME (incl. returning from system settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dialog states
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    // Set to true when user intentionally navigates to system settings to grant permission
    var pendingNotificationEnable by remember { mutableStateOf(false) }

    // Sync toggle with actual permission status:
    // - Permission revoked externally → disable toggle
    // - Permission granted after user intentionally went to system settings → enable toggle
    LaunchedEffect(hasNotificationPermission.value) {
        if (!hasNotificationPermission.value && notificationsEnabled) {
            viewModel.setNotificationsEnabled(false)
        } else if (hasNotificationPermission.value && pendingNotificationEnable) {
            pendingNotificationEnable = false
            viewModel.setNotificationsEnabled(true)
        }
    }

    // Permission launcher (Compose-idiomatic)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission.value = granted
        viewModel.setNotificationsEnabled(granted)
    }

    // ── Permission Rationale Dialog ──
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.notifications_permission_rationale_title)) },
            text = { Text(stringResource(R.string.notifications_permission_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    viewModel.setNotificationsEnabled(false)
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ── Permission permanently denied → Open App Settings Dialog ──
    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            title = { Text(stringResource(R.string.notifications_permission_settings_title)) },
            text = { Text(stringResource(R.string.notifications_permission_settings_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionSettingsDialog = false
                    pendingNotificationEnable = true
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.notifications_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionSettingsDialog = false
                    viewModel.setNotificationsEnabled(false)
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    SettingsSectionHeader(text = stringResource(R.string.sync_section_notifications))

    SettingsSwitch(
        title = stringResource(R.string.notifications_enabled_title),
        subtitle = stringResource(R.string.notifications_enabled_subtitle),
        checked = notificationsEnabled,
        onCheckedChange = { enabled ->
            if (enabled) {
                // User wants to enable notifications — check permission first
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (hasNotificationPermission.value) {
                        // Permission already granted — just enable
                        viewModel.setNotificationsEnabled(true)
                    } else if (activity != null &&
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            activity, Manifest.permission.POST_NOTIFICATIONS
                        )
                    ) {
                        // Permission denied once — show rationale, then re-ask
                        showPermissionRationale = true
                    } else {
                        // Either never asked OR permanently denied
                        val prefs = context.getSharedPreferences(
                            dev.dettmer.simplenotes.utils.Constants.PREFS_NAME,
                            Context.MODE_PRIVATE
                        )
                        val wasPermissionRequested = prefs.getBoolean(
                            "notification_permission_requested", false
                        )
                        if (wasPermissionRequested) {
                            // Permanently denied — redirect to system settings
                            showPermissionSettingsDialog = true
                        } else {
                            // First time asking from settings screen
                            prefs.edit().putBoolean(
                                "notification_permission_requested", true
                            ).apply()
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }
                } else {
                    // Pre-Android 13 — no runtime permission needed
                    viewModel.setNotificationsEnabled(true)
                }
            } else {
                // User wants to disable — always allowed
                viewModel.setNotificationsEnabled(false)
            }
        },
        icon = if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
        enabled = isServerConfigured
    )

    if (notificationsEnabled && isServerConfigured) {
        SettingsSwitch(
            title = stringResource(R.string.notifications_errors_only_title),
            subtitle = stringResource(R.string.notifications_errors_only_subtitle),
            checked = notificationsErrorsOnly,
            onCheckedChange = { viewModel.setNotificationsErrorsOnly(it) },
            icon = Icons.Default.ErrorOutline
        )

        SettingsSwitch(
            title = stringResource(R.string.notifications_server_warning_title),
            subtitle = stringResource(R.string.notifications_server_warning_subtitle),
            checked = notificationsServerWarning,
            onCheckedChange = { viewModel.setNotificationsServerWarning(it) },
            icon = Icons.Default.Warning
        )
    }
}
