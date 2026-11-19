package dev.dettmer.simplenotes.ui.main

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.requestUpdateFlow
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.ui.theme.LocalNewDesign
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.text.NumberFormat
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val TAG = "InAppUpdate"
private const val KEY_ASKED_VERSION = "play_update_asked_version_code"

private sealed interface BannerState {
    /** [fraction] ist null, solange Play die Größe noch nicht kennt (unbestimmter Balken). */
    data class Downloading(val fraction: Float?) : BannerState

    data object Ready : BannerState
}

/**
 * Play-Flavor: flexibles In-App-Update über Play Core (Gegenstück zum No-op in `src/noPlay`).
 *
 * Fragt einmal pro neuer versionCode per Play-Dialog, zeigt den Download als Banner und lässt das
 * Banner danach mit „Neu starten“ stehen, bis jemand tippt. Eine Snackbar lief im Test nach 10 s
 * verdeckt ab. Der Flow läuft nur, solange der Hauptbildschirm sichtbar ist: Ein offener Editor wird
 * also nie vom Neustart überrascht. Ohne Play Store oder bei einer Installation an Play vorbei
 * schlägt die Abfrage fehl, dann passiert einfach nichts.
 */
@Composable
fun InAppUpdateBanner() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val updates = remember {
        AppUpdateManagerFactory.create(context).requestUpdateFlow().catch { e ->
            Logger.d(TAG, "Update check unavailable: ${e.message}")
            emit(AppUpdateResult.NotAvailable)
        }
    }
    val result by updates.collectAsStateWithLifecycle(AppUpdateResult.NotAvailable)
    val r = result

    if (r is AppUpdateResult.Available) LaunchedEffect(r) { askOncePerVersion(context, r, launcher) }

    val state = when (r) {
        is AppUpdateResult.InProgress -> {
            val s = r.installState
            when (s.installStatus()) {
                InstallStatus.PENDING, InstallStatus.DOWNLOADING -> BannerState.Downloading(
                    s.totalBytesToDownload().takeIf { it > 0 }?.let { s.bytesDownloaded().toFloat() / it }
                )
                else -> null
            }
        }
        is AppUpdateResult.Downloaded -> BannerState.Ready
        else -> null
    }

    UpdateBanner(
        state = state,
        onRestart = {
            val downloaded = r as? AppUpdateResult.Downloaded ?: return@UpdateBanner
            // Startet die App neu. Scheitert es, installiert Play das Update später selbst.
            scope.launch {
                runCatching { downloaded.completeUpdate() }
                    .onFailure { Logger.w(TAG, "completeUpdate failed: ${it.message}") }
            }
        }
    )
}

private fun askOncePerVersion(
    context: Context,
    update: AppUpdateResult.Available,
    launcher: ActivityResultLauncher<IntentSenderRequest>
) {
    val info = update.updateInfo
    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    if (!info.isFlexibleUpdateAllowed || prefs.getInt(KEY_ASKED_VERSION, 0) == info.availableVersionCode()) return
    // Vor dem Dialog merken: Abgelehnt heißt, erst beim nächsten Release wieder fragen.
    prefs.edit { putInt(KEY_ASKED_VERSION, info.availableVersionCode()) }
    runCatching { update.startFlexibleUpdate(launcher) }
        .onFailure { Logger.w(TAG, "startFlexibleUpdate failed: ${it.message}") }
}

/**
 * Hülle, Farben und Übergänge wie beim Sync-Banner: altes Design Band über die volle Breite,
 * UI-Beta Pille in Suchfeld-Optik. Der letzte Zustand bleibt während der Ausblende-Animation stehen.
 */
@Composable
private fun UpdateBanner(state: BannerState?, onRestart: () -> Unit) {
    var lastVisible by remember { mutableStateOf<BannerState>(BannerState.Downloading(null)) }
    if (state != null) lastVisible = state

    val newDesign = LocalNewDesign.current
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(tween(durationMillis = 350, easing = EaseOutCubic)),
        exit = shrinkVertically(tween(durationMillis = 280, easing = EaseInCubic)) +
            fadeOut(tween(durationMillis = 220, easing = EaseInCubic))
    ) {
        Surface(
            color = if (newDesign) colors.surfaceContainerHigh else colors.primaryContainer,
            contentColor = if (newDesign) colors.onSurfaceVariant else colors.onPrimaryContainer,
            shape = if (newDesign) CircleShape else RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (newDesign) {
                        // Gleicher Abstand wie die Sync-Pille unter dem Suchfeld.
                        Modifier.padding(
                            start = Dimensions.SpacingLarge,
                            end = Dimensions.SpacingLarge,
                            top = Dimensions.SpacingMedium,
                            bottom = Dimensions.SpacingSmall
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            AnimatedContent(
                targetState = lastVisible,
                transitionSpec = {
                    fadeIn(tween(durationMillis = 220, easing = EaseOutCubic)) togetherWith
                        fadeOut(tween(durationMillis = 160, easing = EaseInCubic))
                },
                // Nur der Wechsel Download → Bereit blendet über, Fortschritt aktualisiert in place.
                contentKey = { it::class },
                label = "updateBanner"
            ) { s ->
                when (s) {
                    is BannerState.Downloading -> DownloadProgress(s.fraction, newDesign)
                    BannerState.Ready -> RestartPrompt(onRestart, newDesign)
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(fraction: Float?, newDesign: Boolean) {
    val accent = if (newDesign) MaterialTheme.colorScheme.primary else LocalContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (newDesign) 20.dp else Dimensions.SpacingLarge, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.play_update_downloading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (fraction != null) {
                val locale = LocalConfiguration.current.locales[0]
                Text(
                    NumberFormat.getPercentInstance(locale).format(fraction.toDouble()),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = accent.copy(alpha = 0.2f)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = accent.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun RestartPrompt(onRestart: () -> Unit, newDesign: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Neues Design: 4 dp Rand um den runden Knopf, er sitzt als kleine Pille in der Pille.
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (newDesign) 20.dp else Dimensions.SpacingLarge, end = Dimensions.SpacingSmall)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (newDesign) colors.primary else LocalContentColor.current
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            stringResource(R.string.play_update_ready),
            style = MaterialTheme.typography.bodyMedium,
            color = if (newDesign) colors.onSurface else LocalContentColor.current,
            modifier = Modifier.weight(1f)
        )
        if (newDesign) {
            FilledTonalButton(onClick = onRestart, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.play_update_restart))
            }
        } else {
            TextButton(onClick = onRestart) { Text(stringResource(R.string.play_update_restart)) }
        }
    }
}
