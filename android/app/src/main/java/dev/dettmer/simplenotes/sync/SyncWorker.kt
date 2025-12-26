package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NotificationHelper
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
                Logger.d(TAG, "📍 Step 2: Checking server reachability (Pre-Check)")
            }
            
            // ⭐ KRITISCH: Server-Erreichbarkeits-Check VOR Sync
            // Verhindert Fehler-Notifications in fremden WiFi-Netzen
            // Wartet bis Netzwerk bereit ist (DHCP, Routing, Gateway)
            if (!syncService.isServerReachable()) {
                Logger.d(TAG, "⏭️ Server not reachable - skipping sync (no error)")
                Logger.d(TAG, "   Reason: Server offline/wrong network/network not ready/not configured")
                Logger.d(TAG, "   This is normal in foreign WiFi or during network initialization")
                
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
                Logger.d(TAG, "📦 Sync result: success=${result.isSuccess}, count=${result.syncedCount}, error=${result.errorMessage}")
            }
            
            if (result.isSuccess) {
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG, "📍 Step 5: Success path")
                }
                Logger.i(TAG, "✅ Sync successful: ${result.syncedCount} notes")
                
                // Nur Notification zeigen wenn tatsächlich etwas gesynct wurde
                if (result.syncedCount > 0) {
                    if (BuildConfig.DEBUG) {
                        Logger.d(TAG, "    Showing success notification...")
                    }
                    NotificationHelper.showSyncSuccess(
                        applicationContext,
                        result.syncedCount
                    )
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
    private fun broadcastSyncCompleted(success: Boolean, count: Int) {
        val intent = Intent(ACTION_SYNC_COMPLETED).apply {
            putExtra("success", success)
            putExtra("count", count)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        Logger.d(TAG, "📡 Broadcast sent: success=$success, count=$count")
    }
}
