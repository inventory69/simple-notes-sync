package dev.dettmer.simplenotes.markdown

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.images.ImageMetadata
import dev.dettmer.simplenotes.images.readImageMetadata
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** M3-Dialog mit EXIF-Metadaten von [assetFile]. Null-Felder werden übersprungen. */
@Composable
fun ImageInfoDialog(assetFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val metadata by produceState<ImageMetadata?>(null, assetFile) {
        value = withContext(Dispatchers.IO) { readImageMetadata(assetFile) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_info_title)) },
        text = {
            val m = metadata
            if (m != null) {
                Column {
                    InfoRow(stringResource(R.string.image_info_dimensions), "${m.widthPx} × ${m.heightPx} px")
                    InfoRow(stringResource(R.string.image_info_file_size), Formatter.formatShortFileSize(context, m.fileSizeBytes))
                    m.dateTaken?.let { InfoRow(stringResource(R.string.image_info_date_taken), it) }
                    val camera = listOfNotNull(m.cameraMake, m.cameraModel).joinToString(" ")
                    if (camera.isNotBlank()) InfoRow(stringResource(R.string.image_info_camera), camera)
                    m.iso?.let { InfoRow(stringResource(R.string.image_info_iso), "ISO $it") }
                    m.exposureTime?.let { InfoRow(stringResource(R.string.image_info_exposure), it) }
                    m.focalLengthMm?.let { InfoRow(stringResource(R.string.image_info_focal_length), "%.0f mm".format(it)) }
                    m.gps?.let { (lat, lon) ->
                        InfoRow(stringResource(R.string.image_info_location), String.format(Locale.US, "%.5f, %.5f", lat, lon))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.image_info_close)) }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
