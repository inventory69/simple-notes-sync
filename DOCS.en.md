# Simple Notes Sync - Technical Documentation

This file contains detailed technical information about implementation, architecture, and advanced features.

**🌍 Languages:** [Deutsch](DOCS.md) · **English**

---

## 📐 Architecture

### Overall Overview

```
┌─────────────────┐
│  Android App    │
│  (Kotlin)       │
└────────┬────────┘
         │ WebDAV/HTTP
         │
┌────────▼────────┐
│  WebDAV Server  │
│  (Docker)       │
└─────────────────┘
```

### Android App Architecture

```
app/
├── models/
│   ├── Note.kt              # Data class for notes
│   └── SyncStatus.kt        # Sync status enum
├── storage/
│   └── NotesStorage.kt      # Local JSON file storage
├── sync/
│   ├── WebDavSyncService.kt # WebDAV sync logic
│   ├── NetworkMonitor.kt    # WiFi detection
│   ├── SyncWorker.kt        # WorkManager background worker
│   └── BootReceiver.kt      # Device reboot handler
├── adapters/
│   └── NotesAdapter.kt      # RecyclerView adapter
├── utils/
│   ├── Constants.kt         # App constants
│   ├── NotificationHelper.kt# Notification management
│   └── Logger.kt            # Debug/release logging
└── activities/
    ├── MainActivity.kt      # Main view with list
    ├── NoteEditorActivity.kt# Note editor
    └── SettingsActivity.kt  # Server configuration
```

---

## 🔄 Auto-Sync Implementation

### WorkManager Periodic Task

Auto-sync is based on **WorkManager** with the following configuration:

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.UNMETERED)  // WiFi only
    .build()

val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
    30, TimeUnit.MINUTES,  // Every 30 minutes
    10, TimeUnit.MINUTES   // Flex interval
)
    .setConstraints(constraints)
    .build()
```

**Why WorkManager?**
- ✅ Runs even when app is closed
- ✅ Automatic restart after device reboot
- ✅ Battery-efficient (Android managed)
- ✅ Guaranteed execution when constraints are met

### Network Detection

Instead of SSID-based detection (Android 13+ privacy issues), we use **Gateway IP Comparison**:

```kotlin
fun isInHomeNetwork(): Boolean {
    val gatewayIP = getGatewayIP()         // e.g. 192.168.0.1
    val serverIP = extractIPFromUrl(serverUrl)  // e.g. 192.168.0.188
    
    return isSameNetwork(gatewayIP, serverIP)  // Checks /24 network
}
```

**Advantages:**
- ✅ No location permissions needed
- ✅ Works with all Android versions
- ✅ Reliable and fast

### Sync Flow

```
1. WorkManager wakes up (every 30 min)
   ↓
2. Check: WiFi connected?
   ↓
3. Check: Same network as server?
   ↓
4. Load local notes
   ↓
5. Upload new/changed notes → Server
   ↓
6. Download remote notes ← Server
   ↓
7. Merge & resolve conflicts
   ↓
8. Update local storage
   ↓
9. Show notification (if changes)
```

---

## 🔋 Battery Optimization

### Usage Analysis

| Component | Frequency | Usage | Details |
|------------|----------|-----------|---------|
| WorkManager Wakeup | Every 30 min | ~0.15 mAh | System wakes up |
| Network Check | 48x/day | ~0.03 mAh | Gateway IP check |
| WebDAV Sync | 2-3x/day | ~1.5 mAh | Only when changes |
| **Total** | - | **~12 mAh/day** | **~0.4%** at 3000mAh |

### Optimizations

1. **IP Caching**
   ```kotlin
   private var cachedServerIP: String? = null
   // DNS lookup only once at start, not every check
   ```

2. **Throttling**
   ```kotlin
   private var lastSyncTime = 0L
   private const val MIN_SYNC_INTERVAL_MS = 60_000L  // Max 1 sync/min
   ```

3. **Conditional Logging**
   ```kotlin
   object Logger {
       fun d(tag: String, msg: String) {
           if (BuildConfig.DEBUG) Log.d(tag, msg)
       }
   }
   ```

4. **Network Constraints**
   - WiFi only (not mobile data)
   - Only when server is reachable
   - No permanent listeners

---

## 📦 WebDAV Sync Details

### Upload Flow

```kotlin
suspend fun uploadNotes(): Int {
    val localNotes = storage.loadAllNotes()
    var uploadedCount = 0
    
    for (note in localNotes) {
        if (note.syncStatus == SyncStatus.PENDING) {
            val jsonContent = note.toJson()
            val remotePath = "$serverUrl/${note.id}.json"
            
            sardine.put(remotePath, jsonContent.toByteArray())
            
            note.syncStatus = SyncStatus.SYNCED
            storage.saveNote(note)
            uploadedCount++
        }
    }
    
    return uploadedCount
}
```

### Download Flow

```kotlin
suspend fun downloadNotes(): DownloadResult {
    val remoteFiles = sardine.list(serverUrl)
    var downloadedCount = 0
    var conflictCount = 0
    
    for (file in remoteFiles) {
        if (!file.name.endsWith(".json")) continue
        
        val content = sardine.get(file.href)
        val remoteNote = Note.fromJson(content)
        val localNote = storage.loadNote(remoteNote.id)
        
        if (localNote == null) {
            // New note from server
            storage.saveNote(remoteNote)
            downloadedCount++
        } else if (localNote.modifiedAt < remoteNote.modifiedAt) {
            // Server has newer version
            storage.saveNote(remoteNote)
            downloadedCount++
        } else if (localNote.modifiedAt > remoteNote.modifiedAt) {
            // Local version is newer → Conflict
            resolveConflict(localNote, remoteNote)
            conflictCount++
        }
    }
    
    return DownloadResult(downloadedCount, conflictCount)
}
```

### Conflict Resolution

Strategy: **Last-Write-Wins** with **Conflict Copy**

```kotlin
fun resolveConflict(local: Note, remote: Note) {
    // Rename remote note (conflict copy)
    val conflictNote = remote.copy(
        id = "${remote.id}_conflict_${System.currentTimeMillis()}",
        title = "${remote.title} (Conflict)"
    )
    
    storage.saveNote(conflictNote)
    
    // Local note remains
    local.syncStatus = SyncStatus.SYNCED
    storage.saveNote(local)
}
```

---

## 🔔 Notifications

### Notification Channels

```kotlin
val channel = NotificationChannel(
    "notes_sync_channel",
    "Notes Synchronization",
    NotificationManager.IMPORTANCE_DEFAULT
)
```

### Success Notification

```kotlin
fun showSyncSuccess(context: Context, count: Int) {
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAGS)
    
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("Sync successful")
        .setContentText("$count notes synchronized")
        .setContentIntent(pendingIntent)  // Click opens app
        .setAutoCancel(true)              // Dismiss on click
        .build()
    
    notificationManager.notify(NOTIFICATION_ID, notification)
}
```

---

## 🛡️ Permissions

The app requires **minimal permissions**:

```xml
<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Boot Receiver -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Battery Optimization (optional) -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

