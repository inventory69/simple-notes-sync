package dev.dettmer.simplenotes.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.dettmer.simplenotes.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Progress Notification zeigen
        val notificationId = NotificationHelper.showSyncProgressNotification(applicationContext)
        
        return@withContext try {
            val syncService = WebDavSyncService(applicationContext)
            val syncResult = syncService.syncNotes()
            
            // Progress Notification entfernen
            NotificationHelper.dismissNotification(applicationContext, notificationId)
            
            when {
                syncResult.hasConflicts -> {
                    // Konflikt-Notification
                    NotificationHelper.showConflictNotification(
                        applicationContext,
                        syncResult.conflictCount
                    )
                    Result.success()
                }
                syncResult.isSuccess -> {
                    // Erfolgs-Notification
                    NotificationHelper.showSyncSuccessNotification(
                        applicationContext,
                        syncResult.syncedCount
                    )
                    Result.success()
                }
                else -> {
                    // Fehler-Notification
                    NotificationHelper.showSyncFailureNotification(
                        applicationContext,
                        syncResult.errorMessage ?: "Unbekannter Fehler"
                    )
                    Result.retry()
                }
            }
            
        } catch (e: Exception) {
            // Fehler-Notification
            NotificationHelper.dismissNotification(applicationContext, notificationId)
            NotificationHelper.showSyncFailureNotification(
                applicationContext,
                e.message ?: "Sync fehlgeschlagen"
            )
            
            // Retry mit Backoff
            Result.retry()
        }
    }
}
