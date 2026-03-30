package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Logger
import java.security.MessageDigest
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 🆕 v2.0.0: Extrahiert Markdown-Export/-Import-Logik aus WebDavSyncService.
 * Verantwortlich für alle Operationen mit notes-md/ auf dem WebDAV-Server.
 */
@Suppress("TooManyFunctions", "LongParameterList")
internal class MarkdownSyncManager(
    private val prefs: SharedPreferences,
    private val storage: NotesStorage,
    private val eTagCache: ETagCache,
    private val urlBuilder: SyncUrlBuilder,
    private val connectionManager: ConnectionManager,
    private val timestampManager: SyncTimestampManager,
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "MarkdownSyncManager"
        private const val MAX_FILENAME_LENGTH = 200
        private const val ETAG_PREVIEW_LENGTH = 8
        private const val CONTENT_PREVIEW_LENGTH = 50
    }

    // ─────────────────────────────────────────────────────────────
    // Export
    // ─────────────────────────────────────────────────────────────

    /**
     * Exportiert einzelne Note als Markdown.
     *
     * 🔧 v1.9.0 (Opt 1): markdownDirExists-Parameter eliminiert redundanten exists()-Call
     * 🔧 v1.9.0 (Opt 6): MD-Content-Hash-Cache für Skip bei unverändertem Inhalt
     */
    fun exportSingle(sardine: Sardine, serverUrl: String, note: Note, markdownDirExists: Boolean = true) {
        val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

        // 🔧 v1.9.0 (Opt 1): Nur prüfen/erstellen wenn Caller nicht bereits bestätigt hat
        if (!markdownDirExists) {
            if (!sardine.exists(mdUrl)) {
                sardine.createDirectory(mdUrl)
                Logger.d(TAG, "📁 Created notes-md/ directory")
            }
        }

        val baseFilename = sanitizeFilename(note.title)
        var filename = "$baseFilename.md"
        // 🔧 v1.8.2 (IMPL_025): trimEnd('/') verhindert Double-Slash
        var noteUrl = "${mdUrl.trimEnd('/')}/$filename"

        // 🆕 v1.9.0 (Opt 6): MD-Content-Hash berechnen und mit Cache vergleichen
        val mdContentStr = note.toMarkdown()
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

        // Prüfe ob Datei bereits existiert und von anderer Note stammt
        try {
            if (sardine.exists(noteUrl)) {
                // Lese existierende Datei und prüfe ID im YAML-Header
                val existingContent = sardine.get(noteUrl).use { it.bufferedReader().readText() }
                val existingIdMatch = Regex("^---\\n.*?\\nid:\\s*([a-f0-9-]+)", RegexOption.DOT_MATCHES_ALL)
                    .find(existingContent)
                val existingId = existingIdMatch?.groupValues?.get(1)

                if (existingId != null && existingId != note.id) {
                    // Andere Note hat gleichen Titel - verwende ID-Suffix
                    val shortId = note.id.take(8)
                    filename = "${baseFilename}_$shortId.md"
                    noteUrl = "${mdUrl.trimEnd('/')}/$filename"
                    Logger.d(TAG, "📝 Duplicate title, using: $filename")
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "⚠️ Could not check existing file: ${e.message}")
            // Continue with default filename
        }

        // Upload
        sardine.put(noteUrl, mdContentBytes, "text/markdown")

        // 🆕 v1.9.0 (Opt 6): MD-Hash und E-Tag nach erfolgreichem Upload cachen
        try {
            val mdResource = sardine.list(noteUrl, 0).firstOrNull()
            val mdETag = mdResource?.etag
            prefs.edit {
                putString("content_hash_md_${note.id}", mdHash)
                if (mdETag != null) {
                    putString("etag_md_${note.id}", mdETag)
                }
            }
            Logger.d(TAG, "   ⚡ MD E-Tag cached: ${mdETag?.take(ETAG_PREVIEW_LENGTH)}")
        } catch (e: Exception) {
            // Non-fatal: Hash trotzdem cachen für nächsten Content-Vergleich
            prefs.edit { putString("content_hash_md_${note.id}", mdHash) }
            Logger.w(TAG, "   ⚠️ MD E-Tag fetch failed: ${e.message}")
        }
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

        val timeoutMs = connectionManager.getTimeoutMs()
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val sardine = SafeSardineWrapper.create(okHttpClient, username, password)

        try {
            val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

            // Ordner sollte bereits existieren, aber Sicherheitscheck
            ensureMarkdownDirExists(sardine, serverUrl)

            // Hole ALLE lokalen Notizen (inklusive SYNCED)
            val allNotes = storage.loadAllNotes()
            val totalCount = allNotes.size
            var exportedCount = 0

            // Track used filenames to handle duplicates
            val usedFilenames = mutableSetOf<String>()

            Logger.d(TAG, "📝 Found $totalCount notes to export")

            allNotes.forEachIndexed { index, note ->
                try {
                    // Progress-Callback
                    onProgress(index + 1, totalCount)

                    // Eindeutiger Filename (mit Duplikat-Handling)
                    val filename = getUniqueFilename(note, usedFilenames) + ".md"
                    // 🔧 v1.8.2 (IMPL_025): trimEnd('/') verhindert Double-Slash
                    val noteUrl = "${mdUrl.trimEnd('/')}/$filename"

                    // Konvertiere zu Markdown
                    val mdContent = note.toMarkdown().toByteArray()

                    // Upload (überschreibt falls vorhanden)
                    sardine.put(noteUrl, mdContent, "text/markdown")

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
            // 🐛 FIX: Connection Leak — SafeSardineWrapper explizit schließen
            sardine.close()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Sync (Manual)
    // ─────────────────────────────────────────────────────────────

    /**
     * Manueller Markdown-Sync: Import aller Server-Markdown-Dateien.
     * Erstellt seinen eigenen Sardine-Client für eigenständige Aufrufe.
     */
    suspend fun syncAll(serverUrl: String, username: String, password: String): Int = withContext(ioDispatcher) {
        return@withContext try {
            Logger.d(TAG, "📝 Starting Markdown sync...")

            val timeoutMs = connectionManager.getTimeoutMs()
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            val sardine = SafeSardineWrapper.create(okHttpClient, username, password)

            try {
                val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

                // Check if notes-md/ exists
                if (!sardine.exists(mdUrl)) {
                    Logger.d(TAG, "⚠️ notes-md/ directory not found - skipping MD import")
                    return@withContext 0
                }

                val localNotes = storage.loadAllNotes()
                val mdResources = sardine.list(mdUrl).filter { it.name.endsWith(".md") }
                var importedCount = 0

                Logger.d(TAG, "📂 Found ${mdResources.size} markdown files")

                for (resource in mdResources) {
                    try {
                        // Download MD-File
                        val mdContent = sardine.get(resource.href.toString())
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
                        Logger.e(TAG, "Failed to import ${resource.name}", e)
                        // Continue with other files
                    }
                }

                Logger.d(TAG, "✅ Markdown sync completed: $importedCount imported")
                importedCount
            } finally {
                // 🐛 FIX: Connection Leak — SafeSardineWrapper explizit schließen
                sardine.close()
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
    @Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements")
    fun importAll(sardine: Sardine, serverUrl: String, excludeNoteIds: Set<String> = emptySet()): Int {
        return try {
            Logger.d(TAG, "📝 Importing Markdown files...")

            val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

            if (!sardine.exists(mdUrl)) {
                Logger.d(TAG, "   ⚠️ notes-md/ directory not found - skipping")
                return 0
            }

            cleanupStaleRoot(sardine, serverUrl)

            val mdResources = sardine.list(mdUrl).filter { !it.isDirectory && it.name.endsWith(".md") }
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

            // 🔧 v1.11.0: Phase erst hier setzen — nach dem Fast-Path-Check.
            SyncStateManager.updateProgress(
                phase = SyncPhase.IMPORTING_MARKDOWN,
                current = 0,
                total = mdResources.size
            )

            var processedCount = 0
            for (resource in mdResources) {
                SyncStateManager.updateProgress(
                    phase = SyncPhase.IMPORTING_MARKDOWN,
                    current = ++processedCount,
                    total = mdResources.size,
                    currentFileName = null
                )
                try {
                    val serverModifiedTime = resource.modified?.time ?: 0L

                    // ⚡ v1.3.1: PERFORMANCE - Skip wenn Datei seit letztem Sync nicht geändert wurde
                    if (lastSyncTime > 0 && serverModifiedTime <= lastSyncTime) {
                        skippedCount++
                        Logger.d(TAG, "   ⏭️ Skipping ${resource.name}: not modified since last sync")
                        continue
                    }

                    Logger.d(TAG, "   🔍 Processing: ${resource.name}, modified=${resource.modified}")

                    SyncStateManager.updateProgress(
                        phase = SyncPhase.IMPORTING_MARKDOWN,
                        current = processedCount,
                        total = mdResources.size,
                        currentFileName = resource.name
                    )

                    // Build full URL
                    val mdFileUrl = mdUrl.trimEnd('/') + "/" + resource.name

                    // Download MD content
                    val mdContent = sardine.get(mdFileUrl).use { it.bufferedReader().readText() }
                    Logger.d(TAG, "      Downloaded ${mdContent.length} chars")

                    // 🔧 v1.7.2 (IMPL_014): Server mtime übergeben für korrekte Timestamp-Sync
                    val mdNote = Note.fromMarkdown(mdContent, serverModifiedTime)
                    if (mdNote == null) {
                        Logger.w(TAG, "      ⚠️ Failed to parse ${resource.name} - fromMarkdown returned null")
                        continue
                    }

                    // FIX-05 (v2.2.0): Checklist-Import-Logging + Korruptions-Warnung
                    if (mdNote.noteType == NoteType.CHECKLIST) {
                        Logger.d(TAG, "      📋 Checklist import: title='${mdNote.title}', items=${mdNote.checklistItems?.size ?: 0}")
                        if (mdNote.title.contains("[ ]") || mdNote.title.contains("[x]") || mdNote.title.contains("[X]")) {
                            @Suppress("MagicNumber")
                            val previewLength = 200
                            Logger.e(TAG, "🚨 CORRUPTION WARNING: Checklist pattern in title after parse: '${mdNote.title}'")
                            Logger.e(TAG, "🚨 Source: ${resource.name}, first $previewLength chars: ${mdContent.take(previewLength)}")
                        }
                    }

                    // 🆕 v1.11.0: Skip Markdown files whose note ID was just exported in this sync cycle.
                    if (mdNote.id in excludeNoteIds) {
                        skippedCount++
                        Logger.d(
                            TAG,
                            "   ⏭️ Skipping ${resource.name}: just exported in this sync cycle (ID=${mdNote.id})"
                        )
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
                            "      ⚠️ Skipping ${resource.name}: " +
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

                    when {
                        localNote == null -> {
                            storage.saveNote(mdNote.copy(syncStatus = SyncStatus.SYNCED))
                            importedCount++
                            Logger.d(TAG, "   ✅ Imported new from Markdown: ${mdNote.title}")
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
                        contentChanged && localNote.syncStatus == SyncStatus.SYNCED -> {
                            storage.saveNote(mdNote.copy(syncStatus = SyncStatus.PENDING))
                            importedCount++
                            Logger.d(
                                TAG,
                                "   ✅ Imported changed content (marked PENDING for JSON sync): ${mdNote.title}"
                            )
                        }
                        mdNote.updatedAt > localNote.updatedAt -> {
                            Logger.d(TAG, "      Decision: Markdown has newer timestamp!")
                            if (localNote.syncStatus == SyncStatus.PENDING) {
                                storage.saveNote(localNote.copy(syncStatus = SyncStatus.CONFLICT))
                                Logger.w(TAG, "   ⚠️ Conflict: Markdown vs local pending: ${mdNote.id}")
                            } else {
                                storage.saveNote(mdNote.copy(syncStatus = SyncStatus.SYNCED))
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
                    Logger.e(TAG, "   ⚠️ Failed to import ${resource.name}", e)
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

    // ─────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────

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
     * Finds a Markdown file by scanning YAML frontmatter for note ID.
     * Used when local note is deleted and title is unavailable.
     */
    suspend fun findByNoteId(sardine: Sardine, mdUrl: String, noteId: String): String? = withContext(ioDispatcher) {
        return@withContext try {
            Logger.d(TAG, "🔍 Scanning MD files for ID: $noteId")
            val resources = sardine.list(mdUrl)

            for (resource in resources) {
                if (resource.isDirectory || !resource.name.endsWith(".md")) {
                    continue
                }

                try {
                    val mdFileUrl = mdUrl.trimEnd('/') + "/" + resource.name
                    val mdContent = sardine.get(mdFileUrl).use { it.bufferedReader().readText() }

                    val idMatch = Regex("""^---\s*\n.*?id:\s*([a-f0-9-]+)""", RegexOption.DOT_MATCHES_ALL)
                        .find(mdContent)

                    if (idMatch?.groupValues?.get(1) == noteId) {
                        Logger.d(TAG, "   ✅ Found MD file: ${resource.name}")
                        return@withContext resource.name
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "   ⚠️ Failed to parse ${resource.name}: ${e.message}")
                }
            }

            Logger.w(TAG, "   ❌ No MD file found for ID: $noteId")
            null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to scan MD files: ${e.message}")
            null
        }
    }

    /**
     * 🔧 v1.8.2: One-time cleanup of stale "/" directory at WebDAV root.
     */
    fun cleanupStaleRoot(sardine: Sardine, serverUrl: String) {
        try {
            val rootUrl = serverUrl.trimEnd('/')
            Logger.d(TAG, "   🔍 DEBUG: Scanning root for stale '/' directory: $rootUrl")
            val rootResources = sardine.list(rootUrl)
            Logger.d(TAG, "   🔍 DEBUG: Found ${rootResources.size} resources at root")
            for ((index, res) in rootResources.withIndex()) {
                Logger.d(
                    TAG,
                    "   🔍 DEBUG [$index]: name='${res.name}', path='${res.path}', " +
                        "isDir=${res.isDirectory}, href=${res.href}"
                )
            }
            val staleSlashDir = rootResources.find { res -> res.isDirectory && res.name == "/" }
            if (staleSlashDir != null) {
                val staleHref = staleSlashDir.href?.toString().orEmpty()
                Logger.w(TAG, "   🗑️ Found stale '/' directory at root (double-slash bug artifact): $staleHref")
                try {
                    sardine.delete(rootUrl + staleSlashDir.href.path)
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

    private fun ensureMarkdownDirExists(sardine: Sardine, serverUrl: String) {
        if (connectionManager.markdownDirEnsured) return

        try {
            val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)
            if (!sardine.exists(mdUrl)) {
                sardine.createDirectory(mdUrl)
                Logger.d(TAG, "📁 Created notes-md/ directory (for future use)")
            }
            connectionManager.markdownDirEnsured = true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create notes-md/: ${e.message}")
        }
    }
}
