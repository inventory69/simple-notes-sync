package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.models.holdsLocalEdit
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.sync.webdav.etagsMatch
import dev.dettmer.simplenotes.sync.webdav.isWebDavNotFound
import dev.dettmer.simplenotes.sync.webdav.listTreeOrNull
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.security.MessageDigest
import java.util.Collections
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 🆕 v2.0.0: Extrahiert Markdown-Export/-Import-Logik aus WebDavSyncService.
 * Verantwortlich für alle Operationen mit notes-md/ auf dem WebDAV-Server.
 */
// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class MarkdownSyncManager(
    private val prefs: SharedPreferences,
    private val storage: NotesStorage,
    private val eTagCache: ETagCache,
    private val urlBuilder: SyncUrlBuilder,
    private val connectionManager: ConnectionManager,
    private val timestampManager: SyncTimestampManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val folderStore: dev.dettmer.simplenotes.storage.FolderStore
) {
    companion object {
        private const val TAG = "MarkdownSyncManager"
        private const val MAX_FILENAME_LENGTH = 200
        private const val ETAG_PREVIEW_LENGTH = 8
        private const val CONTENT_PREVIEW_LENGTH = 50
        private const val SHORT_ID_LENGTH = 8

        /** 404 = Parent fehlt, 409 = Conflict (Parent-Collection existiert nicht). */
        private val MISSING_DIR_STATUS_CODES = setOf(404, 409)

        /** Bis v2.18 zwei Regexe; der aus resolveExportUrl übersah `id` in der ersten Zeile. */
        private val FRONTMATTER_ID = Regex("""\A---\s*\n(?:.*?\n)?id:\s*([a-f0-9-]+)""", RegexOption.DOT_MATCHES_ALL)
    }

    /**
     * 🆕 v2.14.0: URLs der in diesem Sync-Zyklus selbst exportierten MD-Dateien. [importAll]
     * überspringt sie, bevor es sie herunterlädt — bisher wurde die Datei erst geladen und dann
     * anhand der ID aus dem YAML-Header verworfen.
     *
     * Synchronized, weil [exportSingle] aus parallelen Upload-Coroutinen läuft.
     */
    private val exportedThisCycle = Collections.synchronizedSet(mutableSetOf<String>())

    /** Setzt [exportedThisCycle] zurück. Ohne diesen Reset wüchse das Set über Syncs hinweg. */
    fun beginSyncCycle() = exportedThisCycle.clear()

    // ─────────────────────────────────────────────────────────────
    // Export
    // ─────────────────────────────────────────────────────────────

    /**
     * Exportiert einzelne Note als Markdown.
     *
     * 🔧 v1.9.0 (Opt 1): markdownDirExists-Parameter eliminiert redundanten exists()-Call
     * 🔧 v1.9.0 (Opt 6): MD-Content-Hash-Cache für Skip bei unverändertem Inhalt
     */
    fun exportSingle(webdav: WebDavClient, serverUrl: String, note: Note, markdownDirExists: Boolean = true) {
        // 🆕 v2.7.0 (Folders): Basis-md-Ordner sicherstellen, danach ggf. das Subdir der Notiz.
        val mdRootUrl = urlBuilder.getMarkdownUrl(serverUrl)
        if (!markdownDirExists) {
            if (!webdav.exists(mdRootUrl)) {
                webdav.createDirectory(mdRootUrl)
                Logger.d(TAG, "📁 Created notes-md/ directory")
            }
        }
        val mdUrl = urlBuilder.getMarkdownFolderUrl(serverUrl, note.folderName)
        if (note.folderName != null) {
            try {
                webdav.createDirectory(mdUrl)
            } catch (e: Exception) {
                Logger.w(TAG, "createDirectory($mdUrl) failed (continuing): ${e.message}")
            }
        }

        val baseFilename = sanitizeFilename(note.title)
        // 🔧 v1.8.2 (IMPL_025): trimEnd('/') verhindert Double-Slash
        val noteUrlByTitle = "${mdUrl.trimEnd('/')}/$baseFilename.md"

        // 🆕 v1.9.0 (Opt 6): MD-Content-Hash berechnen und mit Cache vergleichen
        val mdContentStr = rewriteAssetLinksForMdMirror(note.toMarkdown(), note.folderName)
        val mdContentBytes = mdContentStr.toByteArray()
        val mdHash = MessageDigest.getInstance("SHA-256")
            .digest(mdContentBytes)
            .joinToString("") { "%02x".format(it) }
        val cachedMdHash = prefs.getString("content_hash_md_${note.id}", null)
        val cachedMdETag = eTagCache.getMdETag(note.id)

        if (mdHash == cachedMdHash && cachedMdETag != null) {
            Logger.d(TAG, "   ⏭️ MD skip: ${note.title} (content unchanged)")
            return
        }

        val noteUrl = resolveExportUrl(webdav, note, mdUrl, baseFilename, noteUrlByTitle)

        // Upload
        val putETag = putMarkdown(webdav, noteUrl, mdContentBytes)
        exportedThisCycle.add(noteUrl)
        replacePreviousCopy(webdav, serverUrl, note.id, noteUrl)

        // 🆕 v1.9.0 (Opt 6): MD-Hash und E-Tag nach erfolgreichem Upload cachen
        // 🆕 v2.14.0: ETag aus der PUT-Antwort bevorzugen — PROPFIND nur als Fallback.
        try {
            val mdETag = putETag ?: webdav.list(noteUrl, 0).firstOrNull()?.etag
            prefs.edit {
                putString("content_hash_md_${note.id}", mdHash)
                if (mdETag != null) {
                    putString("etag_md_${note.id}", mdETag)
                }
            }
            Logger.d(TAG, "   ⚡ MD E-Tag cached: ${mdETag?.takeLast(ETAG_PREVIEW_LENGTH)}")
        } catch (e: Exception) {
            // Non-fatal: Hash trotzdem cachen für nächsten Content-Vergleich
            prefs.edit { putString("content_hash_md_${note.id}", mdHash) }
            Logger.w(TAG, "   ⚠️ MD E-Tag fetch failed: ${e.message}")
        }
    }

    /**
     * Liegt unter dem Titel-Dateinamen bereits die MD-Datei einer ANDEREN Notiz, weicht der
     * Export auf `<titel>_<kurz-id>.md` aus. Fehler beim Check sind non-fatal — dann bleibt es
     * beim Titel-Dateinamen.
     */
    private fun resolveExportUrl(
        webdav: WebDavClient,
        note: Note,
        mdUrl: String,
        baseFilename: String,
        noteUrlByTitle: String
    ): String {
        val existingId = try {
            if (!webdav.exists(noteUrlByTitle)) return noteUrlByTitle
            frontmatterId(webdav.get(noteUrlByTitle).use { it.bufferedReader().readText() })
        } catch (e: Exception) {
            Logger.w(TAG, "⚠️ Could not check existing file: ${e.message}")
            return noteUrlByTitle
        }

        if (existingId == null || existingId == note.id) return noteUrlByTitle

        val filename = "${baseFilename}_${note.id.take(SHORT_ID_LENGTH)}.md"
        Logger.d(TAG, "📝 Duplicate title, using: $filename")
        return "${mdUrl.trimEnd('/')}/$filename"
    }

    /**
     * PUT der MD-Datei.
     *
     * 🆕 v2.14.0 Self-Heal: 404/409 heißt, das persistierte "notes-md/ existiert"-Flag ist stale.
     *
     * @return den ETag der PUT-Antwort, oder `null` wenn der Server keinen schickt.
     */
    private fun putMarkdown(webdav: WebDavClient, noteUrl: String, bytes: ByteArray): String? = try {
        webdav.put(noteUrl, bytes, "text/markdown")
    } catch (e: WebDavException) {
        if (e.statusCode in MISSING_DIR_STATUS_CODES) {
            connectionManager.markdownDirEnsured = false
            Logger.d(TAG, "🔄 markdownDirEnsured cleared (PUT → ${e.statusCode})")
        }
        throw e
    }

    /**
     * 🆕 v2.19.0: Die Kopie folgt der Notiz. Lag sie vorher unter einem anderen Pfad (Umbenennen,
     * Ordnerwechsel, Import einer Editor-Datei), wird die alte Datei gelöscht und der neue Pfad
     * gemerkt. Fehler beim Löschen brechen den Export nicht ab.
     *
     * Gelöscht wird nur, wenn die alte Datei noch die ID der Notiz trägt (+1 GET, nur bei
     * Pfadwechsel). Ein gemerkter Pfad kann aus [healStaleMirrors] stammen, und dort gehört die
     * Titeldatei womöglich einer Editor-Notiz, die noch nicht importiert ist.
     */
    private fun replacePreviousCopy(webdav: WebDavClient, serverUrl: String, noteId: String, noteUrl: String) {
        val previous = rememberedMdPath(noteId, serverUrl)
        if (previous != null && previous != noteUrl && readFrontmatterId(webdav, previous) == noteId) {
            try {
                if (deleteIfPresent(webdav, previous)) Logger.d(TAG, "🗑️ Removed previous MD copy: $previous")
            } catch (e: Exception) {
                // ponytail: die alte Kopie ist danach vergessen; healStaleMirrors räumt sie beim
                // nächsten Config-Wechsel, eine Retry-Liste lohnt sich erst bei Häufung.
                Logger.w(TAG, "⚠️ Could not remove previous MD copy $previous: ${e.message}")
            }
        }
        eTagCache.setMdPath(noteId, noteUrl)
    }

    /** Gemerkter Pfad der MD-Kopie, nur wenn er unter der MD-Wurzel des aktuellen Servers liegt. */
    private fun rememberedMdPath(noteId: String, serverUrl: String): String? =
        eTagCache.getMdPath(noteId)?.takeIf { it.startsWith(urlBuilder.getMarkdownUrl(serverUrl).trimEnd('/') + "/") }

    /** DELETE, 404-tolerant. @return `false`, wenn die Datei schon fehlte. */
    private fun deleteIfPresent(webdav: WebDavClient, url: String): Boolean = try {
        webdav.delete(url)
        true
    } catch (e: java.io.IOException) {
        if (!e.isWebDavNotFound()) throw e
        false
    }

    /**
     * 🆕 v2.9.0 (Trash): Löscht den Server-Markdown-Spiegel einer Notiz, die soeben in den
     * Papierkorb verschoben wurde. Spiegelt den MD-Delete-Block aus
     * [NoteDownloader.deleteFromServer], 404-tolerant.
     *
     * 🆕 v2.19.0: Der gemerkte Pfad hat Vorrang vor dem Titelpfad. Der Titelpfad kann einer anderen
     * Notiz gehören (`<titel>_<kurz-id>.md`) oder nach einer Umbenennung ins Leere zeigen.
     *
     * Invalidiert anschließend MD-Content-Hash + E-Tag, damit ein späteres Restore die Datei neu
     * exportiert (sonst würde der Skip-per-Hash den Re-Export verschlucken).
     */
    fun deleteSingle(webdav: WebDavClient, serverUrl: String, note: Note) {
        val mdUrl = rememberedMdPath(note.id, serverUrl)
            ?: (urlBuilder.getMarkdownFolderUrl(serverUrl, note.folderName).trimEnd('/') + "/" + sanitizeFilename(note.title) + ".md")
        if (deleteIfPresent(webdav, mdUrl)) {
            Logger.d(TAG, "🗑️ Deleted server MD (trashed): $mdUrl")
        } else {
            Logger.d(TAG, "ℹ️ Server MD not found (already gone): $mdUrl")
        }
        prefs.edit {
            remove("content_hash_md_${note.id}")
            remove("etag_md_${note.id}")
        }
        eTagCache.clearMdPath(note.id)
    }

    /**
     * Exportiert ALLE lokalen Notizen als Markdown (Initial-Export).
     *
     * Wird beim ersten Aktivieren der Desktop-Integration aufgerufen.
     * Exportiert auch bereits synchronisierte Notizen.
     *
     * @return Anzahl exportierter Notizen
     */
    suspend fun exportAll(
        serverUrl: String,
        username: String,
        password: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(ioDispatcher) {
        Logger.d(TAG, "🔄 Starting initial Markdown export for all notes...")

        val webdav = WebDavClient(
            ConnectionManager.buildHttpClient(connectionManager.getTimeoutMs(), username, password)
        )

        try {
            val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

            // Ordner sollte bereits existieren, aber Sicherheitscheck
            ensureMarkdownDirExists(webdav, serverUrl)

            // Hole ALLE lokalen Notizen (inklusive SYNCED)
            // 🆕 v2.9.0 (Trash): getrashte Notizen nicht exportieren — ihr MD-Spiegel wird beim
            // Trashen gelöscht (siehe deleteSingle), ein Re-Export würde ihn wiederbeleben.
            val allNotes = storage.loadAllNotes().filter { it.trashedAt == null }
            val totalCount = allNotes.size
            var exportedCount = 0

            Logger.d(TAG, "📝 Found $totalCount notes to export")

            // 🆕 v2.7.0 (Folders): Dedup pro Ordner; Subdir idempotent anlegen.
            val usedFilenamesByFolder = mutableMapOf<String?, MutableSet<String>>()
            val ensuredFolders = mutableSetOf<String>()

            allNotes.forEachIndexed { index, note ->
                try {
                    // Progress-Callback
                    onProgress(index + 1, totalCount)

                    val folderUrl = urlBuilder.getMarkdownFolderUrl(serverUrl, note.folderName)
                    if (note.folderName != null && ensuredFolders.add(note.folderName)) {
                        try {
                            webdav.createDirectory(folderUrl)
                        } catch (e: Exception) {
                            Logger.w(TAG, "createDirectory($folderUrl) failed: ${e.message}")
                        }
                    }
                    val used = usedFilenamesByFolder.getOrPut(note.folderName) { mutableSetOf() }
                    val filename = getUniqueFilename(note, used) + ".md"
                    // 🔧 v1.8.2 (IMPL_025): trimEnd('/') verhindert Double-Slash
                    val noteUrl = "${folderUrl.trimEnd('/')}/$filename"

                    // Konvertiere zu Markdown
                    val mdContent = rewriteAssetLinksForMdMirror(note.toMarkdown(), note.folderName).toByteArray()

                    // Upload (überschreibt falls vorhanden)
                    // 🆕 v2.19.0: PUT-ETag merken — sonst erkennt importAll den eigenen Spiegel nicht.
                    webdav.put(noteUrl, mdContent, "text/markdown")?.let { etag ->
                        prefs.edit { putString("etag_md_${note.id}", etag) }
                    }
                    replacePreviousCopy(webdav, serverUrl, note.id, noteUrl)

                    exportedCount++
                    Logger.d(TAG, "   ✅ Exported [${index + 1}/$totalCount]: ${note.title} -> $filename")
                } catch (e: Exception) {
                    Logger.e(TAG, "❌ Failed to export ${note.title}: ${e.message}")
                    // Continue mit nächster Note (keine Abbruch bei Einzelfehlern)
                }
            }

            Logger.d(TAG, "✅ Initial export completed: $exportedCount/$totalCount notes")

            // ⚡ v1.3.1: Set lastSyncTimestamp to enable timestamp-based skip on next sync
            if (exportedCount > 0) {
                val timestamp = System.currentTimeMillis()
                prefs.edit { putLong("last_sync_timestamp", timestamp) }
                Logger.d(TAG, "💾 Set lastSyncTimestamp after initial export (enables fast next sync)")
            }

            return@withContext exportedCount
        } finally {
            // 🐛 FIX: Connection Leak — WebDavClient explizit schließen
            webdav.close()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Sync (Manual)
    // ─────────────────────────────────────────────────────────────

    /**
     * Manueller Markdown-Sync: Import aller Server-Markdown-Dateien.
     * Erstellt seinen eigenen WebDavClient-Client für eigenständige Aufrufe.
     */
    suspend fun syncAll(serverUrl: String, username: String, password: String): Int = withContext(ioDispatcher) {
        return@withContext try {
            Logger.d(TAG, "📝 Starting Markdown sync...")

            val webdav = WebDavClient(
                ConnectionManager.buildHttpClient(connectionManager.getTimeoutMs(), username, password)
            )

            try {
                val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

                // Check if notes-md/ exists
                if (!webdav.exists(mdUrl)) {
                    Logger.d(TAG, "⚠️ notes-md/ directory not found - skipping MD import")
                    return@withContext 0
                }

                val localNotes = storage.loadAllNotes()
                val mdResources = webdav.list(mdUrl).filter { it.name.endsWith(".md") }
                var importedCount = 0

                Logger.d(TAG, "📂 Found ${mdResources.size} markdown files")

                for (resource in mdResources) {
                    try {
                        // Download MD-File
                        val mdContent = webdav.get(resource.href.toString())
                            .use { it.bufferedReader().readText() }

                        // Parse zu Note
                        val mdNote = Note.fromMarkdown(mdContent) ?: continue

                        val localNote = localNotes.find { it.id == mdNote.id }

                        // Konfliktauflösung: Last-Write-Wins
                        when {
                            localNote == null -> {
                                // Neue Notiz vom Desktop
                                storage.saveNote(mdNote)
                                importedCount++
                                Logger.d(TAG, "   ✅ Imported new: ${mdNote.title}")
                            }
                            mdNote.updatedAt > localNote.updatedAt -> {
                                // Desktop-Version ist neuer (Last-Write-Wins)
                                storage.saveNote(mdNote)
                                importedCount++
                                Logger.d(TAG, "   ✅ Updated from MD: ${mdNote.title}")
                            }
                            // Sonst: Lokale Version behalten
                            else -> {
                                Logger.d(TAG, "   ⏭️ Local newer, skipping: ${mdNote.title}")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to import ${resource.path}", e)
                        // Continue with other files
                    }
                }

                Logger.d(TAG, "✅ Markdown sync completed: $importedCount imported")
                importedCount
            } finally {
                // 🐛 FIX: Connection Leak — WebDavClient explizit schließen
                webdav.close()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Markdown sync failed", e)
            0
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Auto-Import
    // ─────────────────────────────────────────────────────────────

    /**
     * Auto-import Markdown files during regular sync.
     * Called automatically if KEY_MARKDOWN_AUTO_IMPORT is enabled.
     *
     * 🆕 v1.11.0: excludeNoteIds verhindert Re-Import von soeben exportierten Dateien.
     */
    // Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
    @Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements", "CyclomaticComplexMethod", "LongMethod")
    suspend fun importAll(webdav: WebDavClient, serverUrl: String, excludeNoteIds: Set<String> = emptySet()): Int {
        return try {
            Logger.d(TAG, "📝 Importing Markdown files...")

            val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

            // 🆕 v2.14.0: Ist das Verzeichnis für die aktuelle Server-Config schon verifiziert,
            // spart der Short-Circuit den HEAD. Ein zwischenzeitlich gelöschtes notes-md/ fällt
            // beim folgenden list() auf und heilt das Flag dort.
            if (!connectionManager.markdownDirEnsured && !webdav.exists(mdUrl)) {
                Logger.d(TAG, "   ⚠️ notes-md/ directory not found - skipping")
                return 0
            }

            // 🆕 v2.14.0: Einmal pro Server-Config statt bei jedem Import — spart ein PROPFIND
            // auf das komplette WebDAV-Root. Siehe [ConnectionManager.staleRootCleaned].
            if (!connectionManager.staleRootCleaned) {
                cleanupStaleRoot(webdav, serverUrl)
                connectionManager.staleRootCleaned = true
            }

            val mdItems = collectMdItems(webdav, serverUrl)
            val mdResources = mdItems.map { it.resource } // für bestehende Logs/Counts

            var importedCount = 0
            var skippedCount = 0

            Logger.d(TAG, "   📂 Found ${mdResources.size} markdown files")

            // ⚡ v1.3.1: Performance-Optimierung - Letzten Sync-Zeitpunkt holen
            val lastSyncTime = timestampManager.getLast()
            Logger.d(TAG, "   📅 Last sync: ${Date(lastSyncTime)}")

            // 🔧 v1.11.0: Fast-Path — wenn alle Dateien älter als letzter Sync sind,
            // überspringe die gesamte Schleife und zeige keine IMPORTING_MARKDOWN-Phase.
            if (lastSyncTime > 0) {
                val allUnchanged = mdResources.all { resource ->
                    val serverModifiedTime = resource.modified?.time ?: 0L
                    serverModifiedTime <= lastSyncTime
                }
                if (allUnchanged) {
                    Logger.d(TAG, "   ⏭️ All ${mdResources.size} markdown files unchanged since last sync (fast-path)")
                    Logger.d(TAG, "   📊 Markdown import complete: 0 imported, ${mdResources.size} skipped (fast-path)")
                    return 0
                }
            }

            var processedCount = 0
            var importPhaseStarted = false
            for (mdItem in mdItems) {
                val resource = mdItem.resource
                processedCount++
                try {
                    val serverModifiedTime = resource.modified?.time ?: 0L

                    // ⚡ v1.3.1: PERFORMANCE - Skip wenn Datei seit letztem Sync nicht geändert wurde
                    if (lastSyncTime > 0 && serverModifiedTime <= lastSyncTime) {
                        skippedCount++
                        Logger.d(TAG, "   ⏭️ Skipping ${resource.path}: not modified since last sync")
                        continue
                    }

                    // 🆕 v2.14.0: In diesem Zyklus selbst exportiert → gar nicht erst laden.
                    // Die ID-Prüfung gegen excludeNoteIds weiter unten bleibt als zweite
                    // Verteidigungslinie (greift, wenn eine Titeländerung den Pfad verschoben hat).
                    if (mdItem.fileUrl in exportedThisCycle) {
                        skippedCount++
                        Logger.d(TAG, "   ⏭️ Skipping ${resource.path}: just exported in this sync cycle")
                        continue
                    }

                    Logger.d(TAG, "   🔍 Processing: ${resource.path}, modified=${resource.modified}")

                    // Build full URL
                    val mdFileUrl = mdItem.fileUrl

                    // Download MD content
                    val mdContent = webdav.get(mdFileUrl).use { it.bufferedReader().readText() }
                    Logger.d(TAG, "      Downloaded ${mdContent.length} chars")

                    // 🔧 v1.7.2 (IMPL_014): Server mtime übergeben für korrekte Timestamp-Sync
                    val mdNote = Note.fromMarkdown(mdContent, serverModifiedTime)
                    if (mdNote == null) {
                        Logger.w(TAG, "      ⚠️ Failed to parse ${resource.path} - fromMarkdown returned null")
                        continue
                    }
                    // 🆕 v2.7.0 (Folders): Verzeichnis ist autoritativ für folderName.
                    val mdNoteFoldered = mdNote.copy(folderName = mdItem.folder)

                    // FIX-05 (v2.2.0): Checklist-Import-Logging + Korruptions-Warnung
                    if (mdNote.noteType == NoteType.CHECKLIST) {
                        Logger.d(TAG, "      📋 Checklist import: title='${mdNote.title}', items=${mdNote.checklistItems?.size ?: 0}")
                        if (mdNote.title.contains("[ ]") || mdNote.title.contains("[x]") || mdNote.title.contains("[X]")) {
                            @Suppress("MagicNumber")
                            val previewLength = 200
                            Logger.e(TAG, "🚨 CORRUPTION WARNING: Checklist pattern in title after parse: '${mdNote.title}'")
                            Logger.e(TAG, "🚨 Source: ${resource.path}, first $previewLength chars: ${mdContent.take(previewLength)}")
                        }
                    }

                    // 🆕 v1.11.0: Skip Markdown files whose note ID was just exported in this sync cycle.
                    if (mdNote.id in excludeNoteIds) {
                        skippedCount++
                        Logger.d(
                            TAG,
                            "   ⏭️ Skipping ${resource.path}: excluded this cycle (just exported or JSON taken from server, " +
                                "ID=${mdNote.id})"
                        )
                        continue
                    }

                    // 🆕 v2.19.0: Unser eigener, seit dem Export unveränderter Spiegel trägt keine
                    // externe Änderung. Der mtime-Filter oben vergleicht Server- mit Geräte-Uhr und
                    // lässt ihn bei Uhrenversatz durch; ohne diese Prüfung überschrieb er eine
                    // neuere Server-JSON (Force-Zweig + Re-Upload).
                    if (etagsMatch(resource.etag, eTagCache.getMdETag(mdNote.id))) {
                        skippedCount++
                        Logger.d(TAG, "   ⏭️ Skipping ${resource.path}: own mirror, unchanged since export (E-Tag match)")
                        continue
                    }

                    // v1.4.0 FIX: Validierung - leere TEXT-Notizen nicht importieren wenn lokal Content existiert
                    val localNote = storage.loadNote(mdNote.id)
                    if (mdNote.noteType == NoteType.TEXT &&
                        mdNote.content.isBlank() &&
                        localNote != null &&
                        localNote.content.isNotBlank()
                    ) {
                        Logger.w(
                            TAG,
                            "      ⚠️ Skipping ${resource.path}: " +
                                "MD content empty but local has content - likely parse error!"
                        )
                        continue
                    }

                    Logger.d(
                        TAG,
                        "      Parsed: id=${mdNote.id}, title=${mdNote.title}, " +
                            "updatedAt=${Date(mdNote.updatedAt)}, " +
                            "content=${mdNote.content.take(CONTENT_PREVIEW_LENGTH)}..."
                    )

                    Logger.d(
                        TAG,
                        "      Local note: " + if (localNote == null) {
                            "NOT FOUND"
                        } else {
                            "exists, updatedAt=${Date(localNote.updatedAt)}, " +
                                "syncStatus=${localNote.syncStatus}"
                        }
                    )

                    Logger.d(
                        TAG,
                        "      Comparison: mdUpdatedAt=${mdNote.updatedAt}, " +
                            "localUpdated=${localNote?.updatedAt ?: 0L}"
                    )

                    // 🔧 v1.8.2 (IMPL_025): Semantischer Content-Vergleich
                    val mdItems = mdNote.checklistItems.orEmpty()
                    val localItems = localNote?.checklistItems.orEmpty()
                    val checklistContentEqual = when {
                        mdItems.size != localItems.size -> false
                        mdItems.isEmpty() && localItems.isEmpty() -> true
                        else -> mdItems.zip(localItems).all { (md, local) ->
                            md.text == local.text && md.isChecked == local.isChecked && md.order == local.order
                        }
                    }

                    // 🔧 v1.8.2 (IMPL_025 Edit 25.8): Für Checklisten NUR checklistItems vergleichen!
                    val contentChanged = localNote != null &&
                        when (mdNote.noteType) {
                            NoteType.CHECKLIST -> {
                                mdNote.title != localNote.title || !checklistContentEqual
                            }
                            else -> {
                                mdNote.content.trim() != localNote.content.trim() ||
                                    mdNote.title != localNote.title
                            }
                        }

                    if (contentChanged) {
                        Logger.d(TAG, "      📝 Content differs from local!")
                    }

                    importPhaseStarted = true
                    SyncStateManager.updateProgress(
                        phase = SyncPhase.IMPORTING_MARKDOWN,
                        current = processedCount,
                        total = mdResources.size,
                        currentFileName = resource.name
                    )

                    when {
                        localNote == null -> {
                            storage.saveNote(mdNoteFoldered.copy(syncStatus = SyncStatus.SYNCED))
                            eTagCache.setMdPath(mdNote.id, mdItem.fileUrl)
                            importedCount++
                            Logger.d(TAG, "   ✅ Imported new from Markdown: ${mdNote.title}")
                            ActivityLog.log(
                                ActivityLog.Op.CREATE,
                                ActivityLog.Src.REMOTE,
                                id = mdNoteFoldered.id,
                                title = mdNote.title,
                                folder = mdNoteFoldered.folderName,
                                why = "markdown_import"
                            )
                        }
                        localNote.syncStatus == SyncStatus.SYNCED &&
                            !contentChanged &&
                            localNote.updatedAt >= mdNote.updatedAt -> {
                            skippedCount++
                            Logger.d(
                                TAG,
                                "   ⏭️ Skipped ${mdNote.title}: content identical " +
                                    "(local=${localNote.updatedAt}, md=${mdNote.updatedAt})"
                            )
                        }
                        // 🆕 v2.9.0 (Trash): trashedAt == null guarden — sonst würde eine veraltete
                        // MD-Datei eine getrashte Notiz über diesen Force-Import-Pfad wiederbeleben.
                        // Ein echt neuerer MD-Import (nächster Branch, LWW) darf bewusst un-trashen.
                        // 🆕 v2.19.0: Ohne Sync-Basis (lastSync == 0 nach Restore/Cache-Räumung, dann
                        // fehlt auch der MD-E-Tag) ist eine externe Änderung nicht von einem veralteten
                        // Spiegel zu unterscheiden → nicht erzwingen, der LWW-Zweig entscheidet.
                        contentChanged &&
                            lastSyncTime > 0 &&
                            localNote.syncStatus == SyncStatus.SYNCED &&
                            localNote.trashedAt == null -> {
                            // 🔧 Force-import of changed MD content fires regardless of timestamp.
                            // When the MD timestamp is tied with (or older than) local, the JSON hash
                            // stays unchanged → the uploader skips the re-upload → endless import loop.
                            // Bump updatedAt so the re-upload actually happens and the note converges.
                            // (A genuinely newer MD keeps its own timestamp.)
                            val mergedUpdatedAt = if (mdNote.updatedAt > localNote.updatedAt) {
                                mdNote.updatedAt
                            } else {
                                System.currentTimeMillis()
                            }
                            val merged = mdNoteFoldered.copy(
                                syncStatus = SyncStatus.PENDING,
                                updatedAt = mergedUpdatedAt,
                                isPinned = mdNote.isPinned ?: localNote.isPinned,
                                color = mdNote.color ?: localNote.color,
                                archivedAt = mdNote.archivedAt ?: localNote.archivedAt // 🆕 v2.11.0 (Archive)
                            )
                            storage.saveNote(merged)
                            eTagCache.setMdPath(mdNote.id, mdItem.fileUrl)
                            importedCount++
                            Logger.d(
                                TAG,
                                "   ✅ Imported changed content (marked PENDING for JSON sync): ${mdNote.title}"
                            )
                        }
                        mdNote.updatedAt > localNote.updatedAt -> {
                            Logger.d(TAG, "      Decision: Markdown has newer timestamp!")
                            if (localNote.syncStatus.holdsLocalEdit) {
                                storage.saveNote(localNote.copy(syncStatus = SyncStatus.CONFLICT))
                                Logger.w(TAG, "   ⚠️ Conflict: Markdown vs local pending: ${mdNote.id}")
                            } else {
                                storage.saveNote(mdNoteFoldered.copy(syncStatus = SyncStatus.SYNCED))
                                eTagCache.setMdPath(mdNote.id, mdItem.fileUrl)
                                importedCount++
                                Logger.d(TAG, "   ✅ Updated from Markdown (newer timestamp): ${mdNote.title}")
                            }
                        }
                        else -> {
                            skippedCount++
                            Logger.d(
                                TAG,
                                "   ⏭️ Skipped ${mdNote.title}: local is newer or pending " +
                                    "(local=${localNote.updatedAt}, md=${mdNote.updatedAt})"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "   ⚠️ Failed to import ${resource.path}", e)
                    // Continue with other files
                }
            }

            Logger.d(TAG, "   📊 Markdown import complete: $importedCount imported, $skippedCount skipped (unchanged)")
            importedCount
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Markdown import failed", e)
            0
        }
    }

    private data class MdItem(
        val resource: WebDavResource,
        val fileUrl: String,
        val folder: String?
    )

    /**
     * 🆕 v2.7.0 (Folders): Root-md + alle Subdirs einsammeln, Local-only-Ordner ausgenommen.
     * 🆕 v2.14.0: Deep-PROPFIND statt 1+N (siehe [listTreeOrNull]); `null` → klassisch pro Ordner.
     * Ein Fehler beim Root-Listing wirft, ein Fehler in einem Unterordner überspringt nur ihn.
     */
    // Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand aus importAll, v2.19.0 nur herausgelöst)
    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    private fun collectMdItems(webdav: WebDavClient, serverUrl: String): List<MdItem> {
        val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)
        val mdItems = mutableListOf<MdItem>()
        val deepTree = if (connectionManager.deepPropfindRefused) {
            null
        } else {
            webdav.listTreeOrNull(mdUrl) { connectionManager.deepPropfindRefused = true }
        }
        val rootList = deepTree?.get(null) ?: webdav.list(mdUrl)
        rootList.filter { !it.isDirectory && it.name.endsWith(".md") }.forEach {
            mdItems.add(MdItem(it, mdUrl.trimEnd('/') + "/" + it.name, null))
        }
        val mdSubDirs = rootList.filter { res ->
            res.isDirectory &&
                res.name.isNotBlank() &&
                res.name != "/" &&
                !mdUrl.trimEnd('/').endsWith("/" + res.name)
        }
        val localOnlyFolders = folderStore.getLocalOnlyFolderNames().map { it.lowercase() }.toSet()
        for (dir in mdSubDirs) {
            val folder = dev.dettmer.simplenotes.utils.FolderNameValidator.sanitize(dir.name) ?: continue
            if (folder.lowercase() in localOnlyFolders) {
                Logger.d(TAG, "   ⏭️ Skipping local-only folder (MD): $folder")
                continue
            }
            val folderUrl = urlBuilder.getMarkdownFolderUrl(serverUrl, folder)
            val sub = deepTree?.get(dir.name) ?: try {
                webdav.list(folderUrl)
            } catch (e: Exception) {
                Logger.w(TAG, "   ⚠️ list($folderUrl) failed: ${e.message}")
                continue
            }
            sub.filter { !it.isDirectory && it.name.endsWith(".md") }.forEach {
                mdItems.add(MdItem(it, folderUrl.trimEnd('/') + "/" + it.name, folder))
            }
        }
        return mdItems
    }

    // ─────────────────────────────────────────────────────────────
    // Einmaliger Aufräumlauf (v2.19.0)
    // ─────────────────────────────────────────────────────────────

    /**
     * 🆕 v2.19.0: Räumt MD-Kopien weg, die ältere Versionen beim Umbenennen oder Löschen haben
     * liegen lassen. Läuft einmal pro Server-Config ([ConnectionManager.mdMirrorsHealed]),
     * danach 0 Requests.
     *
     * Gelöscht wird nur eindeutig:
     * - ein älterer Zwilling einer lokalen Notiz, deren erwartete Datei nachweislich ihr gehört
     *   und nicht älter ist (`modified`). Ein Gerät mit alter App kann nach einer Umbenennung,
     *   die hier noch nicht angekommen ist, die neuere Datei schreiben, die bleibt deshalb stehen.
     * - die Kopie einer lokal unbekannten Notiz, die im Deletion-Tracker steht.
     *
     * Alles andere bleibt. Liegt die einzige Kopie einer Notiz unter einem alten Namen, wird sie
     * als Pfad gemerkt, der nächste Export zieht sie dann über [replacePreviousCopy] um.
     */
    // ponytail: ein GET je nicht erwarteter Datei, einmal pro Config. Bei sehr vielen Editor-Dateien
    // teuer, dann nur Dateien mit `modified` vor dem Upgrade prüfen.
    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
    suspend fun healStaleMirrors(webdav: WebDavClient, serverUrl: String) {
        if (connectionManager.mdMirrorsHealed) return
        val items = try {
            collectMdItems(webdav, serverUrl)
        } catch (e: Exception) {
            Logger.w(TAG, "⚠️ MD mirror heal: listing failed, retrying next sync: ${e.message}")
            return
        }
        val byUrl = items.associateBy { it.fileUrl }
        val allNotes = storage.loadAllNotes()
        val knownIds = allNotes.mapTo(mutableSetOf()) { it.id }
        val active = allNotes.filter { it.trashedAt == null }
        val activeIds = active.mapTo(mutableSetOf()) { it.id }

        // Erwartete Datei je Notiz: gemerkter Pfad, sonst `<titel>_<kurz-id>.md` (eindeutig), sonst
        // Titelpfad. Beanspruchen zwei Notizen dieselbe Datei, gilt sie für keine als erwartet.
        val claimed = active.mapNotNull { note ->
            val folderUrl = urlBuilder.getMarkdownFolderUrl(serverUrl, note.folderName).trimEnd('/')
            val title = sanitizeFilename(note.title)
            listOfNotNull(
                rememberedMdPath(note.id, serverUrl),
                "$folderUrl/${title}_${note.id.take(SHORT_ID_LENGTH)}.md",
                "$folderUrl/$title.md"
            ).firstOrNull { it in byUrl }?.let { note.id to it }
        }
        val claimCount = claimed.groupingBy { it.second }.eachCount()
        val expected = claimed.filter { claimCount[it.second] == 1 }.toMap()
        expected.forEach { (id, url) ->
            if (eTagCache.getMdPath(id) != url) eTagCache.setMdPath(id, url)
        }

        val expectedUrls = expected.values.toSet()
        val candidatesById = items.filter { it.fileUrl !in expectedUrls }
            .mapNotNull { item -> readFrontmatterId(webdav, item.fileUrl)?.let { it to item } }
            .groupBy({ it.first }, { it.second })

        val tracker = storage.loadDeletionTracker()
        var removed = 0
        for ((id, candidates) in candidatesById) {
            val expectedItem = expected[id]?.let { byUrl[it] }
            val stale = when {
                expectedItem != null -> {
                    val expectedModified = expectedItem.resource.modified?.time
                    val older = candidates.filter { c ->
                        val m = c.resource.modified?.time
                        expectedModified != null && m != null && m <= expectedModified
                    }
                    // Erst jetzt die erwartete Datei prüfen: nur sie belegt, dass die Notiz woanders liegt.
                    if (older.isNotEmpty() && readFrontmatterId(webdav, expectedItem.fileUrl) == id) older else emptyList()
                }
                id !in knownIds && tracker.isDeleted(id) -> candidates
                else -> {
                    if (id in activeIds && candidates.size == 1) {
                        eTagCache.setMdPath(id, candidates.single().fileUrl)
                        Logger.d(TAG, "   📌 Only MD copy under an old name, remembered: ${candidates.single().fileUrl}")
                    }
                    emptyList()
                }
            }
            for (item in stale) {
                try {
                    if (deleteIfPresent(webdav, item.fileUrl)) {
                        removed++
                        Logger.d(TAG, "   🗑️ Removed stale MD copy: ${item.fileUrl}")
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "   ⚠️ Could not remove stale MD copy ${item.fileUrl}: ${e.message}")
                }
            }
        }
        connectionManager.mdMirrorsHealed = true
        Logger.i(TAG, "🧹 MD mirror heal: ${items.size} files, ${candidatesById.values.sumOf { it.size }} checked, $removed removed")
    }

    /** `id` aus dem Frontmatter der Datei, `null` bei fehlender ID oder Fehler. */
    private fun readFrontmatterId(webdav: WebDavClient, url: String): String? = try {
        frontmatterId(webdav.get(url).use { it.bufferedReader().readText() })
    } catch (e: Exception) {
        Logger.w(TAG, "   ⚠️ Could not read $url: ${e.message}")
        null
    }

    // ─────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────

    /**
     * 🆕 Bild-Attachments: Der MD-Mirror liegt in `<syncFolder>-md/[<folder>/]<note>.md`, der
     * Asset-Ordner als Geschwister von `<syncFolder>/` — also `../<syncFolder>-assets/<name>`
     * von einer Root-Notiz aus, `../../<syncFolder>-assets/<name>` aus einem Notiz-Ordner heraus.
     * Reines String-Replace: Content-Referenzen bleiben im JSON `.assets/<name>`, nur der
     * exportierte MD-Spiegel bekommt den relativen Pfad, den externe Viewer auflösen können.
     */
    private fun rewriteAssetLinksForMdMirror(content: String, folderName: String?): String {
        val syncFolderName = prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
            ?: Constants.DEFAULT_SYNC_FOLDER_NAME
        val depthPrefix = if (folderName != null) "../../" else "../"
        return content.replace("](.assets/", "]($depthPrefix$syncFolderName${SyncUrlBuilder.ASSETS_SUFFIX}/")
    }

    /** `id:` aus dem YAML-Frontmatter; `id` darf die erste Zeile sein. */
    internal fun frontmatterId(content: String): String? =
        FRONTMATTER_ID.find(content)?.groupValues?.get(1)

    /**
     * Sanitize Filename für sichere Dateinamen.
     * Entfernt Windows/Linux-verbotene Zeichen, begrenzt Länge.
     */
    fun sanitizeFilename(title: String): String {
        return title
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .replace(Regex("\\s+"), " ")
            .take(MAX_FILENAME_LENGTH)
            .trim('_', ' ')
    }

    /**
     * Generiert eindeutigen Markdown-Dateinamen für eine Notiz.
     * Bei Duplikaten wird die Note-ID als Suffix angehängt.
     */
    fun getUniqueFilename(note: Note, usedFilenames: MutableSet<String>): String {
        val baseFilename = sanitizeFilename(note.title)

        return if (usedFilenames.contains(baseFilename)) {
            val shortId = note.id.take(8)
            val uniqueFilename = "${baseFilename}_$shortId"
            usedFilenames.add(uniqueFilename)
            uniqueFilename
        } else {
            usedFilenames.add(baseFilename)
            baseFilename
        }
    }

    /**
     * Finds all Markdown files in [mdUrl] whose YAML frontmatter carries [noteId].
     * Used when local note is deleted and title is unavailable.
     *
     * 🆕 v2.19.0: alle Treffer statt nur des ersten, ältere Versionen hinterließen Zwillinge.
     */
    suspend fun findAllByNoteId(webdav: WebDavClient, mdUrl: String, noteId: String): List<String> = withContext(ioDispatcher) {
        return@withContext try {
            Logger.d(TAG, "🔍 Scanning MD files for ID: $noteId")
            webdav.list(mdUrl)
                .filter { !it.isDirectory && it.name.endsWith(".md") }
                .filter { readFrontmatterId(webdav, mdUrl.trimEnd('/') + "/" + it.name) == noteId }
                .map { it.name }
                .also { Logger.d(TAG, "   Found ${it.size} MD file(s) for ID: $noteId") }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to scan MD files: ${e.message}")
            emptyList()
        }
    }

    /**
     * 🔧 v1.8.2: One-time cleanup of stale "/" directory at WebDAV root.
     */
    fun cleanupStaleRoot(webdav: WebDavClient, serverUrl: String) {
        try {
            val rootUrl = serverUrl.trimEnd('/')
            Logger.d(TAG, "   🔍 Scanning root for stale '/' directory")
            val rootResources = webdav.list(rootUrl)
            // 🔒 v2.14.0: Nur zählen, nicht auflisten. Das Elternverzeichnis der Sync-Ordner
            // gehört dem Nutzer und enthält Fremddateien, die der LogAnonymizer prinzipiell
            // nicht kennt — sie gingen beim Log-Export ungefiltert raus (Datenminimierung).
            Logger.d(
                TAG,
                "   🔍 Found ${rootResources.size} resources at root " +
                    "(${rootResources.count { it.isDirectory }} directories)"
            )
            val staleSlashDir = rootResources.find { res -> res.isDirectory && res.name == "/" }
            if (staleSlashDir != null) {
                Logger.w(
                    TAG,
                    "   🗑️ Found stale '/' directory at root (double-slash bug artifact): " +
                        staleSlashDir.href
                )
                try {
                    webdav.delete(rootUrl + staleSlashDir.path)
                    Logger.d(TAG, "   ✅ Deleted stale '/' directory at root")
                } catch (e: Exception) {
                    Logger.w(TAG, "   ⚠️ Could not delete stale '/' directory: ${e.message}")
                }
            } else {
                Logger.d(TAG, "   ℹ️ No stale '/' directory found at root (checked name field)")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "   ⚠️ Root cleanup check failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    private fun ensureMarkdownDirExists(webdav: WebDavClient, serverUrl: String) {
        if (connectionManager.markdownDirEnsured) return

        try {
            val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

            // 🔧 v2.2.1 (Issue #50): exists() may return HTTP 405 on bewCloud/some servers,
            // which WebDavClient throws as an IOException. Fallback: try list() — if it succeeds,
            // the directory exists. Identical pattern to WebDavSyncService.ensureMarkdownDirectoryExists().
            val dirExists = try {
                webdav.exists(mdUrl)
            } catch (e: java.io.IOException) {
                Logger.w(TAG, "⚠️ notes-md/ exists() check failed: ${e.message}, trying list()")
                try {
                    webdav.list(mdUrl)
                    true
                } catch (_: java.io.IOException) {
                    false
                }
            }

            if (!dirExists) {
                webdav.createDirectory(mdUrl)
                Logger.d(TAG, "📁 Created notes-md/ directory")
            }
            connectionManager.markdownDirEnsured = true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create notes-md/: ${e.message}")
        }
    }
}
