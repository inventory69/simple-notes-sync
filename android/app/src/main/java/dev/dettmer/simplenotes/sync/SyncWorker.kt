package dev.dettmer.simplenotes.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.dettmer.simplenotes.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "SyncWorker"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 SyncWorker started")
        
        return@withContext try {
            // Show notification that sync is starting
            NotificationHelper.showSyncInProgress(applicationContext)
            Log.d(TAG, "📢 Notification shown: Sync in progress")
            
            // Start sync
            val syncService = WebDavSyncService(applicationContext)
            Log.d(TAG, "🚀 Starting sync...")
            
            val result = syncService.syncNotes()
            
            if (result.isSuccess) {
                Log.d(TAG, "✅ Sync successful: ${result.syncedCount} notes")
                NotificationHelper.showSyncSuccess(
                    applicationContext,
                    result.syncedCount
                )
                Result.success()
            } else {
                Log.e(TAG, "❌ Sync failed: ${result.errorMessage}")
                NotificationHelper.showSyncError(
                    applicationContext,
                    result.errorMessage ?: "Unbekannter Fehler"
                )
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Sync exception: ${e.message}", e)
            NotificationHelper.showSyncError(
                applicationContext,
                e.message ?: "Unknown error"
            )
            Result.failure()
        }
    }
}
