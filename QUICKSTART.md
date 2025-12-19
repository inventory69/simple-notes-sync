# 🚀 Quick Start Guide

## ✅ Server ist bereits gestartet!

Der WebDAV-Server läuft bereits auf:
- **Lokal:** `http://localhost:8080/`
- **Im Netzwerk:** `http://192.168.0.188:8080/`

### Credentials
- **Username:** `noteuser`
- **Password:** `SimpleNotes2025!`

## 📱 Nächste Schritte: Android App erstellen

### Option 1: Mit Android Studio (Empfohlen)

1. **Android Studio öffnen**
   ```
   File → New → New Project
   ```

2. **Template wählen:**
   - Empty Views Activity

3. **Projekt konfigurieren:**
   ```
   Name: Simple Notes
   Package: com.example.simplenotes
   Save location: /home/liq/gitProjects/simple-notes-sync/android/
   Language: Kotlin
   Minimum SDK: API 24 (Android 7.0)
   Build configuration: Kotlin DSL
   ```

4. **Dependencies hinzufügen:**
   
   Öffne `app/build.gradle.kts` und füge hinzu:
   ```kotlin
   dependencies {
       // ... existing dependencies
       
       // WebDAV
       implementation("com.github.thegrizzlylabs:sardine-android:0.8")
       
       // Coroutines
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
       
       // JSON
       implementation("com.google.code.gson:gson:2.10.1")
       
       // WorkManager
       implementation("androidx.work:work-runtime-ktx:2.9.0")
   }
   ```
   
   Und in `settings.gradle.kts`:
   ```kotlin
   dependencyResolutionManagement {
       repositories {
           google()
           mavenCentral()
           maven { url = uri("https://jitpack.io") }  // Für Sardine
       }
   }
   ```

5. **Code implementieren:**
   
   Alle Code-Beispiele findest du in:
   - [ANDROID_GUIDE.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md)
   
   Kopiere der Reihe nach:
   - `models/Note.kt`
   - `models/SyncStatus.kt`
   - `storage/NotesStorage.kt`
   - `utils/DeviceIdGenerator.kt`
   - `utils/NotificationHelper.kt`
   - `utils/Extensions.kt`
   - `utils/Constants.kt`
   - UI Layouts aus dem Guide
   - Activities (MainActivity, NoteEditorActivity, SettingsActivity)
   - `sync/WebDavSyncService.kt`
   - `sync/WifiSyncReceiver.kt`
   - `sync/SyncWorker.kt`
   - `adapters/NotesAdapter.kt`

6. **AndroidManifest.xml anpassen:**
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
   
   <application
       ...
       android:usesCleartextTraffic="true">
   ```

7. **Build & Run:**
   ```
   Build → Make Project
   Run → Run 'app'
   ```

8. **In der App konfigurieren:**
   - Einstellungen öffnen
   - Server URL: `http://192.168.0.188:8080/`
   - Username: `noteuser`
   - Password: `SimpleNotes2025!`
   - Heim-WLAN SSID: `DeinWLANName`
   - "Verbindung testen" → sollte erfolgreich sein ✓

### Option 2: Schritt-für-Schritt Implementation

Folge dem [IMPLEMENTATION_PLAN.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/IMPLEMENTATION_PLAN.md) mit 6 Sprints:

1. **Sprint 1:** Server & Foundation (bereits done ✓)
2. **Sprint 2:** Basic UI (4-6h)
3. **Sprint 3:** Settings & WebDAV (6h)
4. **Sprint 4:** Auto-Sync (6h)
5. **Sprint 5:** Conflicts & Errors (6h)
6. **Sprint 6:** Polish & Testing (6h)

## 🧪 Server testen

Der Server läuft bereits. Teste ihn:

```bash
# Einfacher Test
curl -u noteuser:SimpleNotes2025! http://localhost:8080/

# Test-Notiz hochladen
echo '{"id":"test-123","title":"Test","content":"Hello World","createdAt":1703001234567,"updatedAt":1703001234567,"deviceId":"test","syncStatus":"SYNCED"}' > test.json

curl -u noteuser:SimpleNotes2025! \
  -T test.json \
  http://localhost:8080/test.json

# Test-Notiz abrufen
curl -u noteuser:SimpleNotes2025! http://localhost:8080/test.json

# Löschen
curl -u noteuser:SimpleNotes2025! \
  -X DELETE \
  http://localhost:8080/test.json
```

## 📊 Server Management

```bash
cd /home/liq/gitProjects/simple-notes-sync/server

# Status
docker-compose ps

# Logs
docker-compose logs -f

# Stoppen
docker-compose down

# Neu starten
docker-compose up -d

# Daten ansehen
ls -la notes-data/
```

## 🔧 Troubleshooting

### Server nicht erreichbar von Android

1. **Firewall prüfen:**
   ```bash
   sudo ufw status
   sudo ufw allow 8080
   ```

2. **Ping-Test:**
   ```bash
   ping 192.168.0.188
   ```

3. **Port-Test:**
   ```bash
   telnet 192.168.0.188 8080
   ```

### Permission Denied in Android

- Android 13+: POST_NOTIFICATIONS Permission akzeptieren
- Internet Permission in Manifest vorhanden?
- `usesCleartextTraffic="true"` gesetzt?

## 📚 Weitere Hilfe

- **Vollständige Doku:** [project-docs/simple-notes-sync](https://github.com/inventory69/project-docs/tree/main/simple-notes-sync)
- **Android Code:** [ANDROID_GUIDE.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md)
- **Server Setup:** [SERVER_SETUP.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/SERVER_SETUP.md)
- **Notifications:** [NOTIFICATIONS.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/NOTIFICATIONS.md)

---

**Server Status:** ✅ Running on `http://192.168.0.188:8080/`
**Next:** Android App in Android Studio erstellen
**Estimated Time:** 18-24 Stunden für vollständige App

Viel Erfolg! 🚀
