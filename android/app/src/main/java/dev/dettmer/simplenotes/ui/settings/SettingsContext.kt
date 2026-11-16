package dev.dettmer.simplenotes.ui.settings

import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Was jede Einstellungs-Domäne vom [SettingsViewModel] braucht: Prefs, den ViewModel-Scope und
 * die beiden Kanäle zur Oberfläche (Snackbar, Activity-Events).
 */
class SettingsContext(
    val app: Application,
    val scope: CoroutineScope,
    val prefs: SharedPreferences,
    private val snackbar: MutableSharedFlow<String>,
    private val events: MutableSharedFlow<SettingsViewModel.SettingsEvent>
) {
    /** Aus synchronen Click-Handlern aufrufbar. */
    fun showSnackbar(message: String) {
        snackbar.tryEmit(message)
    }

    suspend fun toast(message: String) = snackbar.emit(message)

    suspend fun emit(event: SettingsViewModel.SettingsEvent) = events.emit(event)

    /**
     * 🌍 v1.7.1: String in der App-Sprache. Der Application-Context kennt die per-App-Sprache
     * (AppCompatDelegate) nicht, deshalb ein Context mit der gewählten Locale.
     */
    fun string(resId: Int, vararg formatArgs: Any): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val context = if (appLocales.isEmpty) {
            app
        } else {
            app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(appLocales.get(0)) })
        }
        // Ohne Argumente nicht formatieren: ein wörtliches "%" im Text würde sonst werfen.
        return if (formatArgs.isEmpty()) context.getString(resId) else context.getString(resId, *formatArgs)
    }
}

/**
 * Ein Bool-Schalter, der 1:1 in den Prefs liegt: [flow] für die Oberfläche, [set] schreibt beides,
 * [reload] liest nach einem Restore neu.
 */
class BoolPref(private val prefs: SharedPreferences, private val key: String, private val default: Boolean) {
    private val state = MutableStateFlow(prefs.getBoolean(key, default))
    val flow: StateFlow<Boolean> = state.asStateFlow()

    fun set(value: Boolean) {
        prefs.edit { putBoolean(key, value) }
        state.value = value
    }

    fun reload() {
        state.value = prefs.getBoolean(key, default)
    }
}
