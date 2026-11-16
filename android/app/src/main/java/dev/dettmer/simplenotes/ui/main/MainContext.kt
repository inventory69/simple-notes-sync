package dev.dettmer.simplenotes.ui.main

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.sync.SyncScheduler
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Was die Teile der Hauptliste ([MainFolders], [MainTrash]) vom [MainViewModel] brauchen:
 * Scope, Prefs, die angezeigte Notizliste und die Kanäle zur Oberfläche.
 */
class MainContext(
    val app: Application,
    val scope: CoroutineScope,
    private val snackbar: MutableSharedFlow<MainViewModel.SnackbarData>,
    private val notes: StateFlow<List<Note>>,
    private val scrollToTop: MutableStateFlow<Boolean>,
    private val reloadNotes: (forceReload: Boolean) -> Unit
) {
    val prefs: SharedPreferences = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val syncScheduler by lazy { SyncScheduler(app) }

    /** Die angezeigte Liste (ohne Papierkorb, ohne Notizen im Undo-Fenster). */
    fun notes(): List<Note> = notes.value

    fun reloadNotes(forceReload: Boolean = false) = reloadNotes.invoke(forceReload)

    fun requestScrollToTop() {
        scrollToTop.value = true
    }

    suspend fun showSnackbar(data: MainViewModel.SnackbarData) = snackbar.emit(data)

    suspend fun showUndo(message: String, onUndo: () -> Unit) =
        snackbar.emit(MainViewModel.SnackbarData(message, actionLabel = string(R.string.snackbar_undo), onAction = onUndo))

    fun string(resId: Int, vararg formatArgs: Any): String =
        if (formatArgs.isEmpty()) app.getString(resId) else app.getString(resId, *formatArgs)

    fun plural(resId: Int, count: Int): String = app.resources.getQuantityString(resId, count, count)

    suspend fun refreshWidgets() = WidgetUpdateHelper.refreshAllWidgets(app)

    /** v2.5.0: Sync-Trigger zentral über [SyncScheduler]. */
    fun triggerOnSaveSync() = syncScheduler.triggerOnSaveSync(reason = "onDelete")

    /** 🌟 v1.6.0: Server-URL eingetragen (unabhängig vom Offline-Modus). */
    fun hasServerConfig(): Boolean {
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        return !serverUrl.isNullOrEmpty() && serverUrl != "http://" && serverUrl != "https://"
    }

    fun isOfflineMode(): Boolean = prefs.getBoolean(Constants.KEY_OFFLINE_MODE, Constants.DEFAULT_OFFLINE_MODE)
}
