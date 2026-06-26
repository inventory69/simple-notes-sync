package dev.dettmer.simplenotes.ui.settings.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.security.AppLock
import dev.dettmer.simplenotes.security.showAppLockPrompt
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch

@Composable
fun SecuritySettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appLockGraceMs by viewModel.appLockGraceMs.collectAsState()
    var showNoBiometricDialog by remember { mutableStateOf(false) }

    if (showNoBiometricDialog) {
        AlertDialog(
            onDismissRequest = { showNoBiometricDialog = false },
            title = { Text(stringResource(R.string.app_lock_no_biometric_title)) },
            text = { Text(stringResource(R.string.app_lock_no_biometric_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNoBiometricDialog = false
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        @Suppress("InlinedApi")
                        Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                            putExtra(
                                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                AppLock.allowedAuthenticators()
                            )
                        }
                    } else {
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                    }
                    runCatching { context.startActivity(intent) }
                }) { Text(stringResource(R.string.app_lock_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoBiometricDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_security),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionHeader(text = stringResource(R.string.app_lock_section))

            SettingsSwitch(
                title = stringResource(R.string.app_lock_toggle),
                subtitle = stringResource(R.string.app_lock_toggle_subtitle),
                checked = appLockEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (activity == null) return@SettingsSwitch
                        when (AppLock.canAuthenticate(context)) {
                            BiometricManager.BIOMETRIC_SUCCESS -> showAppLockPrompt(
                                activity = activity,
                                onSuccess = {
                                    viewModel.setAppLockEnabled(true)
                                    AppLock.applySecureFlag(activity)
                                },
                                onCancelled = { /* toggle stays off */ },
                                onUnrecoverable = { showNoBiometricDialog = true }
                            )
                            else -> showNoBiometricDialog = true
                        }
                    } else {
                        // Settings is already behind the lock — no re-auth needed
                        viewModel.setAppLockEnabled(false)
                        activity?.let { AppLock.applySecureFlag(it) }
                    }
                }
            )

            if (appLockEnabled) {
                Spacer(modifier = Modifier.height(24.dp))
                SettingsSectionHeader(text = stringResource(R.string.app_lock_timeout_title))
                GraceTimeSelector(currentMs = appLockGraceMs, onSelected = { viewModel.setAppLockGraceMs(it) })
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsInfoCard(text = stringResource(R.string.app_lock_info))

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraceTimeSelector(currentMs: Long, onSelected: (Long) -> Unit) {
    val options = listOf(
        0L to R.string.app_lock_timeout_immediately,
        30_000L to R.string.app_lock_timeout_30s,
        60_000L to R.string.app_lock_timeout_1min,
        300_000L to R.string.app_lock_timeout_5min
    )
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { (ms, labelRes) ->
            GraceChip(
                label = stringResource(labelRes),
                selected = currentMs == ms,
                onClick = { onSelected(ms) }
            )
        }
    }
}

@Composable
private fun GraceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 72.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
