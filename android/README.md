# Simple Notes - Android App

## 🚧 Development Setup

### Voraussetzungen

- Android Studio Hedgehog (2023.1.1) oder neuer
- JDK 17
- Android SDK 34
- Min SDK 24

### Projekt in Android Studio öffnen

```bash
# In Android Studio:
# File → New → New Project
# Template: Empty Views Activity
# 
# Settings:
# Name: Simple Notes
# Package: com.example.simplenotes
# Save location: /home/liq/gitProjects/simple-notes-sync/android/
# Language: Kotlin
# Minimum SDK: API 24
# Build configuration: Kotlin DSL
```

### Dependencies

Siehe `ANDROID_GUIDE.md` in project-docs für vollständige `build.gradle.kts`:

**Hauptabhängigkeiten:**
- Sardine Android (WebDAV Client)
- Kotlin Coroutines
- Gson (JSON)
- WorkManager (Background Sync)
- Material Design Components

### Projektstruktur

```
android/
└── app/
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/example/simplenotes/
            │   ├── MainActivity.kt
            │   ├── NoteEditorActivity.kt
            │   ├── SettingsActivity.kt
            │   ├── models/
            │   │   ├── Note.kt
            │   │   └── SyncStatus.kt
            │   ├── storage/
            │   │   └── NotesStorage.kt
            │   ├── sync/
            │   │   ├── WebDavSyncService.kt
            │   │   ├── WifiSyncReceiver.kt
            │   │   ├── SyncWorker.kt
            │   │   └── ConflictResolver.kt
            │   ├── adapters/
            │   │   └── NotesAdapter.kt
            │   └── utils/
            │       ├── DeviceIdGenerator.kt
            │       ├── NotificationHelper.kt
            │       ├── Extensions.kt
            │       └── Constants.kt
            └── res/
                ├── layout/
                ├── values/
                └── drawable/
```

## 📖 Development Guide

Vollständige Code-Beispiele und Implementation:
- [ANDROID_GUIDE.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md)
- [IMPLEMENTATION_PLAN.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/IMPLEMENTATION_PLAN.md)

## 🏗️ Build

```bash
# Debug Build
./gradlew assembleDebug

# Release Build
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## 🧪 Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## 📝 TODO

- [ ] Projekt in Android Studio erstellen
- [ ] Dependencies einrichten
- [ ] Models implementieren (Note.kt, SyncStatus.kt)
- [ ] Storage Layer (NotesStorage.kt)
- [ ] UI Layouts erstellen
- [ ] Activities implementieren
- [ ] Sync Service (WebDavSyncService.kt)
- [ ] WLAN Detection (WifiSyncReceiver.kt)
- [ ] WorkManager Setup (SyncWorker.kt)
- [ ] Notifications (NotificationHelper.kt)
- [ ] Error Handling
- [ ] Testing

---

**Next Step:** Projekt in Android Studio erstellen und Code aus ANDROID_GUIDE.md übernehmen.
