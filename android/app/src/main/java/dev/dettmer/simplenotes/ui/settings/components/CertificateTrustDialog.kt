package dev.dettmer.simplenotes.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import java.text.DateFormat
import java.util.Date

/**
 * 🆕 v2.21.0: Zertifikat aus dem Verbindungstest zum Vertrauen anbieten. Der Fingerabdruck ist
 * auswählbar, damit man ihn mit `openssl x509 -noout -fingerprint -sha256` am Server vergleichen kann.
 */
@Composable
fun CertificateTrustDialog(
    prompt: SettingsViewModel.CertificatePrompt,
    onTrust: () -> Unit,
    onCancel: () -> Unit
) {
    val cert = prompt.certificate
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.cert_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.cert_dialog_message))
                if (prompt.replacesTrusted) {
                    Text(
                        text = stringResource(R.string.cert_dialog_changed),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                CertificateField(stringResource(R.string.cert_dialog_issuer), cert.issuer)
                CertificateField(
                    stringResource(R.string.cert_dialog_valid),
                    stringResource(
                        R.string.cert_dialog_valid_range,
                        dateFormat.format(Date(cert.notBefore)),
                        dateFormat.format(Date(cert.notAfter))
                    )
                )
                // 4 Zeilen à 8 Bytes, bricht nie mitten im Byte um
                val fingerprint = cert.sha256.split(':').chunked(8).joinToString("\n") { it.joinToString(":") }
                CertificateField(stringResource(R.string.cert_dialog_fingerprint), fingerprint, monospace = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) { Text(stringResource(R.string.cert_dialog_trust)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun CertificateField(label: String, value: String, monospace: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else null
            )
        }
    }
}
