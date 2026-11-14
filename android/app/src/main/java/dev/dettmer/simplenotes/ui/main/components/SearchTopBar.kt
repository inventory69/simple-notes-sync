package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncLock
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.sync.SyncStatusSummary
import dev.dettmer.simplenotes.ui.theme.Dimensions

/**
 * UI-Beta (U5): Suchfeld dauerhaft oben statt Titelzeile. Leer zeigt es [placeholder] als Ruhetext
 * (App-Titel, auch den selbst gewählten, bzw. den Ordnernamen), Tippen startet die Suche, das
 * Filter-Symbol im Feld klappt die Chip-Zeile auf. In der Ordneransicht steht links der Zurück-Pfeil.
 * (U6): Sync-Status und Sync als ein Wolken-Symbol, siehe [SyncCloudButton].
 * Beim Tippen ins Feld (Tastatur offen) weichen Wolke und Zahnrad, das Feld wird breit und die Lupe
 * zum Zurück-Pfeil; [onSearchingChange] meldet das, damit die Übersicht darunter zurücktreten kann.
 */
@Composable
fun SearchTopBar(
    placeholder: String,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    showFilterRow: Boolean,
    onFilterToggle: () -> Unit,
    onBack: (() -> Unit)?,
    onSettingsClick: () -> Unit,
    onSearchingChange: (Boolean) -> Unit,
    syncAction: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Erst mit offener Tastatur: wer sie zuklappt, bekommt Wolke und Zahnrad zurück, der Cursor bleibt.
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val searching = focused && imeVisible
    // Verschwindet die Leiste mitten in der Suche (Auswahlmodus), muss die Übersicht trotzdem zurück auf voll.
    DisposableEffect(searching) {
        onSearchingChange(searching)
        onDispose { if (searching) onSearchingChange(false) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = Dimensions.SpacingLarge,
                end = Dimensions.SpacingSmall,
                top = Dimensions.SpacingMedium,
                bottom = Dimensions.SpacingSmall
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchField(
            placeholder = placeholder,
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            showFilterRow = showFilterRow,
            onFilterToggle = onFilterToggle,
            onBack = onBack,
            searching = searching,
            onFocusChange = { focused = it },
            modifier = Modifier
                .weight(1f)
                .height(Dimensions.MinTouchTarget)
        )
        AnimatedVisibility(visible = !searching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                syncAction()
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings))
                }
            }
        }
    }
}

/** Das runde Suchfeld selbst: Lupe bzw. Zurück-Pfeil, Eingabe, Löschen-Kreuz und Filter-Knopf. */
@Composable
private fun SearchField(
    placeholder: String,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    showFilterRow: Boolean,
    onFilterToggle: () -> Unit,
    onBack: (() -> Unit)?,
    searching: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Suchen zuerst: im Ordner beendet der Pfeil erst die Suche, erst danach führt er aus dem Ordner.
            when {
                searching -> IconButton(onClick = { focusManager.clearFocus() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                else -> Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimensions.SpacingLarge, end = Dimensions.SpacingMediumLarge)
                )
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                decorationBox = { inner ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChanged("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
            IconButton(onClick = onFilterToggle) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.toggle_filter_row),
                    tint = if (showFilterRow) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }
    }
}

/**
 * Symbol und Farbe je Sync-Zustand, gemeinsam für die Wolke oben und den Kopf des Status-Sheets.
 * Fehler bewusst nicht als durchgestrichene Wolke: die steht auf den Karten für „nur lokal“.
 */
@Composable
internal fun syncStateVisual(state: SyncStatusSummary.State, neutral: Color): Pair<ImageVector, Color> {
    val colors = MaterialTheme.colorScheme
    return when (state) {
        SyncStatusSummary.State.OK -> Icons.Outlined.CloudDone to neutral
        SyncStatusSummary.State.PENDING -> Icons.Outlined.CloudUpload to neutral
        SyncStatusSummary.State.ATTENTION -> Icons.Outlined.Cloud to colors.tertiary
        SyncStatusSummary.State.FAILED -> Icons.Outlined.SyncProblem to colors.tertiary
        SyncStatusSummary.State.STALE -> Icons.Outlined.SyncProblem to colors.error
        SyncStatusSummary.State.BLOCKED -> Icons.Outlined.SyncLock to colors.tertiary
    }
}

/** Ring um die Wolke, solange ein Sync läuft (auch der stille beim Öffnen der App). */
private val SYNC_RING_SIZE = 32.dp

/**
 * UI-Beta (U6): ein Wolken-Symbol statt „Sync status“ und „Sync“. Zeigt Zustand und Zähler, Tippen
 * öffnet den Status, der „Try again“ anbietet. Der manuelle Sync kostet damit einen Tipp mehr,
 * Herunterziehen bleibt. Ohne eingerichteten Server ([onClick] = null) erscheint nichts.
 */
@Composable
fun SyncCloudButton(state: SyncStatusSummary.State, isSyncing: Boolean, badgeCount: Int, onClick: (() -> Unit)?) {
    if (onClick == null) return
    val (icon, tint) = syncStateVisual(state, LocalContentColor.current)
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (badgeCount > 0 && !isSyncing) Badge { Text(badgeCount.toString()) }
            }
        ) {
            // Feste 24 dp, damit der größere Ring den Zähler nicht verschiebt.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                Crossfade(
                    targetState = if (isSyncing) Icons.Outlined.Cloud to LocalContentColor.current else icon to tint,
                    label = "syncCloud"
                ) { (shownIcon, shownTint) ->
                    Icon(shownIcon, contentDescription = stringResource(R.string.sync_legend_button), tint = shownTint)
                }
                AnimatedVisibility(visible = isSyncing, enter = fadeIn(), exit = fadeOut()) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.requiredSize(SYNC_RING_SIZE)
                    )
                }
            }
        }
    }
}
