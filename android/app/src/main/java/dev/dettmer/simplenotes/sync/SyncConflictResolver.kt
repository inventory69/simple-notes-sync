package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🆕 v2.16.0: Auflösung eines Sync-Konflikts — die beiden Wege aus dem
 * [SyncStatus.CONFLICT]-Zustand heraus.
 *
 * Ohne diese Klasse ist eine markierte Notiz eine Sackgasse: Der Uploader nimmt nur
 * `LOCAL_ONLY` und `PENDING`, lädt sie also nie hoch, und der Downloader überschreibt sie
 * seit diesem Release ebenfalls nicht mehr. Sie bliebe für immer stehen — und ein simples
 * Weiter-Editieren hilft nicht, weil das `If-Match` beim Upload erneut auf `412` läuft.
 *
 * Der Nutzer kann sich die Server-Fassung vorher ansehen ([fetchServerVersion]), aber nicht
 * zeilenweise mergen: Der Desktop-Client bietet an derselben Stelle
 * `resolve_conflict(id, "keep_mine" | "use_server")`, und mehr braucht es für eine
 * persönliche Notiz-App nicht.
 */
class SyncConflictResolver(
    private val context: Context,
    private val storage: NotesStorage = NotesStorage(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    prefsOverride: SharedPreferences? = null,
    // Testeingriff: sonst baut der ConnectionManager den Client aus dem CredentialStore.
    private val webdavProvider: (() -> WebDavClient?)? = null
) {
    private val prefs: SharedPreferences = prefsOverride
        ?: context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val connectionManager = ConnectionManager(context, prefs)
    private val urlBuilder = SyncUrlBuilder(prefs)
    private val eTagCache = ETagCache(prefs)

    /**
     * "Meine behalten": Die lokale Fassung gewinnt.
     *
     * Der gecachte ETag muss weg — er ist der Grund, warum der nächste Upload wieder auf `412`
     * liefe. Ohne ihn geht das PUT ohne Precondition raus und setzt die lokale Fassung
     * bewusst über die des Servers. Der Content-Hash muss mit, sonst hält der Uploader die
     * Notiz für unverändert und überspringt sie ([NoteUploader] Skip-Logik).
     */
    suspend fun keepLocal(noteId: String): Boolean = withContext(ioDispatcher) {
        val note = storage.loadNote(noteId) ?: return@withContext false
        eTagCache.batchUpdate(mapOf("etag_json_$noteId" to null))
        prefs.edit { remove("content_hash_$noteId") }
        storage.saveNote(note.copy(syncStatus = SyncStatus.PENDING))
        ActivityLog.log(
            ActivityLog.Op.CONFLICT,
            ActivityLog.Src.LOCAL,
            id = noteId,
            title = note.title,
            folder = note.folderName,
            why = "resolved_keep_local"
        )
        Logger.i(TAG, "✅ Conflict resolved (keep local): $noteId")
        true
    }

    /**
     * Die Fassung, die gerade auf dem Server liegt — Grundlage der Vergleichsansicht und
     * von [useServer].
     *
     * Sie wird bei Bedarf geholt statt beim Erkennen des Konflikts vorgehalten — ein GET in
     * dem Moment, in dem der Nutzer hinsieht, ist billiger als eine zweite Kopie im Storage
     * samt Lebenszyklus, und liefert außerdem den aktuellen Stand statt eines womöglich
     * Tage alten Schnappschusses.
     *
     * Der Ordner wird aus der lokalen Notiz übernommen: Die Zuordnung ist eine Eigenschaft
     * des Ablageorts, keine des Inhalts — sie kommt aus dem Pfad, unter dem die Datei liegt,
     * nicht aus der JSON.
     *
     * @return `null`, wenn die Notiz lokal fehlt, kein Server eingerichtet ist oder der GET scheitert.
     */
    suspend fun fetchServerVersion(noteId: String): Note? = withContext(ioDispatcher) {
        val local = storage.loadNote(noteId) ?: return@withContext null
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrBlank()) {
            Logger.w(TAG, "⚠️ Cannot fetch server version: no server configured")
            return@withContext null
        }
        val webdav = (webdavProvider?.invoke() ?: connectionManager.getOrCreateClient()) ?: run {
            Logger.w(TAG, "⚠️ Cannot fetch server version: no credentials")
            return@withContext null
        }

        val noteUrl = urlBuilder.getNotesFolderUrl(serverUrl, local.folderName) + "$noteId.json"
        val remote = try {
            val json = webdav.get(noteUrl).use { it.bufferedReader().readText() }
            Note.fromJson(json)
        } catch (e: java.io.IOException) {
            Logger.e(TAG, "❌ Failed to fetch server version of $noteId: ${e.message}")
            null
        } finally {
            if (webdavProvider == null) connectionManager.clearSession()
        }
        remote?.copy(folderName = local.folderName)
    }

    /**
     * "Server nehmen": Die Server-Fassung gewinnt.
     */
    suspend fun useServer(noteId: String): Boolean = withContext(ioDispatcher) {
        val remote = fetchServerVersion(noteId) ?: return@withContext false

        storage.saveNote(remote.copy(syncStatus = SyncStatus.SYNCED))
        // Der gecachte ETag bleibt bewusst stehen: er ist jetzt veraltet, der nächste Sync lädt
        // die Notiz deshalb einmal nach und frischt ihn dabei auf. Ein GET zu viel ist billiger
        // als ein falsch gesetzter ETag, der eine echte Server-Änderung überspringen ließe.
        prefs.edit { remove("content_hash_$noteId") }
        ActivityLog.log(
            ActivityLog.Op.CONFLICT,
            ActivityLog.Src.REMOTE,
            id = noteId,
            title = remote.title,
            folder = remote.folderName,
            why = "resolved_use_server"
        )
        Logger.i(TAG, "✅ Conflict resolved (use server): $noteId")
        true
    }

    companion object {
        private const val TAG = "SyncConflictResolver"
    }
}
