package dev.dettmer.simplenotes.widget

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.security.AppLock
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.ui.theme.ThemePreferences
import dev.dettmer.simplenotes.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quick-Edit-Overlay über dem Homescreen.
 *
 * RemoteViews kennt kein `EditText` — ein Widget kann Text also prinzipiell nicht selbst
 * bearbeiten. Diese Activity ist der Ersatz: ein transparentes Fenster der eigenen App über dem
 * Launcher, **ohne** `SYSTEM_ALERT_WINDOW` (das bräuchte nur, wer über *fremde* Apps zeichnet).
 *
 * Text-Notiz → Inhalt ersetzen. Checkliste → ein Item anhängen. Den Modus bestimmt der
 * Notiztyp selbst (siehe [WidgetQuickEditViewModel]); der Intent trägt nur die Notiz-ID.
 */
class WidgetQuickEditActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NOTE_ID = WidgetQuickEditViewModel.ARG_NOTE_ID
    }

    /**
     * Ob die Einblend-Animation des Systems vorbei ist.
     *
     * Der Launcher enthüllt das Fenster vom Widget aus — dabei wandert die harte Kante des
     * Scrims über den Bildschirm, was wie ein Skalieren aussieht. Unterdrücken lässt sie sich
     * nicht (Theme, `ActivityOptions`, `overrideActivityTransition` sind am Gerät nachgemessen
     * wirkungslos, der Launcher besitzt die Transition). Also zeigt das Overlay währenddessen
     * nichts und blendet erst danach ein.
     *
     * `onEnterAnimationComplete` kommt verlässlich, aber spät (am Emulator 1045 ms nach
     * `onCreate`, die Enthüllung selbst war nach rund 500 ms durch). Deshalb gilt, was zuerst
     * eintritt: der Callback oder [ENTER_BUDGET_MS] ab `onCreate`.
     */
    private var entered by mutableStateOf(false)

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        entered = true
    }

    private val viewModel: WidgetQuickEditViewModel by viewModels {
        viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                handle[WidgetQuickEditViewModel.ARG_NOTE_ID] = intent.getStringExtra(EXTRA_NOTE_ID)
                WidgetQuickEditViewModel(application, handle)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Screenshot-Schutz wie im Editor — aber ohne AppLock.applySecureFlag(): dessen
        // Statusbar-/TaskDescription-Farben würden das transparente Overlay-Fenster einfärben.
        if (AppLock.isEnabled(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        DynamicColors.applyToActivityIfAvailable(this)
        // Pflicht für imePadding(): erst ohne decorFitsSystemWindows kommen die IME-Insets an.
        enableEdgeToEdge()

        lifecycleScope.launch {
            delay(ENTER_BUDGET_MS)
            entered = true
        }

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)

        setContent {
            SimpleNotesTheme(
                themeMode = ThemePreferences.getThemeMode(prefs),
                colorTheme = ThemePreferences.getColorTheme(prefs),
                fontSizeScale = ThemePreferences.getFontSizeScale(prefs)
            ) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                var closing by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { viewModel.events.collect { closing = true } }
                QuickEditOverlay(
                    state = state,
                    entered = entered,
                    closing = closing,
                    onTextChange = viewModel::updateText,
                    onSave = viewModel::save,
                    onDismiss = { closing = true },
                    onClosed = ::finish
                )
            }
        }
    }
}

/** Deckkraft des eigenen Scrims — der Homescreen soll erkennbar bleiben. */
private const val SCRIM_ALPHA = 0.6f

/**
 * Ein- und Ausblenddauer von Scrim und Karte.
 *
 * Das Fenster selbst kann dazu nichts beitragen: die Animation für „Task zurück zum Homescreen"
 * gehört dem Launcher, nicht der App — `windowAnimationStyle` (auch `taskCloseExitAnimation`)
 * wird dabei ignoriert, verifiziert am Emulator. Also animiert Compose den Inhalt selbst und
 * ruft `finish()` erst danach; der Launcher schiebt dann ein bereits leeres Fenster weg. Beim
 * Öffnen unterdrückt das Widget die Launcher-Animation (s. `quickEditAction` in
 * [NoteWidgetContent]), damit nicht beides gleichzeitig läuft.
 */
