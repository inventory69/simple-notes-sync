package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        android.util.Log.d(TAG, "═══════════════════════════════════════")
        android.util.Log.d(TAG, "🔄 SyncWorker.doWork() ENTRY")
        android.util.Log.d(TAG, "Context: ${applicationContext.javaClass.simpleName}")
        android.util.Log.d(TAG, "Thread: ${Thread.currentThread().name}")
        android.util.Log.d(TAG, "RunAttempt: $runAttemptCount")
        
        return@withContext try {
            android.util.Log.d(TAG, "📍 Step 1: Before WebDavSyncService creation")
            
            // Try-catch um Service-Creation
            val syncService = try {
                android.util.Log.d(TAG, "    Creating WebDavSyncService with applicationContext...")
                WebDavSyncService(applicationContext).also {
                    android.util.Log.d(TAG, "    ✅ WebDavSyncService created successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "� CRASH in WebDavSyncService constructor!", e)
                android.util.Log.e(TAG, "Exception: ${e.javaClass.name}: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            android.util.Log.d(TAG, "� Step 2: Before syncNotes() call")
            android.util.Log.d(TAG, "    SyncService: $syncService")
            
            // Try-catch um syncNotes
            val result = try {
                android.util.Log.d(TAG, "    Calling syncService.syncNotes()...")
                syncService.syncNotes().also {
                    android.util.Log.d(TAG, "    ✅ syncNotes() returned")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 CRASH in syncNotes()!", e)
                android.util.Log.e(TAG, "Exception: ${e.javaClass.name}: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            android.util.Log.d(TAG, "📍 Step 3: Processing result")
            android.util.Log.d(TAG, "📍 Step 3: Processing result")
            Logger.d(TAG, "📦 Sync result: success=${result.isSuccess}, count=${result.syncedCount}, error=${result.errorMessage}")
            
            if (result.isSuccess) {
                android.util.Log.d(TAG, "📍 Step 4: Success path")
                Logger.d(TAG, "✅ Sync successful: ${result.syncedCount} notes")
                
                // Nur Notification zeigen wenn tatsächlich etwas gesynct wurde
                if (result.syncedCount > 0) {
                    android.util.Log.d(TAG, "    Showing success notification...")
                    NotificationHelper.showSyncSuccess(
                        applicationContext,
                        result.syncedCount
                    )
                } else {
                    Logger.d(TAG, "ℹ️ No changes to sync - no notification")
                }
                
                // **UI REFRESH**: Broadcast für MainActivity
                android.util.Log.d(TAG, "    Broadcasting sync completed...")
                broadcastSyncCompleted(true, result.syncedCount)
                
                android.util.Log.d(TAG, "✅ SyncWorker.doWork() SUCCESS")
                android.util.Log.d(TAG, "═══════════════════════════════════════")
                Result.success()
            } else {
                android.util.Log.d(TAG, "📍 Step 4: Failure path")
                Logger.e(TAG, "❌ Sync failed: ${result.errorMessage}")
                NotificationHelper.showSyncError(
                    applicationContext,
                    result.errorMessage ?: "Unbekannter Fehler"
                )
                
                // Broadcast auch bei Fehler (damit UI refresht)
                broadcastSyncCompleted(false, 0)
                
                android.util.Log.d(TAG, "❌ SyncWorker.doWork() FAILURE")
                android.util.Log.d(TAG, "═══════════════════════════════════════")
                Result.failure()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "═══════════════════════════════════════")
            android.util.Log.e(TAG, "💥💥💥 FATAL EXCEPTION in doWork() 💥💥💥")
            android.util.Log.e(TAG, "Exception type: ${e.javaClass.name}")
            android.util.Log.e(TAG, "Exception message: ${e.message}")
            android.util.Log.e(TAG, "Stack trace:")
            e.printStackTrace()
            
            Logger.e(TAG, "💥 Sync exception: ${e.message}", e)
            Logger.e(TAG, "Exception type: ${e.javaClass.name}")
            Logger.e(TAG, "Stack trace:", e)
            
            try {
                NotificationHelper.showSyncError(
                    applicationContext,
                    e.message ?: "Unknown error"
                )
            } catch (notifError: Exception) {
                android.util.Log.e(TAG, "Failed to show error notification", notifError)
            }
            
            try {
                broadcastSyncCompleted(false, 0)
            } catch (broadcastError: Exception) {
                android.util.Log.e(TAG, "Failed to broadcast", broadcastError)
            }
            
            android.util.Log.e(TAG, "═══════════════════════════════════════")
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
