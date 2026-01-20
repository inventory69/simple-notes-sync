# Geplante Features 🚀

**🌍 Sprachen:** **Deutsch** · [English](UPCOMING.md)

> Was kommt als Nächstes? Hier findest du unsere Pläne für zukünftige Versionen.

---

## v1.5.0 - Jetpack Compose & Internationalisierung ✅

> **Status:** Released 🎉 (Januar 2026)

### 🎨 Jetpack Compose UI

- ✅ **Komplettes UI-Redesign** - Von XML-Views zu Jetpack Compose
- ✅ **Modernisierte Einstellungen** - 7 kategorisierte Screens
- ✅ **Selection Mode** - Long-Press für Mehrfachauswahl
- ✅ **Silent-Sync Mode** - Kein Banner bei Auto-Sync

### 🌍 Mehrsprachigkeit

- ✅ **Englisch + Deutsch** - 400+ übersetzte Strings
- ✅ **Automatische Spracherkennung** - Folgt der System-Sprache
- ✅ **Per-App Language (Android 13+)** - Native Sprachauswahl

### 🎨 UI-Verbesserungen

- ✅ **Splash Screen** - App-Foreground-Icon
- ✅ **App Icon** - In About Screen und Empty State
- ✅ **Slide-Animationen** - Flüssige Übergänge im NoteEditor

---

## v1.6.0 - Technische Modernisierung ✅

> **Status:** Released 🎉 (Januar 2026)

### ⚙️ Konfigurierbare Sync-Trigger

- ✅ **Individuelle Trigger-Kontrolle** - Jeden Sync-Trigger einzeln aktivieren/deaktivieren
- ✅ **Ereignisbasierte Defaults** - onSave, onResume, WiFi-Connect standardmäßig aktiv
- ✅ **Periodischer Sync optional** - 15/30/60 Min Intervalle (Standard: AUS)
- ✅ **Boot Sync optional** - Periodischen Sync nach Geräteneustart starten (Standard: AUS)
- ✅ **Offline-Modus UI** - Ausgegraute Toggles wenn kein Server konfiguriert
- ✅ **Akku-optimiert** - ~0.2%/Tag mit Defaults, bis zu ~1.0% mit Periodic

---

## v1.6.1 - Clean Code ✅

> **Status:** Released 🎉 (Januar 2026)

### 🧹 Code-Qualität

- ✅ **detekt: 0 Issues** - Alle 29 Code-Qualitäts-Issues behoben
- ✅ **Zero Build Warnings** - Alle 21 Deprecation Warnings eliminiert
- ✅ **ktlint reaktiviert** - Mit Compose-spezifischen Regeln
- ✅ **CI/CD Lint-Checks** - In PR Build Workflow integriert
- ✅ **Constants Refactoring** - Dimensions.kt, SyncConstants.kt

---

## v1.7.0 - Staggered Grid Layout

> **Status:** Geplant 📝

### 🎨 Adaptives Layout

- **Staggered Grid** - Pinterest-artiges Layout mit `LazyVerticalStaggeredGrid`
- **Intelligente Größen** - Kleine Notizen (kurzer Text, wenige Checklist-Items) kompakt dargestellt
- **Layout-Umschalter** - Zwischen Listen- und Grid-Ansicht in Einstellungen wechseln
- **Adaptive Spalten** - 2-3 Spalten basierend auf Bildschirmgröße
- **120 FPS optimiert** - Lazy Loading für flüssiges Scrollen bei vielen Notizen

### 🔧 Server-Ordner Prüfung

- **WebDAV Folder Check** - Prüft ob der Ordner auf dem Server existiert und beschreibbar ist
- **Bessere Fehlermeldungen** - Hilfreiche Hinweise bei Server-Problemen
- **Connection-Test Verbesserung** - Prüft Read/Write Permissions

### 🔧 Technische Verbesserungen

- **Code-Refactoring** - LargeClass Komponenten aufteilen (WebDavSyncService, SettingsActivity)
- **Verbesserte Progress-Dialoge** - Material Design 3 konform

---

## v2.0.0 - Legacy Cleanup

> **Status:** Geplant 📝

### 🗑️ Legacy Code Entfernung

- **SettingsActivity entfernen** - Ersetzt durch ComposeSettingsActivity
- **MainActivity entfernen** - Ersetzt durch ComposeMainActivity
- **LocalBroadcastManager → SharedFlow** - Moderne Event-Architektur
- **ProgressDialog → Material Dialog** - Volle Material 3 Konformität
- **AbstractSavedStateViewModelFactory → viewModelFactory** - Moderne ViewModel-Erstellung

---

## 📋 Backlog

> Features für zukünftige Überlegungen

### 🔐 Sicherheits-Verbesserungen

- **Passwortgeschützte lokale Backups** - Backup-ZIP mit Passwort verschlüsseln
- **Biometrische Entsperrung** - Fingerabdruck/Gesichtserkennung für App

### 🎨 UI Features

- **Widget** - Schnellzugriff vom Homescreen
- **Kategorien/Tags** - Notizen organisieren
- **Suche** - Volltextsuche in Notizen

### 🌍 Community

- **Zusätzliche Sprachen** - Community-Übersetzungen (FR, ES, IT, ...)

---

## 💡 Feedback & Wünsche

Hast du eine Idee für ein neues Feature?

- **[Feature Request erstellen](https://github.com/inventory69/simple-notes-sync/issues/new?template=feature_request.yml)**
- **[Bestehende Wünsche ansehen](https://github.com/inventory69/simple-notes-sync/issues?q=is%3Aissue+label%3Aenhancement)**

---

**Hinweis:** Diese Roadmap zeigt unsere aktuellen Pläne. Prioritäten können sich basierend auf Community-Feedback ändern.

[← Zurück zur Dokumentation](DOCS.md)