private const val OPEN_DURATION_MS = 300

/** Spätestens so lange nach `onCreate` wird eingeblendet — auch ohne Callback. */
private const val ENTER_BUDGET_MS = 300L

private const val CLOSE_DURATION_MS = 150

/** Ab dieser Höhe scrollt das Textfeld statt weiterzuwachsen (Platz für die Tastatur). */
private val TEXT_FIELD_MAX_HEIGHT = 220.dp

/** Deckkraft von Scrim und Karte: beim Öffnen ein, beim Schließen aus. */
@Composable
private fun overlayFade(entered: Boolean, closing: Boolean, onFadedOut: () -> Unit): Float {
    // Start erst, wenn die System-Animation durch ist — und dann nach dem ersten *gezeichneten*
    // Bild: ohne das `withFrameNanos` liefe die Animationsuhr schon während des teuren ersten
    // Layout-Frames (am Emulator gemessen: 84 ms), das erste sichtbare Bild stünde bereits bei
    // rund drei Vierteln Deckkraft. Der Timeout ist das Netz, falls `onEnterAnimationComplete`
    // ausbleibt (nicht jeder Launcher animiert überhaupt).
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(entered) {
        if (!entered) return@LaunchedEffect
        withFrameNanos { }
        shown = true
    }

    val fade by animateFloatAsState(
        targetValue = if (shown && !closing) 1f else 0f,
        animationSpec = if (closing) {
            tween(durationMillis = CLOSE_DURATION_MS, easing = FastOutLinearInEasing)
        } else {
            tween(durationMillis = OPEN_DURATION_MS, easing = FastOutSlowInEasing)
        },
        label = "quickEditFade",
        finishedListener = { if (it == 0f && closing) onFadedOut() }
    )
    return fade
}

@Composable
private fun QuickEditOverlay(
    state: QuickEditUiState,
    entered: Boolean,
    closing: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit
) {
    // Nichts rendern, solange die Notiz lädt — sonst blitzt eine leere Karte auf.
    if (!state.isReady) return

    val focusRequester = remember { FocusRequester() }
    val fade = overlayFade(entered = entered, closing = closing, onFadedOut = onClosed)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Zurück-Geste: sonst verschwindet das Fenster ohne Ausblenden.
    BackHandler(enabled = !closing) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.SpacingXLarge)
                // Tap auf die Karte schluckt der Scrim sonst als "außerhalb" → sofortiges Schließen.
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(modifier = Modifier.padding(Dimensions.SpacingLarge)) {
                Text(
                    text = state.noteTitle.ifBlank {
                        stringResource(
                            if (state.isChecklist) {
                                R.string.widget_quick_edit_add_item_title
                            } else {
                                R.string.widget_quick_edit_title
                            }
                        )
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimensions.SpacingMedium)
                        .heightIn(max = TEXT_FIELD_MAX_HEIGHT)
                        .focusRequester(focusRequester),
                    label = {
                        Text(
                            stringResource(
                                if (state.isChecklist) {
                                    R.string.widget_quick_edit_add_item_hint
                                } else {
                                    R.string.widget_quick_edit_hint
                                }
                            )
                        )
                    },
                    singleLine = state.isChecklist,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (state.isChecklist) ImeAction.Done else ImeAction.Default
                    ),
                    // Ein Item anlegen ist ein Ein-Zeilen-Vorgang — die Enter-Taste schließt ihn ab.
                    keyboardActions = KeyboardActions(onDone = { onSave() })
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimensions.SpacingMedium),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = onSave) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
