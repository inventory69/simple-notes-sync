package dev.dettmer.simplenotes.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.images.readImageMetadata
import dev.dettmer.simplenotes.images.shouldShowImageInfo
import dev.dettmer.simplenotes.ui.theme.Dimensions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SIZE_PRESETS = listOf(
    R.string.image_menu_size_small to 25,
    R.string.image_menu_size_medium to 50,
    R.string.image_menu_size_large to 75,
    R.string.image_menu_size_full to 100
)

/**
 * Modernes M3-Overlay bei Long-Press auf ein Block-Bild: Ausrichtung, Größen-Presets, bei
 * lesbarem Original-Qualität-Bild (nicht re-encodetes WebP) ein „i"-Button — unabhängig davon,
 * ob EXIF-Tags vorhanden sind. Popup gibt Outside-Tap/Back-Dismiss gratis — kein Scrim, kein
 * ModalBottomSheet (existiert nirgends in der App).
 */
// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("LongMethod")
@Composable
fun ImageActionsMenu(
    assetFile: File,
    currentSize: Int,
    currentAlign: ImageAlign,
    currentAlt: String,
    onSelect: (sizePercent: Int, align: ImageAlign, altText: String) -> Unit,
    onInfoClick: () -> Unit,
    // null = Asset fehlt (Sync noch nicht durch) → kein Kopieren-Button statt leerem Clip.
    onCopyImage: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    // I/O erst beim Öffnen des Menüs. Re-encodete WebP → kein "i"; jedes andere lesbare
    // (Original-Qualität-)Bild → "i" sichtbar, auch ohne EXIF.
    val showInfo by produceState(false, assetFile) {
        value = withContext(Dispatchers.IO) { shouldShowImageInfo(readImageMetadata(assetFile), assetFile.extension) }
    }
    var altText by remember(currentAlt) { mutableStateOf(currentAlt) }
    // Größe/Ausrichtung committen sich selbst (Button ruft onSelect direkt auf). Alt-Text hat
    // keinen eigenen Bestätigen-Button — Outside-Tap/Back muss die Eingabe daher hier nachziehen.
    val commitDismiss = {
        if (altText != currentAlt) onSelect(currentSize, currentAlign, altText)
        onDismiss()
    }

    Popup(alignment = Alignment.Center, onDismissRequest = commitDismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Min).padding(12.dp)) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlignButton(
                        ImageAlign.LEFT,
                        currentAlign,
                        Icons.AutoMirrored.Filled.FormatAlignLeft,
                        R.string.image_menu_align_left
                    ) {
                        onSelect(currentSize, ImageAlign.LEFT, altText)
                    }
                    AlignButton(ImageAlign.CENTER, currentAlign, Icons.Filled.FormatAlignCenter, R.string.image_menu_align_center) {
                        onSelect(currentSize, ImageAlign.CENTER, altText)
                    }
                    AlignButton(
                        ImageAlign.RIGHT,
                        currentAlign,
                        Icons.AutoMirrored.Filled.FormatAlignRight,
                        R.string.image_menu_align_right
                    ) {
                        onSelect(currentSize, ImageAlign.RIGHT, altText)
                    }
                    AlignButton(
                        ImageAlign.INLINE,
                        currentAlign,
                        Icons.AutoMirrored.Filled.WrapText,
                        R.string.image_menu_align_inline
                    ) {
                        onSelect(currentSize, ImageAlign.INLINE, altText)
                    }
                    if (showInfo || onCopyImage != null) {
                        VerticalDivider(modifier = Modifier.padding(horizontal = Dimensions.SpacingSmall))
                    }
                    if (onCopyImage != null) {
                        IconButton(onClick = {
                            onCopyImage()
                            commitDismiss()
                        }) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.image_menu_copy)
                            )
                        }
                    }
                    if (showInfo) {
                        IconButton(onClick = {
                            onInfoClick()
                            commitDismiss()
                        }) {
                            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.image_menu_info))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow {
                    SIZE_PRESETS.forEachIndexed { index, (labelRes, size) ->
                        SegmentedButton(
                            selected = currentSize == size,
                            onClick = { onSelect(size, currentAlign, altText) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SIZE_PRESETS.size)
                        ) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = altText,
                    onValueChange = { altText = it },
                    label = { Text(stringResource(R.string.image_menu_alt_text)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AlignButton(value: ImageAlign, current: ImageAlign, icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    FilledIconToggleButton(checked = current == value, onCheckedChange = { onClick() }) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}
