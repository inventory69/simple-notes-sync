package dev.dettmer.simplenotes.ui.settings.keepimport

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.dettmer.simplenotes.noteimport.keep.KeepImportUseCase
import dev.dettmer.simplenotes.noteimport.keep.conflict.ConflictResolver
import dev.dettmer.simplenotes.noteimport.keep.mapper.KeepToNoteMapper
import dev.dettmer.simplenotes.noteimport.keep.parser.KeepEntryParserImpl
import dev.dettmer.simplenotes.noteimport.keep.parser.KeepHtmlFallbackParser
import dev.dettmer.simplenotes.noteimport.keep.persistence.LabelStore
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipReaderImpl
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import java.util.UUID

/**
 * v2.5.0 — Manuelle Factory (DI-frei, konsistent zum bestehenden
 * Pattern in `SettingsViewModel`/`MainViewModel`).
 *
 * Konstruiert die komplette Use-Case-Pipeline einmalig pro ViewModel-Lebenszyklus.
 */
class KeepImportViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(KeepImportViewModel::class.java)) {
            "Unknown ViewModel: $modelClass"
        }
        val storage = NotesStorage(application)
        val zipReader = KeepZipReaderImpl(application)
        val htmlFallback = KeepHtmlFallbackParser()
        val parser = KeepEntryParserImpl(htmlFallback)
        val mapper = KeepToNoteMapper(
            deviceIdProvider = { DeviceIdGenerator.getDeviceId(application) },
            idGenerator = { UUID.randomUUID().toString() }
        )
        val resolver = ConflictResolver(storage)
        val labelStore = LabelStore(application)
        val useCase = KeepImportUseCase(
            zipReader = zipReader,
            entryParser = parser,
            mapper = mapper,
            conflictResolver = resolver,
            storage = storage,
            labelStore = labelStore
        )
        val syncScheduler = dev.dettmer.simplenotes.sync.SyncScheduler(application)
        return KeepImportViewModel(
            application = application,
            useCase = useCase,
            zipReader = zipReader,
            syncScheduler = syncScheduler
        ) as T
    }
}
