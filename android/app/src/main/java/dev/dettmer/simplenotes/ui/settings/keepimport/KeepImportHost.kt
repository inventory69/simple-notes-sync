package dev.dettmer.simplenotes.ui.settings.keepimport

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dettmer.simplenotes.ui.main.MainViewModel

/**
 * v2.5.0 — UI-Host, der den [KeepImportViewModel] hält, den State sammelt
 * und basierend darauf die passende Dialog-Variante rendert. Die
 * Einstiegs-Card ([KeepImportSection]) ist immer sichtbar.
 *
 * Snackbar-Events werden via [onSnackbarEvent] an den umgebenden
 * `SnackbarHost` weitergereicht (Anbindung in [ImportSettingsScreen] +
 * [SettingsNavHost]).
 */
@Composable
fun KeepImportHost(
    onSnackbarEvent: (MainViewModel.SnackbarData) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: KeepImportViewModel = viewModel(factory = KeepImportViewModelFactory(app))
    val state by vm.state.collectAsState()

    // Snackbar-Bridge: ViewModel emittiert → Caller leitet an SettingsNavHost weiter.
    LaunchedEffect(vm) {
        vm.showSnackbar.collect { onSnackbarEvent(it) }
    }

    KeepImportSection(onZipPicked = vm::onZipPicked)

    when (val s = state) {
        is KeepImportUiState.Idle -> Unit
        is KeepImportUiState.Configuring -> KeepImportConfigDialog(
            state = s,
            onConfirm = vm::onConfigConfirmed,
            onDismiss = vm::onResultDismissed // semantisch: zurück zu Idle
        )
        is KeepImportUiState.ConfirmLargeZip -> KeepImportLargeZipDialog(
            preScan = s.preScan,
            onConfirm = vm::onLargeZipConfirmed,
            onDismiss = vm::onLargeZipDeclined
        )
        is KeepImportUiState.Running -> KeepImportProgressDialog(
            progress = s.progress,
            cancellable = s.cancellable,
            onCancel = vm::onCancel
        )
        is KeepImportUiState.Done -> KeepImportResultDialog(
            summary = s.summary,
            onDismiss = vm::onResultDismissed
        )
        is KeepImportUiState.Error -> KeepImportResultDialog(
            // Reuse Result-Dialog mit leerer Summary für die Error-Anzeige —
            // Fehlermeldung steht im Title-Strang via Snackbar; UI bleibt
            // konsistent, ohne einen extra Composable zu erfordern.
            summary = dev.dettmer.simplenotes.noteimport.keep.KeepImportSummary.EMPTY,
            onDismiss = vm::onResultDismissed
        )
    }
}
