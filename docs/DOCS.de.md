# Simple Notes Sync - Technische Dokumentation

Diese Datei enthält detaillierte technische Informationen über die Implementierung, Architektur und erweiterte Funktionen.

**🌍 Sprachen:** **Deutsch** · [English](DOCS.md)

---

## 📐 Architektur

### Gesamtübersicht

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

### Android App Architektur

```
app/
├── models/
│   ├── Note.kt              # Data class für Notizen
│   └── SyncStatus.kt        # Sync-Status Enum
├── storage/
│   └── NotesStorage.kt      # Lokale JSON-Datei Speicherung
├── sync/
│   ├── WebDavSyncService.kt # WebDAV Sync-Logik
│   ├── NetworkMonitor.kt    # WLAN-Erkennung
│   ├── SyncWorker.kt        # WorkManager Background Worker
│   └── BootReceiver.kt      # Device Reboot Handler
├── adapters/
│   └── NotesAdapter.kt      # RecyclerView Adapter
├── utils/
│   ├── Constants.kt         # App-Konstanten
│   ├── NotificationHelper.kt# Notification Management
│   └── Logger.kt            # Debug/Release Logging
└── activities/
    ├── MainActivity.kt      # Hauptansicht mit Liste
    ├── NoteEditorActivity.kt# Editor für Notizen
    └── SettingsActivity.kt  # Server-Konfiguration
```

---

## 🔄 Auto-Sync Implementierung

### WorkManager Periodic Task

Der Auto-Sync basiert auf **WorkManager** mit folgender Konfiguration:

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.UNMETERED)  // Nur WiFi
    .build()

val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
    30, TimeUnit.MINUTES,  // Alle 30 Minuten
    10, TimeUnit.MINUTES   // Flex interval
)
    .setConstraints(constraints)
    .build()
```

**Warum WorkManager?**
- ✅ Läuft auch wenn App geschlossen ist
- ✅ Automatischer Restart nach Device Reboot
- ✅ Battery-efficient (Android managed)
- ✅ Garantierte Ausführung bei erfüllten Constraints

### Network Detection

Wir verwenden **Gateway IP Comparison** um zu prüfen, ob der Server erreichbar ist:

```kotlin
fun isInHomeNetwork(): Boolean {
    val gatewayIP = getGatewayIP()         // z.B. 192.168.0.1
    val serverIP = extractIPFromUrl(serverUrl)  // z.B. 192.168.0.188
    
    return isSameNetwork(gatewayIP, serverIP)  // Prüft /24 Netzwerk
}
```

**Vorteile:**
- ✅ Keine Location Permissions nötig
- ✅ Funktioniert mit allen Android Versionen
- ✅ Zuverlässig und schnell

### Sync Flow

```
1. WorkManager wacht auf (alle 30 Min)
   ↓
2. Check: WiFi connected?
   ↓
3. Check: Same network as server?
   ↓
4. Load local notes
   ↓
5. Upload neue/geänderte Notes → Server
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

## � Sync-Trigger Übersicht

Die App verwendet **4 verschiedene Sync-Trigger** mit unterschiedlichen Anwendungsfällen:

| Trigger | Datei | Funktion | Wann? | Pre-Check? |
|---------|-------|----------|-------|------------|
| **1. Manueller Sync** | `MainActivity.kt` | `triggerManualSync()` | User klickt auf Sync-Button im Menü | ✅ Ja |
| **2. Auto-Sync (onResume)** | `MainActivity.kt` | `triggerAutoSync()` | App wird geöffnet/fortgesetzt | ✅ Ja |
| **3. Hintergrund-Sync (Periodic)** | `SyncWorker.kt` | `doWork()` | Alle 15/30/60 Minuten (konfigurierbar) | ✅ Ja |
| **4. WiFi-Connect Sync** | `NetworkMonitor.kt` → `SyncWorker.kt` | `triggerWifiConnectSync()` | WiFi verbunden | ✅ Ja |

### Server-Erreichbarkeits-Check (Pre-Check)

**Alle 4 Sync-Trigger** verwenden vor dem eigentlichen Sync einen **Pre-Check**:

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

**Warum Socket-Check statt HTTP-Request?**
- ⚡ **Schneller:** Socket-Connect ist instant, HTTP-Request dauert länger
- 🔋 **Akkuschonender:** Kein HTTP-Overhead (Headers, TLS Handshake, etc.)
- 🎯 **Präziser:** Prüft nur Netzwerk-Erreichbarkeit, nicht Server-Logik
- 🛡️ **Verhindert Fehler:** Erkennt fremde WiFi-Netze bevor Sync-Fehler entsteht

