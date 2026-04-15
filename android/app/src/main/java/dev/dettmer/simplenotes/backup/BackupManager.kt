package dev.dettmer.simplenotes.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BackupManager: Lokale Backup & Restore Funktionalität
 *
 * Features:
 * - Backup aller Notizen in JSON-Datei
 * - Restore mit 3 Modi (Merge, Replace, Overwrite Duplicates)
 * - Auto-Backup vor Restore (Sicherheitsnetz)
 * - Backup-Validierung
 */
class BackupManager(private val context: Context, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_VERSION = 1
        private const val AUTO_BACKUP_DIR = "auto_backups"
        private const val AUTO_BACKUP_RETENTION_DAYS = 7
        private const val MAGIC_BYTES_LENGTH = 4 // v1.7.0: For encryption check
    }

    private val storage = NotesStorage(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val encryptionManager = EncryptionManager() // 🔐 v1.7.0
    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Erstellt Backup aller Notizen
     *
     * @param uri Output-URI (via Storage Access Framework)
     * @param password Optional password for encryption (null = unencrypted)
     * @param includeServerSettings v1.9.0: If true, server credentials are included in the backup
     * @return BackupResult mit Erfolg/Fehler Info
     */
    @Suppress("MaxLineLength")
    suspend fun createBackup(uri: Uri, password: String? = null, includeServerSettings: Boolean = false): BackupResult =
        withContext(ioDispatcher) {
            return@withContext try {
                val encryptedSuffix = if (password != null) " (encrypted)" else ""
                Logger.d(TAG, "📦 Creating backup$encryptedSuffix to: $uri")

                val allNotes = storage.loadAllNotes()
                Logger.d(TAG, "   Found ${allNotes.size} notes to backup")

                // v1.9.0: Optionally include full app settings snapshot
                val appSettings = if (includeServerSettings) {
                    Logger.d(
                        TAG,
                        "   includeServerSettings=true, serverUrl=${prefs.getString(
                            Constants.KEY_SERVER_URL,
                            null
                        )?.take(20).orEmpty()}"
                    )
                    AppSettings(
                        // Server connection
                        serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null),
                        username = CredentialStore.getUsername(context),
                        password = CredentialStore.getPassword(context),
                        syncFolder = prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, null),
                        connectionTimeoutSeconds = prefs.getInt(Constants.KEY_CONNECTION_TIMEOUT_SECONDS, -1).takeIf {
                            it >=
                                0
                        },
                        maxParallelConnections = prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, -1).takeIf {
                            it >= 0
                        },
                        // Sync behaviour
                        offlineMode = prefs.getBoolean(Constants.KEY_OFFLINE_MODE, false).takeIf {
                            prefs.contains(Constants.KEY_OFFLINE_MODE)
                        },
                        autoSync = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false).takeIf {
                            prefs.contains(Constants.KEY_AUTO_SYNC)
                        },
                        wifiOnlySync = prefs.getBoolean(Constants.KEY_WIFI_ONLY_SYNC, false).takeIf {
                            prefs.contains(Constants.KEY_WIFI_ONLY_SYNC)
                        },
                        markdownExport = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false).takeIf {
                            prefs.contains(Constants.KEY_MARKDOWN_EXPORT)
                        },
                        markdownAutoImport = prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false).takeIf {
                            prefs.contains(Constants.KEY_MARKDOWN_AUTO_IMPORT)
                        },
                        alwaysCheckServer = prefs.getBoolean(Constants.KEY_ALWAYS_CHECK_SERVER, true).takeIf {
                            prefs.contains(Constants.KEY_ALWAYS_CHECK_SERVER)
                        },
                        alwaysDeleteFromServer = prefs.getBoolean(
                            Constants.KEY_ALWAYS_DELETE_FROM_SERVER,
                            false
                        ).takeIf {
                            prefs.contains(Constants.KEY_ALWAYS_DELETE_FROM_SERVER)
                        },
                        // Sync triggers
                        syncTriggerOnSave = prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, true).takeIf {
                            prefs.contains(Constants.KEY_SYNC_TRIGGER_ON_SAVE)
                        },
                        syncTriggerOnResume = prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, true).takeIf {
                            prefs.contains(Constants.KEY_SYNC_TRIGGER_ON_RESUME)
                        },
                        syncTriggerWifiConnect = prefs.getBoolean(
                            Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT,
                            true
                        ).takeIf {
                            prefs.contains(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT)
                        },
                        syncTriggerPeriodic = prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, false).takeIf {
                            prefs.contains(Constants.KEY_SYNC_TRIGGER_PERIODIC)
                        },
                        syncTriggerBoot = prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_BOOT, false).takeIf {
                            prefs.contains(Constants.KEY_SYNC_TRIGGER_BOOT)
                        },
                        syncIntervalMinutes = prefs.getLong(Constants.PREF_SYNC_INTERVAL_MINUTES, -1L).takeIf {
                            it >= 0
                        },
                        // Display
                        displayMode = prefs.getString(Constants.KEY_DISPLAY_MODE, null),
                        themeMode = prefs.getString("theme_mode", null),
                        colorTheme = prefs.getString("color_theme", null),
                        customAppTitle = prefs.getString(Constants.KEY_CUSTOM_APP_TITLE, null),
                        // Notes behaviour
                        autosaveEnabled = prefs.getBoolean(Constants.KEY_AUTOSAVE_ENABLED, true).takeIf {
                            prefs.contains(Constants.KEY_AUTOSAVE_ENABLED)
                        },
                        sortOption = prefs.getString(Constants.KEY_SORT_OPTION, null),
                        sortDirection = prefs.getString(Constants.KEY_SORT_DIRECTION, null),
                        // Notifications
                        notificationsEnabled = prefs.getBoolean(Constants.KEY_NOTIFICATIONS_ENABLED, true).takeIf {
                            prefs.contains(Constants.KEY_NOTIFICATIONS_ENABLED)
                        },
                        notificationsErrorsOnly = prefs.getBoolean(
                            Constants.KEY_NOTIFICATIONS_ERRORS_ONLY,
                            false
                        ).takeIf {
                            prefs.contains(Constants.KEY_NOTIFICATIONS_ERRORS_ONLY)
                        },
                        notificationsServerWarning = prefs.getBoolean(
                            Constants.KEY_NOTIFICATIONS_SERVER_WARNING,
                            true
                        ).takeIf {
                            prefs.contains(Constants.KEY_NOTIFICATIONS_SERVER_WARNING)
                        },
                        // Grid column control
                        gridAdaptiveScaling = prefs.getBoolean(Constants.KEY_GRID_ADAPTIVE_SCALING, true).takeIf {
                            prefs.contains(Constants.KEY_GRID_ADAPTIVE_SCALING)
                        },
                        gridManualColumns = prefs.getInt(Constants.KEY_GRID_MANUAL_COLUMNS, -1).takeIf { it >= 0 }
                    )
                } else {
                    Logger.d(TAG, "   includeServerSettings=false – no app settings in backup")
                    null
                }

                val backupData = BackupData(
                    backupVersion = BACKUP_VERSION,
                    createdAt = System.currentTimeMillis(),
                    notesCount = allNotes.size,
                    appVersion = BuildConfig.VERSION_NAME,
                    notes = allNotes,
                    appSettings = appSettings
                )

                val jsonString = gson.toJson(backupData)

                // 🔐 v1.7.0: Encrypt if password is provided
                val dataToWrite = if (password != null) {
                    encryptionManager.encrypt(jsonString.toByteArray(), password)
                } else {
                    jsonString.toByteArray()
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(dataToWrite)
                    Logger.d(TAG, "✅ Backup created successfully$encryptedSuffix")
                }

                BackupResult(
                    success = true,
                    notesCount = allNotes.size,
                    message = "Backup erstellt: ${allNotes.size} Notizen$encryptedSuffix"
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
    suspend fun createAutoBackup(): Uri? = withContext(ioDispatcher) {
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
     * @param password Optional password if backup is encrypted
     * @param restoreServerSettings v1.9.0: If true and backup contains server settings, restore them
     * @return RestoreResult mit Details
     */
    suspend fun restoreBackup(
        uri: Uri,
        mode: RestoreMode,
        password: String? = null,
        restoreServerSettings: Boolean = false
    ): RestoreResult = withContext(ioDispatcher) {
        return@withContext try {
            Logger.d(TAG, "📥 Restoring backup from: $uri (mode: $mode)")

            // 1. Backup-Datei lesen
            val fileData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: return@withContext RestoreResult(
                success = false,
                error = "Datei konnte nicht gelesen werden"
            )

            // 🔐 v1.7.0: Check if encrypted and decrypt if needed
            val jsonString = try {
                if (encryptionManager.isEncrypted(fileData)) {
                    if (password == null) {
                        return@withContext RestoreResult(
                            success = false,
                            error = "Backup ist verschlüsselt. Bitte Passwort eingeben."
                        )
                    }
                    val decrypted = encryptionManager.decrypt(fileData, password)
                    String(decrypted)
                } else {
                    String(fileData)
                }
            } catch (e: EncryptionException) {
                return@withContext RestoreResult(
                    success = false,
                    error = "Entschlüsselung fehlgeschlagen: ${e.message}"
                )
            }

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

            // v1.9.0: Optionally restore all app settings if present
            if (restoreServerSettings && backupData.appSettings != null) {
                val s = backupData.appSettings
                prefs.edit {
                    // Server connection
                    s.serverUrl?.let { putString(Constants.KEY_SERVER_URL, it) }
                    s.username?.let { username ->
                        val password = s.password.orEmpty()
                        CredentialStore.setCredentials(context, username, password)
                    }
                    s.syncFolder?.let { putString(Constants.KEY_SYNC_FOLDER_NAME, it) }
                    s.connectionTimeoutSeconds?.let { putInt(Constants.KEY_CONNECTION_TIMEOUT_SECONDS, it) }
                    s.maxParallelConnections?.let { putInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, it) }
                    // Sync behaviour
                    s.offlineMode?.let { putBoolean(Constants.KEY_OFFLINE_MODE, it) }
                    s.autoSync?.let { putBoolean(Constants.KEY_AUTO_SYNC, it) }
                    s.wifiOnlySync?.let { putBoolean(Constants.KEY_WIFI_ONLY_SYNC, it) }
                    s.markdownExport?.let { putBoolean(Constants.KEY_MARKDOWN_EXPORT, it) }
                    s.markdownAutoImport?.let { putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, it) }
                    s.alwaysCheckServer?.let { putBoolean(Constants.KEY_ALWAYS_CHECK_SERVER, it) }
                    s.alwaysDeleteFromServer?.let { putBoolean(Constants.KEY_ALWAYS_DELETE_FROM_SERVER, it) }
                    // Sync triggers
                    s.syncTriggerOnSave?.let { putBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, it) }
                    s.syncTriggerOnResume?.let { putBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, it) }
                    s.syncTriggerWifiConnect?.let { putBoolean(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, it) }
                    s.syncTriggerPeriodic?.let { putBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, it) }
                    s.syncTriggerBoot?.let { putBoolean(Constants.KEY_SYNC_TRIGGER_BOOT, it) }
                    s.syncIntervalMinutes?.let { putLong(Constants.PREF_SYNC_INTERVAL_MINUTES, it) }
                    // Display
                    s.displayMode?.let { putString(Constants.KEY_DISPLAY_MODE, it) }
                    s.themeMode?.let { putString("theme_mode", it) }
                    s.colorTheme?.let { putString("color_theme", it) }
                    s.customAppTitle?.let { putString(Constants.KEY_CUSTOM_APP_TITLE, it) }
                    // Notes behaviour
                    s.autosaveEnabled?.let { putBoolean(Constants.KEY_AUTOSAVE_ENABLED, it) }
                    s.sortOption?.let { putString(Constants.KEY_SORT_OPTION, it) }
                    s.sortDirection?.let { putString(Constants.KEY_SORT_DIRECTION, it) }
                    // Notifications
                    s.notificationsEnabled?.let { putBoolean(Constants.KEY_NOTIFICATIONS_ENABLED, it) }
                    s.notificationsErrorsOnly?.let { putBoolean(Constants.KEY_NOTIFICATIONS_ERRORS_ONLY, it) }
                    s.notificationsServerWarning?.let { putBoolean(Constants.KEY_NOTIFICATIONS_SERVER_WARNING, it) }
                    // Grid column control
                    s.gridAdaptiveScaling?.let { putBoolean(Constants.KEY_GRID_ADAPTIVE_SCALING, it) }
                    s.gridManualColumns?.let { putInt(Constants.KEY_GRID_MANUAL_COLUMNS, it) }
                }
                Logger.d(TAG, "✅ App settings restored from backup")
            }

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
                error = context.getString(R.string.error_restore_failed, e.message.orEmpty())
            )
        }
    }

    /**
     * 🔐 v1.7.0: Check if backup file is encrypted
     */
    suspend fun isBackupEncrypted(uri: Uri): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val header = ByteArray(MAGIC_BYTES_LENGTH)
                val bytesRead = inputStream.read(header)
                bytesRead == MAGIC_BYTES_LENGTH && encryptionManager.isEncrypted(header)
            } ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check encryption status", e)
            false
        }
    }

    /**
     * v1.9.0: Check if a (decrypted) backup contains app settings
     */
    suspend fun backupContainsAppSettings(uri: Uri, password: String? = null): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            val fileData = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext false
            val jsonString = if (encryptionManager.isEncrypted(fileData)) {
                if (password == null) return@withContext false
                String(encryptionManager.decrypt(fileData, password))
            } else {
                String(fileData)
            }
            val backupData = gson.fromJson(jsonString, BackupData::class.java)
            backupData.appSettings != null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check app settings in backup", e)
            false
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
                    errorMessage = context.getString(
                        R.string.error_backup_version_unsupported,
                        backupData.backupVersion,
                        BACKUP_VERSION
                    )
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
                errorMessage = context.getString(R.string.error_backup_corrupt, e.message.orEmpty())
            )
        }
    }

    /**
     * Restore-Modus: MERGE
     * Fügt neue Notizen hinzu, behält bestehende
     */
    private suspend fun restoreMerge(backupNotes: List<Note>): RestoreResult {
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
    private suspend fun restoreReplace(backupNotes: List<Note>): RestoreResult {
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
    private suspend fun restoreOverwriteDuplicates(backupNotes: List<Note>): RestoreResult {
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
    val notes: List<Note>,
    // v1.9.0: Optional app settings snapshot (only present when user opted in).
    // Previously keyed as "server_settings"; broadened to cover all settings.
    @com.google.gson.annotations.SerializedName("app_settings")
    val appSettings: AppSettings? = null
)

/**
 * v1.9.0: Full app settings snapshot, optionally included in backups.
 * All fields are nullable — absent fields are silently skipped on restore,
 * ensuring backward compatibility with older backup files.
 * Encryption is strongly recommended when this is present (contains credentials).
 */
@Suppress("LongParameterList")
data class AppSettings(
    // ── Server connection ──────────────────────────────────────────────────
    @com.google.gson.annotations.SerializedName("server_url")
    val serverUrl: String? = null,
    @com.google.gson.annotations.SerializedName("username")
    val username: String? = null,
    @com.google.gson.annotations.SerializedName("password")
    val password: String? = null,
    @com.google.gson.annotations.SerializedName("sync_folder")
    val syncFolder: String? = null,
    @com.google.gson.annotations.SerializedName("connection_timeout_seconds")
    val connectionTimeoutSeconds: Int? = null,
    @com.google.gson.annotations.SerializedName("max_parallel_connections")
    val maxParallelConnections: Int? = null,
    // ── Sync behaviour ─────────────────────────────────────────────────────
    @com.google.gson.annotations.SerializedName("offline_mode")
    val offlineMode: Boolean? = null,
    @com.google.gson.annotations.SerializedName("auto_sync")
    val autoSync: Boolean? = null,
    @com.google.gson.annotations.SerializedName("wifi_only_sync")
    val wifiOnlySync: Boolean? = null,
    @com.google.gson.annotations.SerializedName("markdown_export")
    val markdownExport: Boolean? = null,
    @com.google.gson.annotations.SerializedName("markdown_auto_import")
    val markdownAutoImport: Boolean? = null,
    @com.google.gson.annotations.SerializedName("always_check_server")
    val alwaysCheckServer: Boolean? = null,
    @com.google.gson.annotations.SerializedName("always_delete_from_server")
    val alwaysDeleteFromServer: Boolean? = null,
    // ── Sync triggers ──────────────────────────────────────────────────────
    @com.google.gson.annotations.SerializedName("sync_trigger_on_save")
    val syncTriggerOnSave: Boolean? = null,
    @com.google.gson.annotations.SerializedName("sync_trigger_on_resume")
    val syncTriggerOnResume: Boolean? = null,
    @com.google.gson.annotations.SerializedName("sync_trigger_wifi_connect")
    val syncTriggerWifiConnect: Boolean? = null,
    @com.google.gson.annotations.SerializedName("sync_trigger_periodic")
    val syncTriggerPeriodic: Boolean? = null,
    @com.google.gson.annotations.SerializedName("sync_trigger_boot")
    val syncTriggerBoot: Boolean? = null,
    @com.google.gson.annotations.SerializedName("sync_interval_minutes")
    val syncIntervalMinutes: Long? = null,
    // ── Display ────────────────────────────────────────────────────────────
    @com.google.gson.annotations.SerializedName("display_mode")
    val displayMode: String? = null,
    @com.google.gson.annotations.SerializedName("theme_mode")
    val themeMode: String? = null,
    @com.google.gson.annotations.SerializedName("color_theme")
    val colorTheme: String? = null,
    @com.google.gson.annotations.SerializedName("custom_app_title")
    val customAppTitle: String? = null,
    // ── Notes behaviour ────────────────────────────────────────────────────
    @com.google.gson.annotations.SerializedName("autosave_enabled")
    val autosaveEnabled: Boolean? = null,
    @com.google.gson.annotations.SerializedName("sort_option")
    val sortOption: String? = null,
    @com.google.gson.annotations.SerializedName("sort_direction")
    val sortDirection: String? = null,
    // ── Notifications ──────────────────────────────────────────────────────
    @com.google.gson.annotations.SerializedName("notifications_enabled")
    val notificationsEnabled: Boolean? = null,
    @com.google.gson.annotations.SerializedName("notifications_errors_only")
    val notificationsErrorsOnly: Boolean? = null,
    @com.google.gson.annotations.SerializedName("notifications_server_warning")
    val notificationsServerWarning: Boolean? = null,
    // 🆕 v2.1.0 (F46): Grid column control
    @com.google.gson.annotations.SerializedName("grid_adaptive_scaling")
    val gridAdaptiveScaling: Boolean? = null,
    @com.google.gson.annotations.SerializedName("grid_manual_columns")
    val gridManualColumns: Int? = null
)

/**
 * Wiederherstellungs-Modi
 */
enum class RestoreMode {
    MERGE, // Bestehende + Neue (Standard)
    REPLACE, // Alles löschen + Importieren
    OVERWRITE_DUPLICATES // Backup überschreibt bei ID-Konflikten
}

/**
 * Backup-Ergebnis
 */
data class BackupResult(val success: Boolean, val notesCount: Int = 0, val message: String? = null, val error: String? = null)

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
data class ValidationResult(val isValid: Boolean, val errorMessage: String? = null)
