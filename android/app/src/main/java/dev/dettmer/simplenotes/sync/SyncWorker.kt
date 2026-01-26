@file:Suppress("DEPRECATION") // LocalBroadcastManager deprecated but functional, will migrate in v2.0.0

package dev.dettmer.simplenotes.sync

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "SyncWorker"
        const val ACTION_SYNC_COMPLETED = "dev.dettmer.simplenotes.SYNC_COMPLETED"
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
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
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
                Logger.d(TAG, "📍 Step 2: Checking for unsynced changes (Performance Pre-Check)")
            }
            
            // 🔥 v1.1.2: Performance-Optimierung - Skip Sync wenn keine lokalen Änderungen
            // Spart Batterie + Netzwerk-Traffic + Server-Last
            if (!syncService.hasUnsyncedChanges()) {
                Logger.d(TAG, "⏭️ No local changes - skipping sync (performance optimization)")
                Logger.d(TAG, "   Saves battery, network traffic, and server load")
                
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (no changes to sync)")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                
                return@withContext Result.success()
            }
            
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 2.5: Checking sync gate (canSync)")
            }
            
            // 🆕 v1.7.0: Zentrale Sync-Gate Prüfung (WiFi-Only, Offline Mode, Server Config)
            val gateResult = syncService.canSync()
            if (!gateResult.canSync) {
                if (gateResult.isBlockedByWifiOnly) {
                    Logger.d(TAG, "⏭️ WiFi-only mode enabled, but not on WiFi - skipping sync")
                } else {
                    Logger.d(TAG, "⏭️ Sync blocked by gate: ${gateResult.blockReason ?: "offline/no server"}")
                }
                
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (gate blocked)")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                
                return@withContext Result.success()
            }
            
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 3: Checking server reachability (Pre-Check)")
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
                
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (silent skip)")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                
                // Success zurückgeben (kein Fehler, Server ist halt nicht erreichbar)
                return@withContext Result.success()
            }
            
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "📍 Step 3: Server reachable - proceeding with sync")
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
                Logger.d(TAG, "📍 Step 4: Processing result")
                Logger.d(
                    TAG,
                    "📦 Sync result: success=${result.isSuccess}, " +
                        "count=${result.syncedCount}, error=${result.errorMessage}"
                )
            }
            
            if (result.isSuccess) {
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "📍 Step 5: Success path")
                }
                Logger.i(TAG, "✅ Sync successful: ${result.syncedCount} notes")
                
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
                
                // **UI REFRESH**: Broadcast für MainActivity
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "    Broadcasting sync completed...")
                }
                broadcastSyncCompleted(true, result.syncedCount)
                
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                Result.success()
            } else {
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "📍 Step 5: Failure path")
                }
                Logger.e(TAG, "❌ Sync failed: ${result.errorMessage}")
                NotificationHelper.showSyncError(
                    applicationContext,
                    result.errorMessage ?: "Unbekannter Fehler"
                )
                
                // Broadcast auch bei Fehler (damit UI refresht)
                broadcastSyncCompleted(false, 0)
                
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "❌ SyncWorker.doWork() FAILURE")
                    Logger.d(TAG, "═══════════════════════════════════════")
                }
                Result.failure()
            }
        } catch (e: CancellationException) {
            // ⭐ Job wurde gecancelt - KEIN FEHLER!
            // Gründe: App-Update, Doze Mode, Battery Optimization, Network Constraint, etc.
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "═══════════════════════════════════════")
            }
            Logger.d(TAG, "⏹️ Job was cancelled (normal - update/doze/constraints)")
            Logger.d(TAG, "   Reason could be: App update, Doze mode, Battery opt, Network disconnect")
            Logger.d(TAG, "   This is expected Android behavior - not an error!")
            
            try {
                // UI-Refresh trotzdem triggern (falls MainActivity geöffnet)
                broadcastSyncCompleted(false, 0)
            } catch (broadcastError: Exception) {
                Logger.e(TAG, "Failed to broadcast after cancellation", broadcastError)
            }
            
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "✅ SyncWorker.doWork() SUCCESS (cancelled, no error)")
                Logger.d(TAG, "═══════════════════════════════════════")
            }
            
            // ⚠️ WICHTIG: Result.success() zurückgeben!
            // Cancellation ist KEIN Fehler, WorkManager soll nicht retries machen
            Result.success()
            
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "═══════════════════════════════════════")
            }
            Logger.e(TAG, "💥💥💥 FATAL EXCEPTION in doWork() 💥💥💥")
            Logger.e(TAG, "Exception type: ${e.javaClass.name}")
            Logger.e(TAG, "Exception message: ${e.message}")
            Logger.e(TAG, "Stack trace:", e)
            
            try {
                NotificationHelper.showSyncError(
                    applicationContext,
                    e.message ?: "Unknown error"
                )
            } catch (notifError: Exception) {
                Logger.e(TAG, "Failed to show error notification", notifError)
            }
            
            try {
                broadcastSyncCompleted(false, 0)
            } catch (broadcastError: Exception) {
                Logger.e(TAG, "Failed to broadcast", broadcastError)
            }
            
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "═══════════════════════════════════════")
            }
            Result.failure()
        }
    }
    
    /**
     * Sendet Broadcast an MainActivity für UI Refresh
     */
    @Suppress("DEPRECATION") // LocalBroadcastManager deprecated but still functional, will migrate in v2.0.0
    private fun broadcastSyncCompleted(success: Boolean, count: Int) {
        val intent = Intent(ACTION_SYNC_COMPLETED).apply {
            putExtra("success", success)
            putExtra("count", count)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        Logger.d(TAG, "📡 Broadcast sent: success=$success, count=$count")
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
            prefs.edit()
                .putLong(dev.dettmer.simplenotes.utils.Constants.KEY_LAST_SYNC_WARNING_SHOWN, now)
                .apply()
            
            Logger.d(TAG, "⚠️ Sync warning shown: Server unreachable for ${hoursSinceLastSync}h")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check/show sync warning", e)
        }
    }
}
