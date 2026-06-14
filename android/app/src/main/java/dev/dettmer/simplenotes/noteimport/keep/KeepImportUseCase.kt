package dev.dettmer.simplenotes.noteimport.keep

import android.net.Uri
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.noteimport.keep.conflict.ConflictResolver
import dev.dettmer.simplenotes.noteimport.keep.mapper.KeepToNoteMapper
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNote
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNoteState
import dev.dettmer.simplenotes.noteimport.keep.parser.KeepEntryParser
import dev.dettmer.simplenotes.noteimport.keep.persistence.LabelStore
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepPreScanResult
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipEntry
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipReader
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.flow.collect

/**
 * v2.5.0 — Top-Level-Orchestrator des Keep-Imports.
 *
 * Vertrag:
 *  - **Soft-Fail pro Entry** — eine fehlgeschlagene Notiz unterbricht den
 *    Lauf nicht; sie landet als [KeepImportError] in der Summary.
 *  - **Fatal nur bei ZIP-Defekten** (IOException aus dem Reader); diese
 *    werden propagiert, der Caller (ViewModel) zeigt `KeepImportUiState.Error`.
 *  - **Cancellation** (CancellationException) wird **nicht** abgefangen.
 *  - Triggert **keinen** Sync (siehe Commit #14).
 *
 * Memory-Profil: Der HTML-Geschwister-Lookup wird als dünner
 * `Map<basename, KeepZipEntry>` aufgebaut; das halten wir bewusst im Speicher,
 * weil `.html`-Dateien klein sind (typisch <10 KB). Bei sehr großen Archiven
 * (>1000 Notizen) bleibt das im niedrigen MB-Bereich.
 */
// Constructor has 7 parameters matching the 7 collaborators of this pipeline;
// no grouping object would reduce coupling — each dependency is independently testable.
@Suppress("LongParameterList")
internal class KeepImportUseCase(
    private val zipReader: KeepZipReader,
    private val entryParser: KeepEntryParser,
    private val mapper: KeepToNoteMapper,
    private val conflictResolver: ConflictResolver,
    private val storage: NotesStorage,
    private val labelStore: LabelStore,
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Streamt den Import. `preScan` ist optional vor-berechnet (vom ViewModel
     * für die Configuring-Dialog-Vorschau); fehlt er, wird intern berechnet.
     *
     * @return vollständige [KeepImportSummary]; throws nur bei Fatal-IOException.
     */
    @Suppress("LongMethod") // klarer linearer Ablauf; Aufsplitten würde Lesbarkeit verschlechtern
    suspend fun import(
        zipUri: Uri,
        options: KeepImportOptions,
        preScan: KeepPreScanResult? = null,
        onProgress: suspend (KeepImportProgress) -> Unit = {}
    ): KeepImportSummary {
        val scan = preScan ?: zipReader.preScan(zipUri)
        val total = scan.matchingCount(options.includeArchived, options.includeTrashed)

        var imported = 0
        var skipped = 0
        var replaced = 0
        var failed = 0
        var droppedAttachmentsNotes = 0
        var sharedImported = 0
        var notesWithColor = 0
        var notesWithPin = 0
        val errors = mutableListOf<KeepImportError>()
        var processed = 0

        // 1. Pass: HTML-Geschwister sammeln (Basename → Entry).
        val htmlByBase: MutableMap<String, KeepZipEntry> = HashMap()
        val jsonEntries: MutableList<KeepZipEntry> = ArrayList()
        zipReader.readEntries(zipUri).collect { entry ->
            when {
                entry.name.endsWith(".html", ignoreCase = true) ->
                    htmlByBase[basename(entry.name)] = entry
                entry.name.endsWith(".json", ignoreCase = true) ->
                    jsonEntries.add(entry)
                else -> Unit
            }
        }

        // 2. Pass: Verarbeitung.
        for (jsonEntry in jsonEntries) {
            val keepNote: KeepNote? = try {
                entryParser.parse(jsonEntry, htmlByBase[basename(jsonEntry.name)])
            } catch (e: Exception) {
                Logger.w(TAG, "Parse threw for '${jsonEntry.name}': ${e.message}")
                null
            }

            if (keepNote == null) {
                failed++
                errors += KeepImportError(jsonEntry.name, "parse failed")
            } else if (!matchesFilter(keepNote, options)) {
                skipped++
            } else {
                try {
                    val note: Note = mapper.map(keepNote, importedAtMs = nowMsProvider())
                    when (val r = conflictResolver.resolve(note, options.conflictStrategy)) {
                        is ConflictResolver.Resolution.Create -> {
                            storage.saveNote(note)
                            imported++
                        }
                        is ConflictResolver.Resolution.Replace -> {
                            storage.saveNote(note.copy(id = r.existingId))
                            replaced++
                        }
                        is ConflictResolver.Resolution.Skip -> {
                            skipped++
                        }
                    }
                    if (keepNote.attachments.isNotEmpty()) droppedAttachmentsNotes++
                    if (keepNote.isShared) sharedImported++
                    if (note.color != null) notesWithColor++
                    if (note.isPinned == true) notesWithPin++

                    if (keepNote.labels.isNotEmpty()) {
                        labelStore.appendBatch(note.id, keepNote.labels.map { it.name })
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Keep entry '${jsonEntry.name}' failed: ${e.message}")
                    failed++
                    errors += KeepImportError(jsonEntry.name, e.message ?: "unknown error")
                }
            }

            processed++
            emitProgress(onProgress, processed, total, jsonEntry.name)
        }

        val labelsImported = try {
            labelStore.load().size()
        } catch (e: Exception) {
            Logger.w(TAG, "labelStore.load failed for summary: ${e.message}")
            0
        }

        return KeepImportSummary(
            totalEntries = jsonEntries.size,
            imported = imported,
            skipped = skipped,
            replaced = replaced,
            failed = failed,
            notesWithDroppedAttachments = droppedAttachmentsNotes,
            labelsImported = labelsImported,
            sharedNotesImported = sharedImported,
            notesWithColor = notesWithColor,
            notesWithPin = notesWithPin,
            errors = errors.toList()
        )
    }

    private fun matchesFilter(keep: KeepNote, options: KeepImportOptions): Boolean = when (keep.state) {
        KeepNoteState.ACTIVE -> true
        KeepNoteState.ARCHIVED -> options.includeArchived
        KeepNoteState.TRASHED -> options.includeTrashed
    }

    private suspend fun emitProgress(
        onProgress: suspend (KeepImportProgress) -> Unit,
        processed: Int,
        total: Int,
        currentName: String
    ) {
        try {
            onProgress(KeepImportProgress(processed = processed, total = total, currentName = currentName))
        } catch (e: Exception) {
            Logger.w(TAG, "onProgress callback failed: ${e.message}")
        }
    }

    private fun basename(name: String): String {
        val lastSlash = name.lastIndexOfAny(charArrayOf('/', '\\'))
        val withoutDir = if (lastSlash >= 0) name.substring(lastSlash + 1) else name
        val lastDot = withoutDir.lastIndexOf('.')
        return if (lastDot > 0) withoutDir.substring(0, lastDot) else withoutDir
    }

    companion object {
        private const val TAG = "KeepImportUseCase"
    }
}
