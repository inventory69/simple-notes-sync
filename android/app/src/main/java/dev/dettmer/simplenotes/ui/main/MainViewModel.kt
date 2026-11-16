package dev.dettmer.simplenotes.ui.main

import android.app.Application
import android.content.Intent
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NoteMutations
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.storage.TrashManager
import dev.dettmer.simplenotes.sync.PendingServerDeletions
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.trashRetentionDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel der Hauptliste und Kompositionspunkt ihrer Teile (Refactoring-Slice 2):
 * [display] (Anzeige-Einstellungen), [query] (Sortierung, Filter, Suche), [selection],
 * [folders], [trash] (Löschen mit Undo) und [sync] (Sync-Auslösung, Banner, Server-Löschungen).
 * Hier bleiben die Notizliste, ihre sortierte Ansicht und die Aktionen auf ausgewählten Notizen.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
        private const val SEARCH_DEBOUNCE_MS = 300L
        const val EXTRA_FOLDER = "extra_folder"

        /** 🆕 v2.19.0: Problem-Benachrichtigungen öffnen den Sync-Status-Dialog. */
        const val ACTION_SHOW_SYNC_STATUS = "dev.dettmer.simplenotes.action.SHOW_SYNC_STATUS"
    }

    private val ioDispatcher = Dispatchers.IO
    private val storage = NotesStorage(application)
    private val folderStore = FolderStore(application)
    private val pendingServerDeletions = PendingServerDeletions(application)
    private val noteMutations = NoteMutations(storage, folderStore, pendingServerDeletions)

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _showSnackbar = MutableSharedFlow<SnackbarData>()
    val showSnackbar: SharedFlow<SnackbarData> = _showSnackbar.asSharedFlow()

    // Phase 3: Scroll-to-top, wenn eine neue Notiz oben erscheint
    private val _scrollToTop = MutableStateFlow(false)
    val scrollToTop: StateFlow<Boolean> = _scrollToTop.asStateFlow()

    private val ctx = MainContext(application, viewModelScope, _showSnackbar, _notes, _scrollToTop) { loadNotes(it) }
    private val prefs = ctx.prefs

    // 🆕 v2.9.0 (Trash): Löschen = in den Papierkorb verschieben
    private val trashManager = TrashManager(
        storage = storage,
        pendingServerDeletions = pendingServerDeletions,
        folderStore = folderStore,
        retentionMs = { prefs.trashRetentionDays() * Constants.DAY_MS }
    )

    // 🌟 v1.6.0: Server konfiguriert und Offline-Modus aus
    private val _isServerConfigured = MutableStateFlow(serverConfigured())
    val isServerConfigured: StateFlow<Boolean> = _isServerConfigured.asStateFlow()

    val display = MainDisplaySettings(prefs) { folders.currentFolder.value }
    val query = NoteListQuery(prefs) { folders.currentFolder.value }

    val sync: MainSyncController = MainSyncController(
        app = application,
        scope = viewModelScope,
        pendingServerDeletions = pendingServerDeletions,
        reloadNotes = { loadNotes(forceReload = true) },
        reloadFolders = { folders.refreshFolders() },
        showSnackbar = { _showSnackbar.emit(it) }
    )
    val selection: MainSelection = MainSelection(viewModelScope, ::visibleNotes, ::selectableFolders)
    val folders: MainFolders = MainFolders(ctx, folderStore, noteMutations, sync, selection) { folder ->
        query.loadSortFor(folder)
        display.loadCollapsedFor(folder)
    }
    val trash: MainTrash = MainTrash(ctx, trashManager, folders, selection, sync)

    private val changelogGateCleared = MutableStateFlow(false)

    // 🆕 v2.5.0: Kombinierter Filter-State (combine() kann nativ max. 5 Flows). 🆕 v2.7.0: Der
    // Ordner-Filter ist NICHT Teil dieser Pipeline, ihn wendet jede AnimatedContent-Pane selbst an
    // (sonst Flackern beim Ordnerwechsel, siehe MainScreen).
    private val filterCriteria =
        combine(query.noteFilter, query.colorFilter, query.showArchived) { f, c, a -> Triple(f, c, a) }

    // 🆕 v2.16.0 (#141): Läuft gerade eine Suche? Dann entfällt der Ordner-Filter, die Suche geht
    // über ALLE Ordner. Wird IN sortedNotesUnfoldered gesetzt (nicht aus dem Suchbegriff
    // abgeleitet), damit die Flanke zur debounce-verzögerten Liste passt.
    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    // Erste Notiz der sortierten Liste, um eine neue Notiz oben zu erkennen. Wird seit flowOn
    // auch vom Default-Dispatcher geschrieben.
    @Volatile
    private var previousFirstSortedNoteId: String? = null

    // Gesetzt beim Zurückkehren aus dem Editor, geleert nach dem nächsten Vergleich
    @Volatile
    private var expectNewNoteCheck = false

    /**
     * Notizen gefiltert (Typ, Farbe, Archiv), durchsucht und sortiert, Angeheftete zuerst.
     * OHNE Ordner-Filter: jede Pane filtert selbst nach ihrem `folderKey`.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class) // debounce(transform) is a preview API
    val sortedNotesUnfoldered: StateFlow<List<Note>> = combine(
        _notes,
        query.sortOption,
        query.sortDirection,
        filterCriteria,
        // 🔧 Perf: Suche entprellt, Leeren sofort
        query.searchQuery.debounce { q -> if (q.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
    ) { notes, option, direction, criteria, search ->
        val (filter, colorFilter, showArchived) = criteria
        val searched = searchNotes(filterNotes(notes, filter, colorFilter, showArchived), search)
        _searchActive.value = search.isNotBlank() // synchron zur ausgelieferten Liste
        val sorted = sortNotes(searched, option, direction)
        val result = sorted.filter { it.isPinned == true } + sorted.filter { it.isPinned != true }

        val newFirstId = result.firstOrNull()?.id
        if (expectNewNoteCheck && newFirstId != null && previousFirstSortedNoteId != null && newFirstId != previousFirstSortedNoteId) {
            _scrollToTop.value = true
            Logger.d(TAG, "📜 New note detected at top, triggering scroll-to-top")
        }
        expectNewNoteCheck = false
        previousFirstSortedNoteId = newFirstId
        result
    }.flowOn(Dispatchers.Default) // 🔧 Perf: filter/search/sort of the full list off the Main thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 🆕 v2.5.0: Notizen je Farbe nach Typ-Filter (vor Farbfilter), Datenquelle des Farb-Dropdowns. */
    val availableColors: StateFlow<Map<String?, Int>> = combine(_notes, query.noteFilter, query.showArchived) { notes, filter, archived ->
        filterNotes(notes, filter, showArchived = archived).groupingBy { it.color }.eachCount()
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Notizen je Ordner für die Ordner-Kacheln; archivierte zählen nicht (v2.11.0). */
    val folderNoteCounts: StateFlow<Map<String, Int>> = combine(_notes, folders.list) { notes, list ->
        val counts = notes.filter { !it.isArchived }.groupingBy { it.folderName }.eachCount()
        list.associate { f -> f.name to (counts[f.name] ?: 0) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    data class SnackbarData(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
        val longDuration: Boolean = false // 🆕 e.g. onboarding hints that need more time to read
    )

    init {
        // v2.3.0 (FIX-013): Check for stale sync state on every ViewModel init
        SyncStateManager.checkAndResetStaleState()
        // 🆕 Einmaliger Hinweis zum Umsortieren der Abschnitte, sobald das Changelog-Sheet fertig
        // ist UND mindestens eine Abschnitts-Überschrift sichtbar ist.
        viewModelScope.launch {
            combine(changelogGateCleared, sortedNotesUnfoldered, folders.list) { gateCleared, notes, list ->
                gateCleared && (notes.any { it.isPinned == true } || list.isNotEmpty())
            }
                .filter { it && !prefs.getBoolean(Constants.KEY_SECTION_REORDER_HINT_SHOWN, false) }
                .take(1)
                .collect {
                    prefs.edit { putBoolean(Constants.KEY_SECTION_REORDER_HINT_SHOWN, true) }
                    emitSnackbar(ctx.string(R.string.section_reorder_hint), longDuration = true)
                }
        }
        viewModelScope.launch(ioDispatcher) {
            // 🆕 v2.9.0 (Trash): abgelaufene Papierkorb-Einträge automatisch endgültig löschen.
            if (trashManager.purgeExpired() > 0) ctx.triggerOnSaveSync()
            loadNotesAsync()
            folders.reloadFromStore()
            _isReady.value = true
        }
    }

    fun handleIncomingIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_FOLDER)?.let { folders.enterFolder(it) }
        if (intent.action == ACTION_SHOW_SYNC_STATUS) sync.openSyncStatus()
    }

    /** Nach der Rückkehr aus den Einstellungen (onResume): Offline-Modus oder Server können sich geändert haben. */
    fun refreshOfflineModeState() {
        _isServerConfigured.value = serverConfigured()
    }

    private fun serverConfigured() = !ctx.isOfflineMode() && ctx.hasServerConfig()

    /** Called from ComposeMainActivity once UpdateChangelogSheet has nothing left to show. */
    fun onChangelogDismissed() {
        changelogGateCleared.value = true
    }

    /** 🆕 v2.11.0 (Archive): Archiv ist eine flache Liste, immer aus der Root-Ansicht heraus. */
    fun setShowArchived(show: Boolean) {
        query.setShowArchived(show)
        if (show) folders.show(null)
        selection.clearSelection()
    }

    fun emitSnackbar(message: String, longDuration: Boolean = false) {
        viewModelScope.launch { _showSnackbar.emit(SnackbarData(message = message, longDuration = longDuration)) }
    }

    /**
     * 🆕 v2.9.0 / v2.11.0: nur nicht-getrashte Notizen (archivierte bleiben, der Archiv-Filter
     * arbeitet auf dieser Liste), ohne die Notizen im Undo-Fenster.
     */
    private suspend fun loadNotesAsync(forceReload: Boolean = false) {
        val hidden = trash.hiddenIds.value
        val visible = storage.loadNonTrashedNotes(forceReload).filter { it.id !in hidden }
        withContext(Dispatchers.Main) { _notes.value = visible }
    }

    fun loadNotes(forceReload: Boolean = false) {
        viewModelScope.launch(ioDispatcher) { loadNotesAsync(forceReload) }
    }

    fun resetScrollToTop() {
        _scrollToTop.value = false
    }

    fun scrollToTop() {
        _scrollToTop.value = true
    }

    /** Beim Zurückkehren aus dem Editor: die nächste Liste darf eine neue Notiz oben melden. */
    fun notifyReturningFromEditor() {
        expectNewNoteCheck = true
    }

    /** 🆕 v2.7.0: Notizen der sichtbaren Ansicht. Archiv und Suche (#141) sind flach über alle Ordner. */
    private fun visibleNotes(): List<Note> =
        if (query.showArchived.value || _searchActive.value) {
            sortedNotesUnfoldered.value
        } else {
            sortedNotesUnfoldered.value.filter { it.folderName == folders.currentFolder.value }
        }

    /** Ordner-Kacheln gibt es nur im Root, nicht im Archiv und nicht während einer Suche (#141). */
    private fun selectableFolders(): Set<String> =
        if (folders.currentFolder.value == null && !query.showArchived.value && !_searchActive.value) {
            folders.list.value.map { it.name }.toSet()
        } else {
            emptySet()
        }

    /** 🆕 v2.5.0 (Issue #65): Farbe für alle ausgewählten Notizen und Ordner. */
    fun setColorForSelected(hex: String?) {
        val noteIds = selection.selectedNotes.value
        val folderNames = selection.selectedFolders.value
        if (noteIds.isEmpty() && folderNames.isEmpty()) return
        viewModelScope.launch {
            withContext(ioDispatcher) {
                noteMutations.setColor(noteIds, hex)
                folderNames.forEach { name -> folderStore.setColor(name, hex) }
            }
            folders.reloadFromStore()
            selection.clearSelection()
            loadNotes()
            ctx.refreshWidgets()
            ctx.triggerOnSaveSync()
        }
    }

    fun togglePinForSelected() {
        val ids = selection.selectedNotes.value
        if (ids.isEmpty()) return
        val allPinned = _notes.value.filter { it.id in ids }.all { it.isPinned == true }
        viewModelScope.launch {
            withContext(ioDispatcher) { noteMutations.setPinned(ids, if (allPinned) null else true) }
            selection.clearSelection()
            loadNotes()
            _scrollToTop.value = true
            ctx.refreshWidgets()
        }
    }

    /**
     * 🆕 v2.11.0 (Archive): Archivieren-Toggle für die Auswahl, wie beim Anheften: sind alle
     * archiviert → aufheben, sonst archivieren. Mit Undo.
     */
    fun toggleArchiveForSelected() {
        val ids = selection.selectedNotes.value
        val targets = _notes.value.filter { it.id in ids }
        if (targets.isEmpty()) return
        val allArchived = targets.all { it.isArchived }
        val message = ctx.plural(if (allArchived) R.plurals.snackbar_notes_unarchived else R.plurals.snackbar_notes_archived, targets.size)
        viewModelScope.launch {
            val originals = withContext(ioDispatcher) { noteMutations.setArchived(ids, archived = !allArchived) }
            selection.clearSelection()
            afterArchiveToggle(message, originals, forceReload = false)
        }
    }

    /** 🆕 v2.11.0: Archivieren aus dem Editor (die Activity hat ihn schon geschlossen), mit Undo. */
    fun toggleArchiveFromEditor(noteId: String) {
        viewModelScope.launch {
            val note = withContext(ioDispatcher) { storage.loadNote(noteId) } ?: return@launch
            val originals = withContext(ioDispatcher) { noteMutations.setArchived(listOf(noteId), archived = !note.isArchived) }
            val messageRes = if (note.isArchived) R.string.snackbar_note_unarchived else R.string.snackbar_note_archived
            val message = ctx.string(messageRes, note.title)
            afterArchiveToggle(message, originals, forceReload = true)
        }
    }

    private suspend fun afterArchiveToggle(message: String, originals: List<Note>, forceReload: Boolean) {
        loadNotes(forceReload)
        ctx.showUndo(message) {
            viewModelScope.launch {
                withContext(ioDispatcher) { noteMutations.restoreArchived(originals) }
                loadNotes()
                ctx.refreshWidgets()
                ctx.triggerOnSaveSync()
            }
        }
        ctx.refreshWidgets()
        ctx.triggerOnSaveSync()
    }
}
