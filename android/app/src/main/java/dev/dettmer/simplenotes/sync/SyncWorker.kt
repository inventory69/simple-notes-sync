package dev.dettmer.simplenotes.sync

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "SyncWorker"

        // WorkManager stop reason codes (mirrors WorkInfo.STOP_REASON_* constants, API 31+)
        private const val STOP_REASON_QUOTA = 10
        private const val STOP_REASON_BACKGROUND_RESTRICTION = 11
        private const val STOP_REASON_APP_STANDBY = 12
    }

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * 🔧 v1.7.2: Required for expedited work on Android 9-11
     *
     * WorkManager ruft diese Methode auf um die Foreground-Notification zu erstellen
     * wenn der Worker als Expedited Work gestartet wird.
     *
     * Ab Android 12+ wird diese Methode NICHT aufgerufen (neue Expedited API).
     * Auf Android 9-11 MUSS diese Methode implementiert sein!
     *
     * @see https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#foregroundinfo
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper.createSyncProgressNotification(applicationContext)

        // Android 10+ benötigt foregroundServiceType
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.SYNC_PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                NotificationHelper.SYNC_PROGRESS_NOTIFICATION_ID,
                notification
            )
        }
    }

    /**
     * Prüft ob die App im Vordergrund ist.
     * Wenn ja, brauchen wir keine Benachrichtigung - die UI zeigt die Änderungen direkt.
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = applicationContext.packageName

        return appProcesses.any { process ->
            process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                process.processName == packageName
        }
    }

    @Suppress("LongMethod") // Linear sync flow with debug logging — splitting would hurt readability
    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "═══════════════════════════════════════")
            Logger.d(TAG, "🔄 SyncWorker.doWork() ENTRY")
            Logger.d(TAG, "Context: ${applicationContext.javaClass.simpleName}")
            Logger.d(TAG, "Thread: ${Thread.currentThread().name}")
            Logger.d(TAG, "RunAttempt: $runAttemptCount")
        }

        return@withContext try {
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 1: Before WebDavSyncService creation")
            }

            // Try-catch um Service-Creation
            val syncService = try {
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "    Creating WebDavSyncService with applicationContext...")
                }
                WebDavSyncService(applicationContext).also {
                    if (BuildConfig.DEBUG) {
                        Logger.d(TAG, "    ✅ WebDavSyncService created successfully")
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "💥 CRASH in WebDavSyncService constructor!", e)
                Logger.e(TAG, "Exception: ${e.javaClass.name}: ${e.message}")
                throw e
            }

            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 2: SyncStateManager coordination & global cooldown (v1.8.1)")
            }

            // 🆕 v1.8.1 (IMPL_08): SyncStateManager-Koordination
            // Verhindert dass Foreground und Background gleichzeitig syncing-State haben
            val prefs = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

            // 🆕 v1.8.1 (IMPL_08B): onSave-Syncs bypassen den globalen Cooldown
            // Grund: User hat explizit gespeichert → erwartet zeitnahen Sync
            // Der eigene 5s-Throttle + isSyncing-Mutex reichen als Schutz
            val isOnSaveSync = tags.contains(Constants.SYNC_ONSAVE_TAG)

            // Globaler Cooldown-Check (nicht für onSave-Syncs)
            if (!isOnSaveSync && !SyncStateManager.canSyncGlobally(prefs)) {
                Logger.d(TAG, "⏭️ SyncWorker: Global sync cooldown active - skipping")
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (cooldown)")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                return@withContext Result.success()
            }

            if (!SyncStateManager.tryStartSync("worker-${tags.firstOrNull() ?: "unknown"}", silent = true)) {
                Logger.d(TAG, "⏭️ SyncWorker: Another sync already in progress - skipping")
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (already syncing)")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                return@withContext Result.success()
            }

            // Globalen Cooldown markieren
            SyncStateManager.markGlobalSyncStarted(prefs)

            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 3: Checking for unsynced changes (Performance Pre-Check)")
            }

            // 🔥 v1.1.2: Performance-Optimierung - Skip Sync wenn keine lokalen Änderungen
            // Spart Batterie + Netzwerk-Traffic + Server-Last
            if (!syncService.hasUnsyncedChanges()) {
                Logger.d(TAG, "⏭️ No local changes - skipping sync (performance optimization)")
                Logger.d(TAG, "   Saves battery, network traffic, and server load")

                // 🆕 v2.4.0 (FIX-SSBE-005): Visibility-aware Termination
                // markCompleted() prüft silent-Flag: silent=true → IDLE, promoted → COMPLETED
                SyncStateManager.markCompleted(
                    applicationContext.getString(R.string.toast_already_synced)
                )

                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (no changes to sync)")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }

                return@withContext Result.success()
            }

            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 4: Checking sync gate (canSync)")
            }

            // 🆕 v1.7.0: Zentrale Sync-Gate Prüfung (WiFi-Only, Offline Mode, Server Config)
            val gateResult = syncService.canSync()
            if (!gateResult.canSync) {
                if (gateResult.isBlockedByWifiOnly) {
                    Logger.d(TAG, "⏭️ WiFi-only mode enabled, but not on WiFi - skipping sync")
                } else {
                    Logger.d(TAG, "⏭️ Sync blocked by gate: ${gateResult.blockReason ?: "offline/no server"}")
                }

                // 🆕 v2.4.0 (FIX-SSBE-006): Visibility-aware Termination
                // Zeigt Fehler im Banner wenn der Sync promoted wurde (silent=false)
                val gateErrorMessage = if (gateResult.isBlockedByWifiOnly) {
                    applicationContext.getString(R.string.sync_wifi_only_error)
                } else {
                    gateResult.blockReason
                }
                SyncStateManager.errorIfVisible(gateErrorMessage)

                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (gate blocked)")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }

                return@withContext Result.success()
            }

            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 5: Checking server reachability (Pre-Check)")
            }

            // ⭐ KRITISCH: Server-Erreichbarkeits-Check VOR Sync
            // Verhindert Fehler-Notifications in fremden WiFi-Netzen
            // Wartet bis Netzwerk bereit ist (DHCP, Routing, Gateway)
            if (!syncService.isServerReachable()) {
                Logger.d(TAG, "⏭️ Server not reachable - skipping sync (no error)")
                Logger.d(TAG, "   Reason: Server offline/wrong network/network not ready/not configured")
                Logger.d(TAG, "   This is normal in foreign WiFi or during network initialization")

                // 🔥 v1.1.2: Check if we should show warning (server unreachable for >24h)
                checkAndShowSyncWarning(syncService)

                // 🆕 v2.4.0 (FIX-SSBE-004): Visibility-aware Termination
                // Wenn der Sync promoted wurde (User hat Pull-to-Refresh gemacht),
                // zeige den Fehler im Banner. Sonst: silent reset.
                SyncStateManager.errorIfVisible(
                    applicationContext.getString(R.string.snackbar_server_unreachable)
                )

                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (silent skip)")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }

                // Success zurückgeben (kein Fehler, Server ist halt nicht erreichbar)
                return@withContext Result.success()
            }

            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 6: Server reachable - proceeding with sync")
                Logger.d(TAG, "    SyncService: $syncService")
            }

            // Try-catch um syncNotes
            val result = try {
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "    Calling syncService.syncNotes()...")
                }
                syncService.syncNotes().also {
                    if (BuildConfig.DEBUG) {
                        Logger.d(TAG, "    ✅ syncNotes() returned")
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "💥 CRASH in syncNotes()!", e)
                Logger.e(TAG, "Exception: ${e.javaClass.name}: ${e.message}")
                throw e
            }

            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 7: Processing result")
                Logger.d(
                    TAG,
                    "📦 Sync result: success=${result.isSuccess}, " +
                        "count=${result.syncedCount}, error=${result.errorMessage}"
                )
            }

            if (result.isSuccess) {
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "📍 Step 8: Success path")
                }
                Logger.i(TAG, "✅ Sync successful: ${result.syncedCount} notes")

                // 🆕 v1.8.1 (IMPL_08): SyncStateManager aktualisieren
                SyncStateManager.markCompleted()

                // Nur Notification zeigen wenn tatsächlich etwas gesynct wurde
                // UND die App nicht im Vordergrund ist (sonst sieht User die Änderungen direkt)
                if (result.syncedCount > 0) {
                    val appInForeground = isAppInForeground()
                    if (appInForeground) {
                        Logger.d(TAG, "ℹ️ App in foreground - skipping notification (UI shows changes)")
                    } else {
                        if (BuildConfig.DEBUG) {
                            Logger.d(TAG, "    Showing success notification...")
                        }
                        NotificationHelper.showSyncSuccess(
                            applicationContext,
                            result.syncedCount
                        )
                    }
                } else {
                    Logger.d(TAG, "ℹ️ No changes to sync - no notification")
                }

                // **UI REFRESH**: SyncEventBus für ComposeMainActivity
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "    Broadcasting sync completed...")
                }
                SyncEventBus.emit(SyncEvent.SyncCompleted(success = true, count = result.syncedCount))

                // 🆕 v1.8.0: Alle Widgets aktualisieren nach Sync
                dev.dettmer.simplenotes.widget.WidgetUpdateHelper.refreshAllNoteWidgets(applicationContext)

                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                Result.success()
            } else {
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "📍 Step 8: Failure path")
                }
                Logger.e(TAG, "❌ Sync failed: ${result.errorMessage}")

                // 🆕 v1.8.1 (IMPL_08): SyncStateManager aktualisieren
                SyncStateManager.markError(result.errorMessage)

                NotificationHelper.showSyncError(
                    applicationContext,
                    result.errorMessage ?: "Unbekannter Fehler"
                )

                // Notify UI auch bei Fehler
                SyncEventBus.emit(SyncEvent.SyncCompleted(success = false, count = 0))

                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "❌ SyncWorker.doWork() FAILURE")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                Result.failure()
            }
        } catch (e: CancellationException) {
            // 🛡️ v1.8.2 (IMPL_14): State reset — verhindert "Sync already in progress" Deadlock
            SyncStateManager.reset()

            // 🆕 v1.10.0-P2: Detailed stop reason logging + quota/standby tracking
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                logCancellationStopReason(stopReason)
            } else {
                Logger.d(TAG, "⏹️ Job was cancelled (normal - update/doze/constraints)")
            }
            Logger.d(TAG, "   This is expected Android behavior - not an error!")

            try {
                // UI-Refresh trotzdem triggern (falls ComposeMainActivity geöffnet)
                SyncEventBus.emit(SyncEvent.SyncCompleted(success = false, count = 0))
            } catch (broadcastError: Exception) {
                Logger.e(TAG, "Failed to emit SyncEvent after cancellation", broadcastError)
            }

            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (cancelled, no error)")
                Logger.d(TAG, "═══════════════════════════════════════")
            }

            // ⚠️ Cancellation ist KEIN Fehler → kein markError(), kein Error-Banner
            // Result.success() damit WorkManager kein exponentielles Backoff auslöst
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "═══════════════════════════════════════")
            }
            Logger.e(TAG, "💥💥💥 FATAL EXCEPTION in doWork() 💥💥💥")
            Logger.e(TAG, "Exception type: ${e.javaClass.name}")
            Logger.e(TAG, "Exception message: ${e.message}")
            Logger.e(TAG, "Stack trace:", e)

            // 🆕 v1.8.2: State cleanup — verhindert "Sync already in progress" Deadlock
            SyncStateManager.markError(e.message)

            try {
                NotificationHelper.showSyncError(
                    applicationContext,
                    e.message ?: "Unknown error"
                )
            } catch (notifError: Exception) {
                Logger.e(TAG, "Failed to show error notification", notifError)
            }

            try {
                SyncEventBus.emit(SyncEvent.SyncCompleted(success = false, count = 0))
            } catch (broadcastError: Exception) {
                Logger.e(TAG, "Failed to emit SyncEvent", broadcastError)
            }

            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "═══════════════════════════════════════")
            }
            Result.failure()
        }
    }

    /**
     * 🆕 v1.10.0-P2: Maps WorkManager stopReason code to a human-readable name and tracks
     * quota/standby stops for user-facing notification on next app resume.
     *
     * Extracted to keep doWork() within cyclomatic complexity limits.
     */
    @androidx.annotation.RequiresApi(31)
    private fun logCancellationStopReason(reason: Int) {
        val reasonName = when (reason) {
            0 -> "NOT_STOPPED"
            1 -> "CANCELLED_BY_APP"
            2 -> "PREEMPTED"
            3 -> "TIMEOUT"
            4 -> "DEVICE_STATE"
            5 -> "CONSTRAINT_BATTERY_NOT_LOW"
            6 -> "CONSTRAINT_CHARGING"
            7 -> "CONSTRAINT_CONNECTIVITY"
            8 -> "CONSTRAINT_DEVICE_IDLE"
            9 -> "CONSTRAINT_STORAGE_NOT_LOW"
            STOP_REASON_QUOTA -> "QUOTA"
            STOP_REASON_BACKGROUND_RESTRICTION -> "BACKGROUND_RESTRICTION"
            STOP_REASON_APP_STANDBY -> "APP_STANDBY"
            13 -> "ESTIMATED_APP_LAUNCH_TIME_CHANGED"
            14 -> "SYSTEM_PROCESSING"
            15 -> "FOREGROUND_SERVICE_TIMEOUT"
            else -> "UNKNOWN($reason)"
        }
        Logger.d(TAG, "⏹️ Job was cancelled — stop reason: $reasonName (code: $reason)")

        if (reason == STOP_REASON_QUOTA ||
            reason == STOP_REASON_BACKGROUND_RESTRICTION ||
            reason == STOP_REASON_APP_STANDBY
        ) {
            SyncStateManager.recordQuotaStop(reasonName)
        }
    }

    /**
     * Prüft ob Server längere Zeit unreachable und zeigt ggf. Warnung (v1.1.2)
     * - Nur wenn Auto-Sync aktiviert
     * - Nur wenn schon mal erfolgreich gesynct
     * - Nur wenn >24h seit letztem erfolgreichen Sync
     * - Throttling: Max. 1 Warnung pro 24h
     */
    private fun checkAndShowSyncWarning(syncService: WebDavSyncService) {
        try {
            val prefs = applicationContext.getSharedPreferences(
                dev.dettmer.simplenotes.utils.Constants.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )

            // Check 1: Auto-Sync aktiviert?
            val autoSyncEnabled = prefs.getBoolean(
                dev.dettmer.simplenotes.utils.Constants.KEY_AUTO_SYNC,
                false
            )
            if (!autoSyncEnabled) {
                Logger.d(TAG, "⏭️ Auto-Sync disabled - no warning needed")
                return
            }

            // Check 2: Schon mal erfolgreich gesynct?
            val lastSuccessfulSync = syncService.getLastSuccessfulSyncTimestamp()
            if (lastSuccessfulSync == 0L) {
                Logger.d(TAG, "⏭️ Never synced successfully - no warning needed")
                return
            }

            // Check 3: >24h seit letztem erfolgreichen Sync?
            val now = System.currentTimeMillis()
            val timeSinceLastSync = now - lastSuccessfulSync
            if (timeSinceLastSync < dev.dettmer.simplenotes.utils.Constants.SYNC_WARNING_THRESHOLD_MS) {
                Logger.d(TAG, "⏭️ Last successful sync <24h ago - no warning needed")
                return
            }

            // Check 4: Throttling - schon Warnung in letzten 24h gezeigt?
            val lastWarningShown = prefs.getLong(
                dev.dettmer.simplenotes.utils.Constants.KEY_LAST_SYNC_WARNING_SHOWN,
                0L
            )
            if (now - lastWarningShown < dev.dettmer.simplenotes.utils.Constants.SYNC_WARNING_THRESHOLD_MS) {
                Logger.d(TAG, "⏭️ Warning already shown in last 24h - throttling")
                return
            }

            // Zeige Warnung
            val hoursSinceLastSync = timeSinceLastSync / (1000 * 60 * 60)
            NotificationHelper.showSyncWarning(applicationContext, hoursSinceLastSync)

            // Speichere Zeitpunkt der Warnung
            prefs.edit { putLong(dev.dettmer.simplenotes.utils.Constants.KEY_LAST_SYNC_WARNING_SHOWN, now) }

            Logger.d(TAG, "⚠️ Sync warning shown: Server unreachable for ${hoursSinceLastSync}h")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check/show sync warning", e)
        }
    }
}
