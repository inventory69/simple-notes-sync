# Settings-Redesign Plan (v1.5.0)

## 📋 Übersicht

Die aktuelle Settings-Activity hat 857 Zeilen XML und ist unübersichtlich geworden.
Ziel: Moderne, gruppierte Settings nach Material Design 3 Richtlinien.

---

## 🔍 Aktuelle Struktur (v1.4.0)

### Vorhandene Cards (6 Stück)
1. **Server-Konfiguration** (~200 Zeilen)
   - Protokoll-Auswahl (HTTP/HTTPS)
   - Server-URL
   - Username/Password
   - Verbindung testen

2. **Sync-Einstellungen** (~160 Zeilen)
   - Auto-Sync Toggle
   - Sync-Intervall (15/30/60 Min)
   - Jetzt synchronisieren Button

3. **Markdown-Integration** (~100 Zeilen)
   - Markdown Auto-Sync Toggle
   - Manueller Markdown-Sync Button

4. **Backup & Wiederherstellung** (~100 Zeilen)
   - Backup erstellen
   - Aus Datei wiederherstellen
   - Vom Server wiederherstellen

5. **Debug/Entwickler** (~100 Zeilen)
   - File Logging Toggle
   - Logs exportieren
   - Logs löschen

6. **Über die App** (~100 Zeilen)
   - Version
   - GitHub-Links
   - Lizenz

---

## 🎯 Neue Struktur (v1.5.0)

### Ansatz: PreferenceFragmentCompat + Material 3

Anstatt XML-Cards mit manuellen Views verwenden wir das moderne Preference-System:

```
┌─────────────────────────────────────────┐
│ ⚙️ Einstellungen                        │
├─────────────────────────────────────────┤
│                                         │
│ 🔄 SYNCHRONISATION                      │
│ ├─ Server-Verbindung          →         │
│ ├─ Auto-Sync                  🔘        │
│ └─ Sync-Intervall             30 Min    │
│                                         │
│ 📝 NOTIZEN                              │
│ └─ Markdown-Export            🔘        │
│                                         │
│ 💾 DATEN                                │
│ ├─ Backup erstellen           →         │
│ ├─ Wiederherstellen           →         │
│ └─ Vom Server laden           →         │
│                                         │
│ 🔧 ERWEITERT                            │
│ ├─ Datei-Logging              🔘        │
│ └─ Logs verwalten             →         │
│                                         │
│ ℹ️ ÜBER                                  │
│ ├─ Version                    1.5.0     │
│ ├─ GitHub Repository          →         │
│ ├─ Entwickler                 →         │
│ └─ Lizenz                     MIT       │
│                                         │
└─────────────────────────────────────────┘
```

---

## 📐 Technische Implementierung

### Option A: PreferenceFragmentCompat (Empfohlen)
**Vorteile:**
- Native Android Preference-System
- Automatische State-Verwaltung
- Eingebaute Material 3 Styles
- Hierarchische Navigation (Sub-Screens)
- Weniger Code (~300 Zeilen statt 1148)

**Dateien:**
```
res/xml/
├── preferences_root.xml      # Hauptmenü mit Kategorien
├── preferences_sync.xml      # Server-Konfiguration
├── preferences_data.xml      # Backup-Optionen
└── preferences_debug.xml     # Entwickler-Optionen

SettingsActivity.kt           # Wird zu SettingsFragment.kt
```

### Option B: Jetpack Compose (Zukunftssicher)
**Vorteile:**
- Modernste UI-Technologie
- Deklarativer Code
- Hot Reload während Entwicklung
- Besser für komplexe Custom-UI

**Nachteile:**
- Größere Migration
- Mischung mit View-System komplizierter
- Lernkurve

### Empfehlung: **Option A** für v1.5.0
PreferenceFragmentCompat ist der richtige Mittelweg:
- Schnell zu implementieren (~1-2 Tage)
- Native Material 3 Unterstützung
- Etabliertes Pattern
- Compose-Migration kann später erfolgen (v2.0.0)

---

## 🎨 Design-Prinzipien (Material 3)

### 1. Preference Categories
```xml
<PreferenceCategory
    app:title="Synchronisation"
    app:iconSpaceReserved="false">
```

### 2. Switch Preferences
```xml
<SwitchPreferenceCompat
    app:key="auto_sync"
    app:title="Auto-Sync"
    app:summary="Automatisch bei WLAN-Verbindung"
    app:icon="@drawable/ic_sync" />
```

### 3. Navigations-Preferences (→ Sub-Screen)
```xml
<Preference
    app:key="server_config"
    app:title="Server-Verbindung"
    app:summary="192.168.0.188:8080"
    app:fragment="dev.dettmer.simplenotes.settings.ServerSettingsFragment" />
```

### 4. List Preferences (Dropdown)
```xml
<ListPreference
    app:key="sync_interval"
    app:title="Sync-Intervall"
    app:entries="@array/sync_interval_entries"
    app:entryValues="@array/sync_interval_values"
    app:defaultValue="30" />
```

---

## 📁 Neue Dateistruktur

```
app/src/main/java/dev/dettmer/simplenotes/
├── settings/
│   ├── SettingsFragment.kt           # Haupt-Preference-Fragment
│   ├── ServerSettingsFragment.kt     # Server-Konfiguration
│   ├── BackupSettingsFragment.kt     # Backup/Restore Dialoge
│   └── DebugSettingsFragment.kt      # Logging & Logs

app/src/main/res/xml/
├── preferences_root.xml
├── preferences_server.xml
├── preferences_backup.xml
└── preferences_debug.xml
```

---

## ✅ Implementierungs-Checklist

### Phase 1: Grundstruktur
- [ ] `preferences_root.xml` erstellen
- [ ] `SettingsFragment.kt` mit PreferenceFragmentCompat
- [ ] SettingsActivity als Container anpassen
- [ ] Kategorien: Sync, Notizen, Daten, Erweitert, Über

### Phase 2: Server-Konfiguration
- [ ] `preferences_server.xml` für Server-Details
- [ ] `ServerSettingsFragment.kt` mit Custom-Dialogen
- [ ] Verbindungstest-Button als Preference-Action
- [ ] Protocol-Auswahl als ListPreference

### Phase 3: Sync & Markdown
- [ ] Auto-Sync als SwitchPreference
- [ ] Sync-Intervall als ListPreference
- [ ] Markdown-Export als SwitchPreference
- [ ] "Jetzt synchronisieren" als Action-Preference

### Phase 4: Backup & Debug
- [ ] Backup-Aktionen als Preferences
- [ ] Logging-Toggle
- [ ] Log-Export/Clear Aktionen

### Phase 5: Über & Polish
- [ ] Version, Links, Lizenz
- [ ] Icons für alle Kategorien
- [ ] Animationen & Transitions
- [ ] Dark Mode Testing

---

## ⏱️ Zeitschätzung

| Phase | Aufwand |
|-------|---------|
| Phase 1: Grundstruktur | 2-3h |
| Phase 2: Server | 3-4h |
| Phase 3: Sync | 2h |
| Phase 4: Backup/Debug | 2h |
| Phase 5: Polish | 2h |
| **Gesamt** | **~12h** |

---

## 🔗 Referenzen

- [Material 3 Settings](https://m3.material.io/components/lists/overview)
- [AndroidX Preference](https://developer.android.com/develop/ui/views/components/settings)
- [Preference Styling](https://developer.android.com/develop/ui/views/components/settings/organize-your-settings)
