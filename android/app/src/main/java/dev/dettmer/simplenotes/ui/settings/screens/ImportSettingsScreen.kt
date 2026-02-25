package dev.dettmer.simplenotes.ui.settings.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.noteimport.NotesImportWizard
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.BackupProgressCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsOutlinedButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import kotlinx.coroutines.launch

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = 1024L * 1024L

/**
 * Import Notes Settings Screen
 *
 * Erlaubt das Importieren von .md, .json und .txt Dateien vom WebDAV-Server
 * oder dem lokalen Gerät-Speicher.
 *
 * v1.9.0: Issue #21
 */
@Composable
fun ImportSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isServerConfigured = viewModel.isServerConfigured()
    val noFilesFoundText = stringResource(R.string.import_no_files_found)

    var isScanning by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<NotesImportWizard.ImportCandidate>>(emptyList()) }
    var importSummary by remember { mutableStateOf<NotesImportWizard.ImportSummary?>(null) }
    var showScanResults by remember { mutableStateOf(false) }
    var selectedCandidates by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // File Picker für lokalen Import (mehrere Dateien)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isImporting = true
                val summary = viewModel.importLocalFiles(uris)
                importSummary = summary
                isImporting = false
            }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_import_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Globale Info-Card
            SettingsInfoCard(text = stringResource(R.string.import_info_text))

            // Fortschrittsanzeige während Import
            if (isImporting) {
                BackupProgressCard(
                    statusText = stringResource(R.string.import_dialog_scanning)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            WebDavImportSection(
                isServerConfigured = isServerConfigured,
                isScanning = isScanning,
                isImporting = isImporting,
                showScanResults = showScanResults,
                scanResults = scanResults,
                selectedCandidates = selectedCandidates,
                onScanClick = {
                    scope.launch {
                        isScanning = true
                        scanResults = viewModel.scanWebDavForImport()
                        selectedCandidates = scanResults.indices.toSet()
                        showScanResults = scanResults.isNotEmpty()
                        isScanning = false
                        if (scanResults.isEmpty()) {
                            Toast.makeText(context, noFilesFoundText, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSelectionChange = { index, checked ->
                    selectedCandidates = if (checked) selectedCandidates + index else selectedCandidates - index
                },
                onCancelScan = {
                    showScanResults = false
                    scanResults = emptyList()
                },
                onImportSelected = {
                    scope.launch {
                        isImporting = true
                        showScanResults = false
                        val selected = selectedCandidates.map { scanResults[it] }
                        importSummary = viewModel.importCandidates(selected)
                        scanResults = emptyList()
                        isImporting = false
                    }
                }
            )

            SettingsDivider()

            LocalImportSection(
                isImporting = isImporting,
                onPickFiles = {
                    filePickerLauncher.launch(arrayOf(
                        "text/markdown",
                        "text/plain",
                        "application/json",
                        "application/octet-stream"
                    ))
                }
            )

            importSummary?.let { summary ->
                SettingsDivider()
                ImportSummarySection(summary = summary)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WebDavImportSection(
    isServerConfigured: Boolean,
    isScanning: Boolean,
    isImporting: Boolean,
    showScanResults: Boolean,
    scanResults: List<NotesImportWizard.ImportCandidate>,
    selectedCandidates: Set<Int>,
    onScanClick: () -> Unit,
    onSelectionChange: (Int, Boolean) -> Unit,
    onCancelScan: () -> Unit,
    onImportSelected: () -> Unit,
) {
    SettingsSectionHeader(text = stringResource(R.string.import_section_server))
    Spacer(modifier = Modifier.height(8.dp))
    SettingsInfoCard(text = stringResource(R.string.import_server_info))
    Spacer(modifier = Modifier.height(8.dp))
    SettingsButton(
        text = stringResource(R.string.settings_import_from_server),
        onClick = onScanClick,
        enabled = !isScanning && !isImporting && isServerConfigured,
        isLoading = isScanning,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    if (!isServerConfigured) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_sync_offline_mode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    if (showScanResults && scanResults.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        ScanResultsCard(
            scanResults = scanResults,
            selectedCandidates = selectedCandidates,
            onSelectionChange = onSelectionChange,
            onCancelScan = onCancelScan,
            onImportSelected = onImportSelected,
        )
    }
}

@Composable
private fun ScanResultsCard(
    scanResults: List<NotesImportWizard.ImportCandidate>,
    selectedCandidates: Set<Int>,
    onSelectionChange: (Int, Boolean) -> Unit,
    onCancelScan: () -> Unit,
    onImportSelected: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.import_dialog_found, scanResults.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(scanResults.indices.toList()) { index ->
                    val candidate = scanResults[index]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = selectedCandidates.contains(index),
                            onCheckedChange = { checked -> onSelectionChange(index, checked) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = candidate.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${candidate.fileType.name} · ${formatFileSize(candidate.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsOutlinedButton(
                    text = stringResource(R.string.import_cancel),
                    onClick = onCancelScan,
                    modifier = Modifier.weight(1f)
                )
                SettingsButton(
                    text = stringResource(R.string.import_button_import, selectedCandidates.size),
                    onClick = onImportSelected,
                    enabled = selectedCandidates.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LocalImportSection(
    isImporting: Boolean,
    onPickFiles: () -> Unit,
) {
    SettingsSectionHeader(text = stringResource(R.string.import_section_local))
    Spacer(modifier = Modifier.height(8.dp))
    SettingsInfoCard(text = stringResource(R.string.import_local_info))
    Spacer(modifier = Modifier.height(8.dp))
    SettingsOutlinedButton(
        text = stringResource(R.string.settings_import_from_file),
        onClick = onPickFiles,
        enabled = !isImporting,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun ImportSummarySection(summary: NotesImportWizard.ImportSummary) {
    SettingsSectionHeader(text = stringResource(R.string.import_summary_title))
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (summary.failed > 0)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.import_dialog_complete,
                    summary.imported, summary.skipped, summary.failed
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.failed > 0) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (summary.failed > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                summary.results.filterIsInstance<NotesImportWizard.ImportResult.Failed>().forEach { failed ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${failed.sourceName}: ${failed.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < BYTES_PER_KB -> "$bytes B"
        bytes < BYTES_PER_MB -> "${bytes / BYTES_PER_KB} KB"
        else -> "${"%.1f".format(bytes.toDouble() / BYTES_PER_MB.toDouble())} MB"
    }
}
