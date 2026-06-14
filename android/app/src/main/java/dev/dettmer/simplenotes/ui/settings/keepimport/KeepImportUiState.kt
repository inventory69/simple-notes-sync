package dev.dettmer.simplenotes.ui.settings.keepimport

import android.net.Uri
import dev.dettmer.simplenotes.noteimport.keep.KeepImportProgress
import dev.dettmer.simplenotes.noteimport.keep.KeepImportSummary
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepPreScanResult

/**
 * v2.5.0 — Sealed UI-State für den Keep-Import-Flow.
 *
 * Zustandsdiagramm (Analyseplan §4.2):
 *
 *   Idle ──onZipPicked──► Configuring(preScan=null, scanning=true)
 *                              │ preScan fertig
 *                              ▼
 *                         Configuring(preScan=…, scanning=false)
 *                              │ onConfigConfirmed
 *                              │   ├── sizeBytes ≤ 200 MB → Running
 *                              │   └── sizeBytes  > 200 MB → ConfirmLargeZip
 *                              ▼
 *                         ConfirmLargeZip ──onLargeZipConfirmed──► Running
 *                                          ──onLargeZipDeclined───► Idle
 *                              │
 *   Running(progress) ──Done(summary)
 *                    └─ onCancel ─► Idle (+ Snackbar "abgebrochen")
 *                    └─ Exception ─► Error(message)
 *
 *   Error ──onResultDismissed──► Idle
 *   Done  ──onResultDismissed──► Idle
 */
sealed class KeepImportUiState {
    object Idle : KeepImportUiState()

    /**
     * Configuring-Dialog. `preScan == null && scanning == true` → Dialog zeigt
     * "Archivinhalt wird analysiert…". Sobald PreScan abgeschlossen ist, wird
     * `preScan` gesetzt und `scanning = false`.
     */
    data class Configuring(
        val zipUri: Uri,
        val preScan: KeepPreScanResult? = null,
        val scanning: Boolean = true
    ) : KeepImportUiState()

    /**
     * Zwischengeschalteter Confirmation-Step bei ZIPs >200 MB
     * (Analyseplan §4.2 [3a], §7.2 weiches Limit).
     */
    data class ConfirmLargeZip(
        val zipUri: Uri,
        val preScan: KeepPreScanResult,
        val options: KeepImportOptionsHolder
    ) : KeepImportUiState()

    data class Running(
        val progress: KeepImportProgress,
        val cancellable: Boolean = true
    ) : KeepImportUiState()

    data class Done(val summary: KeepImportSummary) : KeepImportUiState()

    data class Error(val message: String) : KeepImportUiState()
}

/**
 * v2.5.0 — Wrapper, damit wir die User-Auswahl aus dem Configuring-Dialog
 * über den ConfirmLargeZip-Step hinüber transportieren können, ohne
 * `KeepImportOptions` (Domain-Layer) im UI-State direkt zu referenzieren.
 * (Identisches Tupel, aber UI-Layer.)
 */
data class KeepImportOptionsHolder(
    val includeArchived: Boolean,
    val includeTrashed: Boolean,
    val conflictStrategy: dev.dettmer.simplenotes.noteimport.keep.conflict.ConflictStrategy
)
