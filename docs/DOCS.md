# Simple Notes Sync - Technical Documentation

This file contains detailed technical information about implementation, architecture, and advanced features.

**🌍 Languages:** [Deutsch](DOCS.de.md) · **English**

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
│   ├── WebDavSyncService.kt # Sync facade (delegates to modules)
│   ├── SyncGateChecker.kt   # Pre-sync validation
│   ├── ETagCache.kt         # E-Tag caching
│   ├── SyncTimestampManager.kt # Timestamp tracking
│   ├── ConnectionManager.kt # HTTP connection lifecycle
│   ├── NoteUploader.kt      # Upload logic
│   ├── NoteDownloader.kt    # Download logic
│   ├── MarkdownSyncManager.kt # Markdown bidirectional sync
│   ├── NetworkMonitor.kt    # WiFi detection
│   ├── SyncWorker.kt        # WorkManager background worker
│   └── BootReceiver.kt      # Device reboot handler
├── ui/
│   ├── main/                # Main screen (Compose)
│   ├── editor/              # Note editor (Compose)
│   ├── settings/            # Settings screens (Compose)
│   └── widget/              # Homescreen widgets (Glance)
└── utils/
    ├── Constants.kt         # App constants
    ├── NotificationHelper.kt# Notification management
    └── Logger.kt            # Debug/release logging
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

We use **Gateway IP Comparison** to check if the server is reachable:

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

## 🔄 Sync Trigger Overview

The app uses **4 different sync triggers** with different use cases:

| Trigger | File | Function | When? | Pre-Check? |
|---------|------|----------|-------|------------|
| **1. Manual Sync** | `ComposeMainActivity` | `triggerManualSync()` | User clicks sync button in menu | ✅ Yes |
| **2. Auto-Sync (onResume)** | `ComposeMainActivity` | `triggerAutoSync()` | App opened/resumed | ✅ Yes |
| **3. Background Sync (Periodic)** | `SyncWorker.kt` | `doWork()` | Every 15/30/60 minutes (configurable) | ✅ Yes |
| **4. WiFi-Connect Sync** | `NetworkMonitor.kt` → `SyncWorker.kt` | `triggerWifiConnectSync()` | WiFi connected | ✅ Yes |

### Server Reachability Check (Pre-Check)

**All 4 sync triggers** use a **pre-check** before the actual sync:

```kotlin
// WebDavSyncService.kt - isServerReachable()
suspend fun isServerReachable(): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2000)  // 2s Timeout
        }
        true
    } catch (e: Exception) {
        Logger.d(TAG, "Server not reachable: ${e.message}")
        false
    }
}
```

**Why Socket Check instead of HTTP Request?**
- ⚡ **Faster:** Socket connect is instant, HTTP request takes longer
- 🔋 **Battery Efficient:** No HTTP overhead (headers, TLS handshake, etc.)
- 🎯 **More Precise:** Only checks network reachability, not server logic
- 🛡️ **Prevents Errors:** Detects foreign WiFi networks before sync error occurs

**When does the check fail?**
- ❌ Server offline/unreachable
- ❌ Wrong WiFi network (e.g. public café WiFi)
- ❌ Network not ready yet (DHCP/routing delay after WiFi connect)
- ❌ VPN blocks server access
- ❌ No WebDAV server URL configured

### Sync Behavior by Trigger Type

| Trigger | When server not reachable | On successful sync | Throttling |
|---------|--------------------------|-------------------|------------|
| Manual Sync | Toast: "Server not reachable" | Toast: "✅ Synced: X notes" | None |
| Auto-Sync (onResume) | Silent abort (no toast) | Toast: "✅ Synced: X notes" | Max. 1x/min |
| Background Sync | Silent abort (no toast) | Silent (SharedFlow only) | 15/30/60 min |
| WiFi-Connect Sync | Silent abort (no toast) | Silent (SharedFlow only) | WiFi-based |

---

## 🔋 Battery Optimization