**Wann schlägt der Check fehl?**
- ❌ Server offline/nicht erreichbar
- ❌ Falsches WiFi-Netzwerk (z.B. öffentliches Café-WiFi)
- ❌ Netzwerk noch nicht bereit (DHCP/Routing-Delay nach WiFi-Connect)
- ❌ VPN blockiert Server-Zugriff
- ❌ Keine WebDAV-Server-URL konfiguriert

### Sync-Verhalten nach Trigger-Typ

| Trigger | Bei Server nicht erreichbar | Bei erfolgreichem Sync | Throttling |
|---------|----------------------------|----------------------|------------|
| Manueller Sync | Toast: "Server nicht erreichbar" | Toast: "✅ Gesynct: X Notizen" | Keins |
| Auto-Sync (onResume) | Silent abort (kein Toast) | Toast: "✅ Gesynct: X Notizen" | Max. 1x/Min |
| Hintergrund-Sync | Silent abort (kein Toast) | Silent (LocalBroadcast only) | 15/30/60 Min |
| WiFi-Connect Sync | Silent abort (kein Toast) | Silent (LocalBroadcast only) | WiFi-basiert |

---

## 🔋 Akku-Optimierung

### v1.6.0: Konfigurierbare Sync-Trigger

Seit v1.6.0 kann jeder Sync-Trigger einzeln aktiviert/deaktiviert werden. Das gibt Nutzern feine Kontrolle über den Akkuverbrauch.

#### Sync-Trigger Übersicht

| Trigger | Standard | Akku-Impact | Beschreibung |
|---------|----------|-------------|--------------|
| **Manueller Sync** | Immer an | 0 (nutzer-getriggert) | Toolbar-Button / Pull-to-Refresh |
| **onSave Sync** | ✅ AN | ~0.5 mAh/Speichern | Sync sofort nach Speichern einer Notiz |
| **onResume Sync** | ✅ AN | ~0.3 mAh/Öffnen | Sync beim App-Öffnen (60s Throttle) |
| **WiFi-Connect** | ✅ AN | ~0.5 mAh/Verbindung | Sync bei WiFi-Verbindung |
| **Periodic Sync** | ❌ AUS | 0.2-0.8%/Tag | Hintergrund-Sync alle 15/30/60 Min |
| **Boot Sync** | ❌ AUS | ~0.1 mAh/Boot | Start Hintergrund-Sync nach Neustart |

#### Akku-Verbrauchsberechnung

**Typisches Nutzungsszenario (Standardeinstellungen):**
- onSave: ~5 Speichern/Tag × 0.5 mAh = **~2.5 mAh**
- onResume: ~10 Öffnen/Tag × 0.3 mAh = **~3 mAh**
- WiFi-Connect: ~2 Verbindungen/Tag × 0.5 mAh = **~1 mAh**
- **Gesamt: ~6.5 mAh/Tag (~0.2% bei 3000mAh Akku)**

**Mit aktiviertem Periodic Sync (15/30/60 Min):**

| Intervall | Syncs/Tag | Akku/Tag | Gesamt (mit Standards) |
|-----------|-----------|----------|------------------------|
| **15 Min** | ~96 | ~23 mAh | ~30 mAh (~1.0%) |
| **30 Min** | ~48 | ~12 mAh | ~19 mAh (~0.6%) |
| **60 Min** | ~24 | ~6 mAh | ~13 mAh (~0.4%) |

#### Komponenten-Aufschlüsselung

| Komponente | Frequenz | Verbrauch | Details |
|------------|----------|-----------|---------|
| WorkManager Wakeup | Pro Sync | ~0.15 mAh | System wacht auf |
| Network Check | Pro Sync | ~0.03 mAh | Gateway IP Check |
| WebDAV Sync | Nur bei Änderungen | ~0.25 mAh | HTTP PUT/GET |
| **Pro-Sync Gesamt** | - | **~0.25 mAh** | Optimiert |

### Optimierungen

1. **Pre-Checks vor Sync**
   ```kotlin
   // Reihenfolge wichtig! Günstigste Checks zuerst
   if (!hasUnsyncedChanges()) return  // Lokaler Check (günstig)
   if (!isServerReachable()) return   // Netzwerk Check (teuer)
   performSync()                       // Nur wenn beide bestehen
   ```

2. **Throttling**
   - onResume: 60 Sekunden Mindestabstand
   - onSave: 5 Sekunden Mindestabstand
   - Periodic: 15/30/60 Minuten Intervalle

