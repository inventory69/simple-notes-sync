package dev.dettmer.simplenotes.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * BackupManager: Lokale Backup & Restore Funktionalität
 * 
 * Features:
 * - Backup aller Notizen in JSON-Datei
 * - Restore mit 3 Modi (Merge, Replace, Overwrite Duplicates)
 * - Auto-Backup vor Restore (Sicherheitsnetz)
 * - Backup-Validierung
 */
class BackupManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_VERSION = 1
        private const val AUTO_BACKUP_DIR = "auto_backups"
        private const val AUTO_BACKUP_RETENTION_DAYS = 7
    }
    
    private val storage = NotesStorage(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    /**
     * Erstellt Backup aller Notizen
     * 
     * @param uri Output-URI (via Storage Access Framework)
     * @return BackupResult mit Erfolg/Fehler Info
     */
    suspend fun createBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        return@withContext try {
            Logger.d(TAG, "📦 Creating backup to: $uri")
            
            val allNotes = storage.loadAllNotes()
            Logger.d(TAG, "   Found ${allNotes.size} notes to backup")
            
            val backupData = BackupData(
                backupVersion = BACKUP_VERSION,
                createdAt = System.currentTimeMillis(),
                notesCount = allNotes.size,
                appVersion = BuildConfig.VERSION_NAME,
                notes = allNotes
            )
            
            val jsonString = gson.toJson(backupData)
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
                Logger.d(TAG, "✅ Backup created successfully")
            }
            
            BackupResult(
                success = true,
                notesCount = allNotes.size,
                message = "Backup erstellt: ${allNotes.size} Notizen"
            )
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create backup", e)
            BackupResult(
                success = false,
                error = "Backup fehlgeschlagen: ${e.message}"
            )
        }
    }
    
    /**
     * Erstellt automatisches Backup (vor Restore)
     * Gespeichert in app-internem Storage
     * 
     * @return Uri des Auto-Backups oder null bei Fehler
     */
    suspend fun createAutoBackup(): Uri? = withContext(Dispatchers.IO) {
        return@withContext try {
            val autoBackupDir = File(context.filesDir, AUTO_BACKUP_DIR).apply {
                if (!exists()) mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
                .format(Date())
            val filename = "auto_backup_before_restore_$timestamp.json"
            val file = File(autoBackupDir, filename)
            
            Logger.d(TAG, "📦 Creating auto-backup: ${file.absolutePath}")
            
            val allNotes = storage.loadAllNotes()
            val backupData = BackupData(
                backupVersion = BACKUP_VERSION,
                createdAt = System.currentTimeMillis(),
                notesCount = allNotes.size,
                appVersion = BuildConfig.VERSION_NAME,
                notes = allNotes
            )
            
            file.writeText(gson.toJson(backupData))
            
            // Cleanup alte Auto-Backups
            cleanupOldAutoBackups(autoBackupDir)
            
            Logger.d(TAG, "✅ Auto-backup created: ${file.absolutePath}")
            Uri.fromFile(file)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create auto-backup", e)
            null
        }
    }
    
    /**
     * Stellt Notizen aus Backup wieder her
     * 
     * @param uri Backup-Datei URI
     * @param mode Wiederherstellungs-Modus (Merge/Replace/Overwrite)
     * @return RestoreResult mit Details
     */
    suspend fun restoreBackup(uri: Uri, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        return@withContext try {
            Logger.d(TAG, "📥 Restoring backup from: $uri (mode: $mode)")
            
            // 1. Backup-Datei lesen
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: return@withContext RestoreResult(
                success = false,
                error = "Datei konnte nicht gelesen werden"
            )
            
            // 2. Backup validieren & parsen
            val validationResult = validateBackup(jsonString)
            if (!validationResult.isValid) {
                return@withContext RestoreResult(
                    success = false,
                    error = validationResult.errorMessage ?: context.getString(R.string.error_invalid_backup_file)
                )
            }
            
            val backupData = gson.fromJson(jsonString, BackupData::class.java)
            Logger.d(TAG, "   Backup valid: ${backupData.notesCount} notes, version ${backupData.backupVersion}")
            
            // 3. Auto-Backup erstellen (Sicherheitsnetz)
            val autoBackupUri = createAutoBackup()
            if (autoBackupUri == null) {
                Logger.w(TAG, "⚠️ Auto-backup failed, but continuing with restore")
            }
            
            // 4. Restore durchführen (je nach Modus)
            val result = when (mode) {
                RestoreMode.MERGE -> restoreMerge(backupData.notes)
                RestoreMode.REPLACE -> restoreReplace(backupData.notes)
                RestoreMode.OVERWRITE_DUPLICATES -> restoreOverwriteDuplicates(backupData.notes)
            }
            
            Logger.d(TAG, "✅ Restore completed: ${result.importedNotes} imported, ${result.skippedNotes} skipped")
            result
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to restore backup", e)
            RestoreResult(
                success = false,
                error = context.getString(R.string.error_restore_failed, e.message ?: "")
            )
        }
    }
    
    /**
     * Validiert Backup-Datei
     */
    private fun validateBackup(jsonString: String): ValidationResult {
        return try {
            val backupData = gson.fromJson(jsonString, BackupData::class.java)
            
            // Version kompatibel?
            if (backupData.backupVersion > BACKUP_VERSION) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = context.getString(R.string.error_backup_version_unsupported, backupData.backupVersion, BACKUP_VERSION)
                )
            }
            
            // Notizen-Array vorhanden?
            if (backupData.notes.isEmpty()) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = context.getString(R.string.error_backup_empty)
                )
            }
            
            // Alle Notizen haben ID, title, content?
            val invalidNotes = backupData.notes.filter { note ->
                note.id.isBlank() || note.title.isBlank()
            }
            
            if (invalidNotes.isNotEmpty()) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = context.getString(R.string.error_backup_invalid_notes, invalidNotes.size)
                )
            }
            
            ValidationResult(isValid = true)
            
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                errorMessage = context.getString(R.string.error_backup_corrupt, e.message ?: "")
            )
        }
    }
    
    /**
     * Restore-Modus: MERGE
     * Fügt neue Notizen hinzu, behält bestehende
     */
    private fun restoreMerge(backupNotes: List<Note>): RestoreResult {
        val existingNotes = storage.loadAllNotes()
        val existingIds = existingNotes.map { it.id }.toSet()
        
        val newNotes = backupNotes.filter { it.id !in existingIds }
        val skippedNotes = backupNotes.size - newNotes.size
        
        newNotes.forEach { note ->
            storage.saveNote(note)
        }
        
        return RestoreResult(
            success = true,
            importedNotes = newNotes.size,
            skippedNotes = skippedNotes,
            message = context.getString(R.string.restore_merge_result, newNotes.size, skippedNotes)
        )
    }
    
    /**
     * Restore-Modus: REPLACE
     * Löscht alle bestehenden Notizen, importiert Backup
     */
    private fun restoreReplace(backupNotes: List<Note>): RestoreResult {
        // Alle bestehenden Notizen löschen
        storage.deleteAllNotes()
        
        // Backup-Notizen importieren
        backupNotes.forEach { note ->
            storage.saveNote(note)
        }
        
        return RestoreResult(
            success = true,
            importedNotes = backupNotes.size,
            skippedNotes = 0,
            message = context.getString(R.string.restore_replace_result, backupNotes.size)
        )
    }

    /**
     * Restore-Modus: OVERWRITE_DUPLICATES
     * Backup überschreibt bei ID-Konflikten
     */
    private fun restoreOverwriteDuplicates(backupNotes: List<Note>): RestoreResult {
        val existingNotes = storage.loadAllNotes()
        val existingIds = existingNotes.map { it.id }.toSet()
        
        val newNotes = backupNotes.filter { it.id !in existingIds }
        val overwrittenNotes = backupNotes.filter { it.id in existingIds }
        
        // Alle Backup-Notizen speichern (überschreibt automatisch)
        backupNotes.forEach { note ->
            storage.saveNote(note)
        }
        
        return RestoreResult(
            success = true,
            importedNotes = newNotes.size,
            skippedNotes = 0,
            overwrittenNotes = overwrittenNotes.size,
            message = context.getString(R.string.restore_overwrite_result, newNotes.size, overwrittenNotes.size)
        )
    }
    
    /**
     * Löscht Auto-Backups älter als RETENTION_DAYS
     */
    private fun cleanupOldAutoBackups(autoBackupDir: File) {
        try {
            val retentionTimeMs = AUTO_BACKUP_RETENTION_DAYS * 24 * 60 * 60 * 1000L
            val cutoffTime = System.currentTimeMillis() - retentionTimeMs
            
            autoBackupDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    Logger.d(TAG, "🗑️ Deleting old auto-backup: ${file.name}")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cleanup old backups", e)
        }
    }
}

/**
 * Backup-Daten Struktur (JSON)
 * NOTE: Property names use @SerializedName for JSON compatibility with snake_case
 */
data class BackupData(
    @com.google.gson.annotations.SerializedName("backup_version")
    val backupVersion: Int,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: Long,
    @com.google.gson.annotations.SerializedName("notes_count")
    val notesCount: Int,
    @com.google.gson.annotations.SerializedName("app_version")
    val appVersion: String,
    val notes: List<Note>
)

/**
 * Wiederherstellungs-Modi
 */
enum class RestoreMode {
    MERGE,                  // Bestehende + Neue (Standard)
    REPLACE,                // Alles löschen + Importieren
    OVERWRITE_DUPLICATES    // Backup überschreibt bei ID-Konflikten
}

/**
 * Backup-Ergebnis
 */
data class BackupResult(
    val success: Boolean,
    val notesCount: Int = 0,
    val message: String? = null,
    val error: String? = null
)

/**
 * Restore-Ergebnis
 */
data class RestoreResult(
    val success: Boolean,
    val importedNotes: Int = 0,
    val skippedNotes: Int = 0,
    val overwrittenNotes: Int = 0,
    val message: String? = null,
    val error: String? = null
)

/**
 * Validierungs-Ergebnis
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