**No Location Permissions!**  
Earlier versions required `ACCESS_FINE_LOCATION` for SSID detection. Now we use Gateway IP Comparison.

---

## 🧪 Testing

### Test Server

```bash
# WebDAV server reachable?
curl -u noteuser:password http://192.168.0.188:8080/

# Upload file
echo '{"test":"data"}' > test.json
curl -u noteuser:password -T test.json http://192.168.0.188:8080/test.json

# Download file
curl -u noteuser:password http://192.168.0.188:8080/test.json
```

### Test Android App

**Unit Tests:**
```bash
cd android
./gradlew test
```

**Instrumented Tests:**
```bash
./gradlew connectedAndroidTest
```

**Manual Testing Checklist:**

- [ ] Create note → visible in list
- [ ] Edit note → changes saved
- [ ] Delete note → removed from list
- [ ] Manual sync → server status "Reachable"
- [ ] Auto-sync → notification after ~30 min
- [ ] Close app → auto-sync continues
- [ ] Device reboot → auto-sync starts automatically
- [ ] Server offline → error notification
- [ ] Notification click → app opens

---

## 🚀 Build & Deployment

### Debug Build

```bash
cd android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Sign (for Distribution)

```bash
# Create keystore
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias

# Sign APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore my-release-key.jks \
  app-release-unsigned.apk my-alias

# Optimize
zipalign -v 4 app-release-unsigned.apk app-release.apk
```

---

## 🐛 Debugging

### LogCat Filter

```bash
# Only app logs
adb logcat -s SimpleNotesApp NetworkMonitor SyncWorker WebDavSyncService

# With timestamps
adb logcat -v time -s SyncWorker

# Save to file
adb logcat -s SyncWorker > sync_debug.log
```

### Common Issues

**Problem: Auto-sync not working**
```
Solution: Disable battery optimization
Settings → Apps → Simple Notes → Battery → Don't optimize
```

**Problem: Server not reachable**
```
Check: 
1. Server running? → docker-compose ps
2. IP correct? → ip addr show
3. Port open? → telnet 192.168.0.188 8080
4. Firewall? → sudo ufw allow 8080
```

**Problem: Notifications not appearing**
```
Check:
1. Notification permission granted?
2. Do Not Disturb active?
3. App in background? → Force stop & restart
```

---

## 📚 Dependencies

```gradle
// Core
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1
com.google.android.material:material:1.11.0

// Lifecycle
androidx.lifecycle:lifecycle-runtime-ktx:2.7.0

// RecyclerView
androidx.recyclerview:recyclerview:1.3.2

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3

// WorkManager
androidx.work:work-runtime-ktx:2.9.0

// WebDAV Client
com.github.thegrizzlylabs:sardine-android:0.8

// Broadcast (deprecated but working)
androidx.localbroadcastmanager:localbroadcastmanager:1.1.0
```

---

## 🔮 Roadmap

### v1.1
- [ ] Search & Filter
- [ ] Dark Mode
- [ ] Tags/Categories
- [ ] Markdown Preview

### v2.0
- [ ] Desktop Client (Flutter)
- [ ] End-to-End Encryption
- [ ] Shared Notes (Collaboration)
- [ ] Attachment Support

---

## 📖 Further Documentation

- [Project Docs](https://github.com/inventory69/project-docs/tree/main/simple-notes-sync)
- [Android Guide](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md)
- [Bugfix Documentation](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/BUGFIX_SYNC_SPAM_AND_NOTIFICATIONS.md)

---

**Last updated:** December 21, 2025
