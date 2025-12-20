package dev.dettmer.simplenotes.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.dettmer.simplenotes.MainActivity

object NotificationHelper {
    
    private const val CHANNEL_ID = "notes_sync_channel"
    private const val CHANNEL_NAME = "Notizen Synchronisierung"
    private const val CHANNEL_DESCRIPTION = "Benachrichtigungen über Sync-Status"
    private const val NOTIFICATION_ID = 1001
    private const val SYNC_NOTIFICATION_ID = 2
    
    /**
     * Erstellt Notification Channel (Android 8.0+)
     * Muss beim App-Start aufgerufen werden
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
                as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Zeigt Erfolgs-Notification nach Sync
     */
    fun showSyncSuccessNotification(context: Context, syncedCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("Sync erfolgreich")
            .setContentText("$syncedCount Notiz(en) synchronisiert")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notify(NOTIFICATION_ID, notification)
                }
            } else {
                notify(NOTIFICATION_ID, notification)
            }
        }
    }
    
    /**
     * Zeigt Fehler-Notification bei fehlgeschlagenem Sync
     */
    fun showSyncFailureNotification(context: Context, errorMessage: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Sync fehlgeschlagen")
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(errorMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notify(NOTIFICATION_ID, notification)
                }
            } else {
                notify(NOTIFICATION_ID, notification)
            }
        }
    }
    
    /**
     * Zeigt Progress-Notification während Sync läuft
     */
    fun showSyncProgressNotification(context: Context): Int {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Synchronisiere...")
            .setContentText("Notizen werden synchronisiert")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notify(NOTIFICATION_ID, notification)
                }
            } else {
                notify(NOTIFICATION_ID, notification)
            }
        }
        
        return NOTIFICATION_ID
    }
    
    /**
     * Zeigt Notification bei erkanntem Konflikt
     */
    fun showConflictNotification(context: Context, conflictCount: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sync-Konflikt erkannt")
            .setContentText("$conflictCount Notiz(en) haben Konflikte")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notify(NOTIFICATION_ID + 1, notification)
                }
            } else {
                notify(NOTIFICATION_ID + 1, notification)
            }
        }
    }
    
    /**
     * Entfernt aktive Notification
     */
    fun dismissNotification(context: Context, notificationId: Int = NOTIFICATION_ID) {
        with(NotificationManagerCompat.from(context)) {
            cancel(notificationId)
        }
    }
    
    /**
     * Prüft ob Notification-Permission vorhanden (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * Zeigt Notification dass Sync startet
     */
    fun showSyncInProgress(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Synchronisierung läuft")
            .setContentText("Notizen werden synchronisiert...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        manager.notify(SYNC_NOTIFICATION_ID, notification)
    }
    
    /**
     * Zeigt Erfolgs-Notification
     */
    fun showSyncSuccess(context: Context, count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Sync erfolgreich")
            .setContentText("$count Notizen synchronisiert")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        manager.notify(SYNC_NOTIFICATION_ID, notification)
    }
    
    /**
     * Zeigt Fehler-Notification
     */
    fun showSyncError(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Sync Fehler")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        manager.notify(SYNC_NOTIFICATION_ID, notification)
    }
}