3. **IP Caching**
   ```kotlin
   private var cachedServerIP: String? = null
   // DNS lookup nur 1x beim Start, nicht bei jedem Check
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
   - Nur WiFi (nicht mobile Daten)
   - Nur wenn Server erreichbar
   - Keine permanenten Listeners

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
            // Neue Note vom Server
            storage.saveNote(remoteNote)
            downloadedCount++
        } else if (localNote.modifiedAt < remoteNote.modifiedAt) {
            // Server hat neuere Version
            storage.saveNote(remoteNote)
            downloadedCount++
        } else if (localNote.modifiedAt > remoteNote.modifiedAt) {
            // Lokale Version ist neuer → Conflict
            resolveConflict(localNote, remoteNote)
            conflictCount++
        }
    }
    
    return DownloadResult(downloadedCount, conflictCount)
}
```

### Conflict Resolution

Strategie: **Last-Write-Wins** mit **Conflict Copy**

```kotlin
fun resolveConflict(local: Note, remote: Note) {
    // Remote Note umbenennen (Conflict Copy)
    val conflictNote = remote.copy(
        id = "${remote.id}_conflict_${System.currentTimeMillis()}",
        title = "${remote.title} (Konflikt)"
    )
    
    storage.saveNote(conflictNote)
    
    // Lokale Note bleibt
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
    "Notizen Synchronisierung",
    NotificationManager.IMPORTANCE_DEFAULT
)
```

### Success Notification

```kotlin
fun showSyncSuccess(context: Context, count: Int) {
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAGS)
    
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("Sync erfolgreich")
        .setContentText("$count Notizen synchronisiert")
        .setContentIntent(pendingIntent)  // Click öffnet App
        .setAutoCancel(true)              // Dismiss on click
        .build()
    
    notificationManager.notify(NOTIFICATION_ID, notification)
}
```

---

## 🛡️ Permissions

Die App benötigt **minimale Permissions**:

```xml
<!-- Netzwerk -->
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

**Keine Location Permissions!**  
Wir verwenden Gateway IP Comparison statt SSID-Erkennung. Keine Standortberechtigung nötig.

---

## 🧪 Testing

### Server testen

```bash
# WebDAV Server erreichbar?
curl -u noteuser:password http://192.168.0.188:8080/

# Datei hochladen
echo '{"test":"data"}' > test.json
curl -u noteuser:password -T test.json http://192.168.0.188:8080/test.json

# Datei herunterladen
curl -u noteuser:password http://192.168.0.188:8080/test.json
```

### Android App testen

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

- [ ] Notiz erstellen → in Liste sichtbar
- [ ] Notiz bearbeiten → Änderungen gespeichert
- [ ] Notiz löschen → aus Liste entfernt
- [ ] Manueller Sync → Server Status "Erreichbar"
- [ ] Auto-Sync → Notification nach ~30 Min
- [ ] App schließen → Auto-Sync funktioniert weiter
- [ ] Device Reboot → Auto-Sync startet automatisch
- [ ] Server offline → Error Notification
- [ ] Notification Click → App öffnet sich

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

### Signieren (für Distribution)

```bash
# Keystore erstellen
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias

# APK signieren
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore my-release-key.jks \
  app-release-unsigned.apk my-alias

# Optimieren
zipalign -v 4 app-release-unsigned.apk app-release.apk
```

---

## 🐛 Debugging

### LogCat Filter

```bash
# Nur App-Logs
adb logcat -s SimpleNotesApp NetworkMonitor SyncWorker WebDavSyncService

# Mit Timestamps
adb logcat -v time -s SyncWorker

# In Datei speichern
adb logcat -s SyncWorker > sync_debug.log
```

### Common Issues

**Problem: Auto-Sync funktioniert nicht**
```
Lösung: Akku-Optimierung deaktivieren
Settings → Apps → Simple Notes → Battery → Don't optimize
```

**Problem: Server nicht erreichbar**
```
Check: 
1. Server läuft? → docker compose ps
2. IP korrekt? → ip addr show
3. Port offen? → telnet 192.168.0.188 8080
4. Firewall? → sudo ufw allow 8080
```

**Problem: Notifications kommen nicht**
```
Check:
1. Notification Permission erteilt?
2. Do Not Disturb aktiv?
3. App im Background? → Force stop & restart
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

Siehe [UPCOMING.de.md](UPCOMING.de.md) für die vollständige Roadmap und geplante Features.

---

## 📖 Weitere Dokumentation

- [Project Docs](https://github.com/inventory69/project-docs/tree/main/simple-notes-sync)
- [Sync Architecture](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/SYNC_ARCHITECTURE.md) - **Detaillierte Sync-Trigger Dokumentation**
- [Android Guide](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md)
- [Bugfix Documentation](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/BUGFIX_SYNC_SPAM_AND_NOTIFICATIONS.md)

---

**Letzte Aktualisierung:** Februar 2026