### v1.6.0: Configurable Sync Triggers

Since v1.6.0, each sync trigger can be individually enabled/disabled. This gives users fine-grained control over battery usage.

#### Sync Trigger Overview

| Trigger | Default | Battery Impact | Description |
|---------|---------|----------------|-------------|
| **Manual Sync** | Always on | 0 (user-triggered) | Toolbar button / Pull-to-refresh |
| **onSave Sync** | ✅ ON | ~0.5 mAh/save | Sync immediately after saving a note |
| **onResume Sync** | ✅ ON | ~0.3 mAh/resume | Sync when app is opened (60s throttle) |
| **WiFi-Connect** | ✅ ON | ~0.5 mAh/connect | Sync when WiFi is connected |
| **Periodic Sync** | ❌ OFF | 0.2-0.8%/day | Background sync every 15/30/60 min |
| **Boot Sync** | ❌ OFF | ~0.1 mAh/boot | Start background sync after reboot |

#### Battery Usage Calculation

**Typical usage scenario (defaults):**
- onSave: ~5 saves/day × 0.5 mAh = **~2.5 mAh**
- onResume: ~10 opens/day × 0.3 mAh = **~3 mAh**
- WiFi-Connect: ~2 connects/day × 0.5 mAh = **~1 mAh**
- **Total: ~6.5 mAh/day (~0.2% on 3000mAh battery)**

**With Periodic Sync enabled (15/30/60 min):**

| Interval | Syncs/day | Battery/day | Total (with defaults) |
|----------|-----------|-------------|----------------------|
| **15 min** | ~96 | ~23 mAh | ~30 mAh (~1.0%) |
| **30 min** | ~48 | ~12 mAh | ~19 mAh (~0.6%) |
| **60 min** | ~24 | ~6 mAh | ~13 mAh (~0.4%) |

#### Component Breakdown

| Component | Frequency | Usage | Details |
|-----------|-----------|-------|---------|
| WorkManager Wakeup | Per sync | ~0.15 mAh | System wakes up |
| Network Check | Per sync | ~0.03 mAh | Gateway IP check |
| WebDAV Sync | Only if changes | ~0.25 mAh | HTTP PUT/GET |
| **Per-Sync Total** | - | **~0.25 mAh** | Optimized |

### Optimizations

1. **Pre-Checks before Sync**
   ```kotlin
   // Order matters! Cheapest checks first
   if (!hasUnsyncedChanges()) return  // Local check (cheap)
   if (!isServerReachable()) return   // Network check (expensive)
   performSync()                       // Only if both pass
   ```

2. **Throttling**
   - onResume: 60 second minimum interval
   - onSave: 5 second minimum interval
   - Periodic: 15/30/60 minute intervals

3. **IP Caching**
   ```kotlin
   private var cachedServerIP: String? = null
   // DNS lookup only once at start, not every check
   ```

4. **Conditional Logging**
   ```kotlin
   object Logger {
       fun d(tag: String, msg: String) {
           if (BuildConfig.DEBUG) Log.d(tag, msg)
       }
   }
   ```

5. **Network Constraints**
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
We use Gateway IP Comparison instead of SSID detection. No location permission required.

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
1. Server running? → docker compose ps
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

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3

// WorkManager
androidx.work:work-runtime-ktx:2.9.0

// WebDAV Client
com.github.thegrizzlylabs:sardine-android:0.8
```

---

## 🔮 Roadmap

See [UPCOMING.md](UPCOMING.md) for the full roadmap and planned features.

---

## 📖 Further Documentation

- [Project Docs](https://github.com/inventory69/project-docs/tree/main/simple-notes-sync)
- [Sync Architecture](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/SYNC_ARCHITECTURE.md) - **Detailed Sync Trigger Documentation**
- [Android Guide](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md)
- [Bugfix Documentation](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/BUGFIX_SYNC_SPAM_AND_NOTIFICATIONS.md)

---

**Last updated:** February 2026
