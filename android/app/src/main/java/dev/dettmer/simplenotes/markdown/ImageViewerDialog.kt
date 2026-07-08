package dev.dettmer.simplenotes.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.dettmer.simplenotes.storage.AssetStore

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val VIEWER_BACKGROUND_ALPHA = 0.95f

/**
 * Fullscreen-Bildbetrachter: Tap dismisst, Doppel-Tap toggelt zwischen 1x und
 * [DOUBLE_TAP_SCALE]x, Pinch zoomt (geclampter Pan). Back dismisst via Dialog-Default.
 */
@Composable
fun ImageViewerDialog(assetName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val assetFile = remember(context, assetName) { AssetStore(context).getAssetFile(assetName) }

    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = VIEWER_BACKGROUND_ALPHA))
                .onGloballyPositioned { containerSize = it.size }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > MIN_SCALE) {
                                scale = MIN_SCALE
                                offset = Offset.Zero
                            } else {
                                scale = DOUBLE_TAP_SCALE
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val maxX = (newScale - 1f) * containerSize.width / 2f
                        val maxY = (newScale - 1f) * containerSize.height / 2f
                        scale = newScale
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
        ) {
            AsyncImage(
                model = assetFile,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
    }
}
