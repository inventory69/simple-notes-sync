package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch

/**
 * Sync settings screen (Auto-Sync toggle and interval selection)
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun SyncSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val syncInterval by viewModel.syncInterval.collectAsState()
    
    SettingsScaffold(
        title = "Sync-Einstellungen",
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Auto-Sync Info
            SettingsInfoCard(
                text = "🔄 Auto-Sync:\n" +
                    "• Prüft alle 30 Min. ob Server erreichbar\n" +
                    "• Funktioniert bei jeder WiFi-Verbindung\n" +
                    "• Läuft auch im Hintergrund\n" +
                    "• Minimaler Akkuverbrauch (~0.4%/Tag)"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Auto-Sync Toggle
            SettingsSwitch(
                title = "Auto-Sync aktiviert",
                checked = autoSyncEnabled,
                onCheckedChange = { viewModel.setAutoSync(it) },
                icon = Icons.Default.Sync
            )
            
            SettingsDivider()
            
            // Sync Interval Section
            SettingsSectionHeader(text = "Sync-Intervall")
            
            SettingsInfoCard(
                text = "Legt fest, wie oft die App im Hintergrund synchronisiert. " +
                    "Kürzere Intervalle bedeuten aktuellere Daten, verbrauchen aber etwas mehr Akku.\n\n" +
                    "⏱️ Hinweis: Wenn dein Smartphone im Standby ist, kann Android die " +
                    "Synchronisation verzögern (bis zu 60 Min.), um Akku zu sparen. " +
                    "Das ist normal und betrifft alle Hintergrund-Apps."
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Interval Radio Group
            val intervalOptions = listOf(
                RadioOption(
                    value = 15L,
                    title = "⚡ Alle 15 Minuten",
                    subtitle = "Schnellste Synchronisation • ~0.8% Akku/Tag (~23 mAh)"
                ),
                RadioOption(
                    value = 30L,
                    title = "✓ Alle 30 Minuten (Empfohlen)",
                    subtitle = "Ausgewogenes Verhältnis • ~0.4% Akku/Tag (~12 mAh)"
                ),
                RadioOption(
                    value = 60L,
                    title = "🔋 Alle 60 Minuten",
                    subtitle = "Maximale Akkulaufzeit • ~0.2% Akku/Tag (~6 mAh geschätzt)"
                )
            )
            
            SettingsRadioGroup(
                options = intervalOptions,
                selectedValue = syncInterval,
                onValueSelected = { viewModel.setSyncInterval(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
