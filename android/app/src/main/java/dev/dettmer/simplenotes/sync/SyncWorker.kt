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
        Logger.d(TAG, "🔄 SyncWorker started")
        Logger.d(TAG, "Context: ${applicationContext.javaClass.simpleName}")
        Logger.d(TAG, "Thread: ${Thread.currentThread().name}")
        
        return@withContext try {
            // Start sync (kein "in progress" notification mehr)
            val syncService = WebDavSyncService(applicationContext)
            Logger.d(TAG, "🚀 Starting sync...")
            Logger.d(TAG, "📊 Attempt: ${runAttemptCount}")
            
            val result = syncService.syncNotes()
            
            Logger.d(TAG, "📦 Sync result: success=${result.isSuccess}, count=${result.syncedCount}, error=${result.errorMessage}")
            
            if (result.isSuccess) {
                Logger.d(TAG, "✅ Sync successful: ${result.syncedCount} notes")
                
                // Nur Notification zeigen wenn tatsächlich etwas gesynct wurde
                if (result.syncedCount > 0) {
                    NotificationHelper.showSyncSuccess(
                        applicationContext,
                        result.syncedCount
                    )
                } else {
                    Logger.d(TAG, "ℹ️ No changes to sync - no notification")
                }
                
                // **UI REFRESH**: Broadcast für MainActivity
                broadcastSyncCompleted(true, result.syncedCount)
                
                Result.success()
            } else {
                Logger.e(TAG, "❌ Sync failed: ${result.errorMessage}")
                NotificationHelper.showSyncError(
                    applicationContext,
                    result.errorMessage ?: "Unbekannter Fehler"
                )
                
                // Broadcast auch bei Fehler (damit UI refresht)
                broadcastSyncCompleted(false, 0)
                
                Result.failure()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "💥 Sync exception: ${e.message}", e)
            Logger.e(TAG, "Exception type: ${e.javaClass.name}")
            Logger.e(TAG, "Stack trace:", e)
            NotificationHelper.showSyncError(
                applicationContext,
                e.message ?: "Unknown error"
            )
            
            broadcastSyncCompleted(false, 0)
            
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
