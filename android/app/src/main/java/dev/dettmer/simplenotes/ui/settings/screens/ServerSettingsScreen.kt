package dev.dettmer.simplenotes.ui.settings.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold

/**
 * Server configuration settings screen
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun ServerSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val isHttps by viewModel.isHttps.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    var passwordVisible by remember { mutableStateOf(false) }
    
    // Check server status on load
    LaunchedEffect(Unit) {
        viewModel.checkServerStatus()
    }
    
    SettingsScaffold(
        title = "Server-Einstellungen",
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Verbindungstyp
            Text(
                text = "Verbindungstyp",
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
                    label = { Text("🏠 Intern (HTTP)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isHttps,
                    onClick = { viewModel.updateProtocol(true) },
                    label = { Text("🌐 Extern (HTTPS)") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Text(
                text = if (!isHttps) {
                    "HTTP nur für lokale Netzwerke (z.B. 192.168.x.x, 10.x.x.x)"
                } else {
                    "HTTPS für sichere Verbindungen über das Internet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            
            // Server-Adresse
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text("Server-Adresse") },
                supportingText = { Text("z.B. http://192.168.0.188:8080/notes") },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Benutzername
            OutlinedTextField(
                value = username,
                onValueChange = { viewModel.updateUsername(it) },
                label = { Text("Benutzername") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Passwort
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text("Passwort") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (passwordVisible) "Verstecken" else "Anzeigen"
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            
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
                    Text("Server-Status:", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = when (serverStatus) {
                            is SettingsViewModel.ServerStatus.Reachable -> "✅ Erreichbar"
                            is SettingsViewModel.ServerStatus.Unreachable -> "❌ Nicht erreichbar"
                            is SettingsViewModel.ServerStatus.Checking -> "🔍 Prüfe..."
                            is SettingsViewModel.ServerStatus.NotConfigured -> "⚠️ Nicht konfiguriert"
                            else -> "❓ Unbekannt"
                        },
                        color = when (serverStatus) {
                            is SettingsViewModel.ServerStatus.Reachable -> Color(0xFF4CAF50)
                            is SettingsViewModel.ServerStatus.Unreachable -> Color(0xFFF44336)
                            is SettingsViewModel.ServerStatus.NotConfigured -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Verbindung testen")
                }
                
                Button(
                    onClick = { viewModel.syncNow() },
                    enabled = !isSyncing,
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
                    Text("Jetzt synchronisieren")
                }
            }
        }
    }
}
