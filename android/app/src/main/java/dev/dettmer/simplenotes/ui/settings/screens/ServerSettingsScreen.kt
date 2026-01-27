package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold

/**
 * Server configuration settings screen
 * v1.5.0: Jetpack Compose Settings Redesign
 * v1.6.0: Offline Mode Toggle
 * v1.7.0 Hotfix: Save settings on screen exit (not on every keystroke)
 */
@Suppress("LongMethod", "MagicNumber") // Compose UI + Color hex values
@Composable
fun ServerSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val offlineMode by viewModel.offlineMode.collectAsState()
    val serverHost by viewModel.serverHost.collectAsState()  // 🌟 v1.6.0: Only host part
    val serverUrl by viewModel.serverUrl.collectAsState()    // Full URL for display
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val isHttps by viewModel.isHttps.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    var passwordVisible by remember { mutableStateOf(false) }
    
    // 🔧 v1.7.0 Hotfix: Save server settings when leaving this screen
    // This prevents false "server changed" detection during text input
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveServerSettingsManually()
        }
    }
    
    // Check server status on load (only if not in offline mode)
    LaunchedEffect(offlineMode) {
        if (!offlineMode) {
            viewModel.checkServerStatus()
        }
    }
    
    SettingsScaffold(
        title = stringResource(R.string.server_settings_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ═══════════════════════════════════════════════════════════════
            // 🌟 v1.6.0: Offline-Modus Toggle (TOP)
            // ═══════════════════════════════════════════════════════════════
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setOfflineMode(!offlineMode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (offlineMode) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.server_offline_mode_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.server_offline_mode_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = offlineMode,
                        onCheckedChange = { viewModel.setOfflineMode(it) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // Server Configuration (grayed out when offline mode)
            // ═══════════════════════════════════════════════════════════════
            
            val fieldsEnabled = !offlineMode
            val fieldsAlpha = if (offlineMode) 0.5f else 1f
            
            Column(modifier = Modifier.alpha(fieldsAlpha)) {
                // Verbindungstyp
                Text(
                    text = stringResource(R.string.server_connection_type),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isHttps,
                        onClick = { viewModel.updateProtocol(false) },
                        label = { Text(stringResource(R.string.server_connection_http)) },
                        enabled = fieldsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isHttps,
                        onClick = { viewModel.updateProtocol(true) },
                        label = { Text(stringResource(R.string.server_connection_https)) },
                        enabled = fieldsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Text(
                    text = if (!isHttps) {
                        stringResource(R.string.server_connection_http_hint)
                    } else {
                        stringResource(R.string.server_connection_https_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                
                // 🌟 v1.6.0: Server-Adresse with non-editable prefix
                OutlinedTextField(
                    value = serverHost,  // Only host part is editable
                    onValueChange = { viewModel.updateServerHost(it) },
                    label = { Text(stringResource(R.string.server_address)) },
                    supportingText = { Text(stringResource(R.string.server_address_hint)) },
                    prefix = {
                        // Protocol prefix is displayed but not editable
                        Text(
                            text = if (isHttps) "https://" else "http://",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (fieldsEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Language, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = fieldsEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Benutzername
                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.updateUsername(it) },
                    label = { Text(stringResource(R.string.username)) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = fieldsEnabled
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Passwort
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.updatePassword(it) },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    stringResource(R.string.server_password_hide)
                                } else {
                                    stringResource(R.string.server_password_show)
                                }
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = fieldsEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Server-Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.server_status_label), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = when (serverStatus) {
                            is SettingsViewModel.ServerStatus.OfflineMode -> stringResource(R.string.server_status_offline_mode)
                            is SettingsViewModel.ServerStatus.Reachable -> stringResource(R.string.server_status_reachable)
                            is SettingsViewModel.ServerStatus.Unreachable -> stringResource(R.string.server_status_unreachable)
                            is SettingsViewModel.ServerStatus.Checking -> stringResource(R.string.server_status_checking)
                            is SettingsViewModel.ServerStatus.NotConfigured -> stringResource(R.string.server_status_offline_mode)
                            else -> stringResource(R.string.server_status_unknown)
                        },
                        color = when (serverStatus) {
                            is SettingsViewModel.ServerStatus.OfflineMode -> MaterialTheme.colorScheme.tertiary
                            is SettingsViewModel.ServerStatus.Reachable -> Color(0xFF4CAF50)
                            is SettingsViewModel.ServerStatus.Unreachable -> Color(0xFFF44336)
                            is SettingsViewModel.ServerStatus.NotConfigured -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action Buttons (disabled in offline mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(fieldsAlpha),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    enabled = fieldsEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.test_connection))
                }
                
                Button(
                    onClick = { viewModel.syncNow() },
                    enabled = fieldsEnabled && !isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.sync_now))
                }
            }
        }
    }
}
