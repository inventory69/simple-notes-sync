# Vollständige Feature-Liste 📋

**🌍 Languages:** **Deutsch** · [English](FEATURES.en.md)

> Alle Features von Simple Notes Sync im Detail

---

## 📝 Notiz-Verwaltung

### Basis-Funktionen
- ✅ **Einfache Textnotizen** - Fokus auf Inhalt, keine Ablenkung
- ✅ **Automatisches Speichern** - Kein manuelles Speichern nötig
- ✅ **Titel + Inhalt** - Klare Struktur für jede Notiz
- ✅ **Zeitstempel** - Erstellungs- und Änderungsdatum automatisch
- ✅ **Swipe-to-Delete** - Intuitive Geste zum Löschen
- ✅ **Bestätigungs-Dialog** - Schutz vor versehentlichem Löschen
- ✅ **Material Design 3** - Moderne, saubere UI
- ✅ **Dark Mode** - Automatisch je nach System-Einstellung
- ✅ **Dynamic Colors** - Passt sich deinem Android-Theme an

### Editor
- ✅ **Minimalistischer Editor** - Kein Schnickschnack
- ✅ **Auto-Fokus** - Direkt losschreiben
- ✅ **Vollbild-Modus** - Maximale Schreibfläche
- ✅ **Speichern-Button** - Manuelle Bestätigung möglich
- ✅ **Zurück-Navigation** - Speichert automatisch

---

## 💾 Backup & Wiederherstellung

### Lokales Backup System
- ✅ **JSON-Export** - Alle Notizen in einer Datei
- ✅ **Freie Speicherort-Wahl** - Downloads, SD-Karte, Cloud-Ordner
- ✅ **Dateinamen mit Zeitstempel** - `simplenotes_backup_YYYY-MM-DD_HHmmss.json`
- ✅ **Vollständiger Export** - Titel, Inhalt, Timestamps, IDs
- ✅ **Menschenlesbares Format** - JSON mit Formatierung
- ✅ **Unabhängig vom Server** - Funktioniert komplett offline

### Wiederherstellungs-Modi
- ✅ **Zusammenführen (Merge)** - Neue Notizen hinzufügen, bestehende behalten _(Standard)_
- ✅ **Ersetzen (Replace)** - Alle löschen und Backup importieren
- ✅ **Duplikate überschreiben (Overwrite)** - Backup gewinnt bei ID-Konflikten
- ✅ **Automatisches Sicherheits-Backup** - Vor jeder Wiederherstellung
- ✅ **Backup-Validierung** - Prüft Format und Version
- ✅ **Fehlerbehandlung** - Klare Fehlermeldungen bei Problemen

---

## 🖥️ Desktop-Integration

### Markdown-Export
- ✅ **Automatischer Export** - Jede Notiz → `.md` Datei
- ✅ **Dual-Format** - JSON (Master) + Markdown (Mirror)
- ✅ **Dateinamen-Sanitization** - Sichere Dateinamen aus Titeln
- ✅ **Frontmatter-Metadata** - YAML mit ID, Timestamps, Tags
- ✅ **WebDAV-Sync** - Parallel zum JSON-Sync
- ✅ **Optional** - In Einstellungen ein/ausschaltbar
- ✅ **Initial Export** - Alle bestehenden Notizen beim Aktivieren
- ✅ **Progress-Anzeige** - Zeigt X/Y beim Export

### Markdown-Import
- ✅ **Desktop → App** - Änderungen vom Desktop importieren
- ✅ **Last-Write-Wins** - Konfliktauflösung via Timestamp
- ✅ **Frontmatter-Parsing** - Liest Metadata aus `.md` Dateien
- ✅ **Neue Notizen erkennen** - Automatisch in App übernehmen
- ✅ **Updates erkennen** - Nur wenn Desktop-Version neuer ist
- ✅ **Fehlertoleranz** - Einzelne Fehler brechen Import nicht ab

### WebDAV-Zugriff
- ✅ **Network Drive Mount** - Windows, macOS, Linux
- ✅ **Jeder Markdown-Editor** - VS Code, Typora, Notepad++, iA Writer
- ✅ **Live-Bearbeitung** - Direkter Zugriff auf `.md` Dateien
- ✅ **Ordner-Struktur** - `/notes/` für JSON, `/notes-md/` für Markdown
- ✅ **Automatische Ordner-Erstellung** - Beim ersten Sync

---

## 🔄 Synchronisation

### Auto-Sync
- ✅ **Intervall-Auswahl** - 15, 30 oder 60 Minuten
- ✅ **WLAN-Bindung** - Nur im konfigurierten Heim-WLAN
- ✅ **Akkuschonend** - ~0.2-0.8% pro Tag
- ✅ **Smart Server-Check** - Keine Fehler in fremden Netzwerken
- ✅ **WorkManager** - Zuverlässige Background-Ausführung
- ✅ **Battery-Optimierung kompatibel** - Funktioniert auch mit Doze Mode

### Sync-Trigger (6 Stück)
1. ✅ **Periodic Sync** - Automatisch nach Intervall
2. ✅ **App-Start Sync** - Beim Öffnen der App
3. ✅ **WiFi-Connect Sync** - Wenn Heim-WLAN verbindet
4. ✅ **Manual Sync** - Button in Einstellungen
5. ✅ **Pull-to-Refresh** - Wisch-Geste in Notizliste
6. ✅ **Settings-Save Sync** - Nach Server-Konfiguration

### Sync-Mechanismus
- ✅ **Upload** - Lokale Änderungen zum Server
- ✅ **Download** - Server-Änderungen in App
- ✅ **Konflikt-Erkennung** - Bei gleichzeitigen Änderungen
- ✅ **Konfliktfreies Merging** - Last-Write-Wins via Timestamp
- ✅ **Sync-Status Tracking** - LOCAL_ONLY, PENDING, SYNCED, CONFLICT
- ✅ **Fehlerbehandlung** - Retry bei Netzwerkproblemen
- ✅ **Offline-First** - App funktioniert ohne Server

### Server-Verbindung
- ✅ **WebDAV-Protokoll** - Standard-Protokoll
- ✅ **HTTP/HTTPS** - HTTP nur lokal, HTTPS für extern
- ✅ **Username/Password** - Basic Authentication
- ✅ **Connection Test** - In Einstellungen testen
- ✅ **Gateway SSID** - WLAN-Name für Auto-Sync
- ✅ **Server-URL Normalisierung** - Automatisches `/notes/` und `/notes-md/` _(NEU in v1.2.1)_
- ✅ **Flexible URL-Eingabe** - Beide Varianten funktionieren: `http://server/` und `http://server/notes/`

---

## 🔒 Privacy & Sicherheit

### Self-Hosted
- ✅ **Eigener Server** - Volle Kontrolle über Daten
- ✅ **Keine Cloud** - Keine Drittanbieter
- ✅ **Kein Tracking** - Keine Analytik, keine Telemetrie
- ✅ **Kein Account** - Nur Server-Zugangsdaten
- ✅ **100% Open Source** - MIT Lizenz

### Daten-Sicherheit
- ✅ **Lokale Speicherung** - App-Private Storage (Android)
- ✅ **WebDAV-Verschlüsselung** - HTTPS für externe Server
- ✅ **Passwort-Speicherung** - Android SharedPreferences (verschlüsselt)
- ✅ **Keine Drittanbieter-Libs** - Nur Android SDK + Sardine (WebDAV)

---

## 🔋 Performance & Optimierung

### Akku-Effizienz
- ✅ **Optimierte Sync-Intervalle** - 15/30/60 Min
- ✅ **WLAN-Only** - Kein Mobile Data Sync
- ✅ **Smart Server-Check** - Nur im Heim-WLAN
- ✅ **WorkManager** - System-optimierte Ausführung
- ✅ **Doze Mode kompatibel** - Sync läuft auch im Standby
- ✅ **Gemessener Verbrauch:**
  - 15 Min: ~0.8% / Tag (~23 mAh)
  - 30 Min: ~0.4% / Tag (~12 mAh) ⭐ _Empfohlen_
  - 60 Min: ~0.2% / Tag (~6 mAh)

### App-Performance
- ✅ **Offline-First** - Funktioniert ohne Internet
- ✅ **Instant-Load** - Notizen laden in <100ms
- ✅ **Smooth Scrolling** - RecyclerView mit ViewHolder
- ✅ **Material Design 3** - Native Android UI
- ✅ **Kotlin Coroutines** - Asynchrone Operationen
- ✅ **Minimale APK-Größe** - ~2 MB

---

## 🛠️ Technische Details

### Plattform
- ✅ **Android 8.0+** (API 26+)
- ✅ **Target SDK 36** (Android 15)
- ✅ **Kotlin** - Moderne Programmiersprache
- ✅ **Material Design 3** - Neueste Design-Richtlinien
- ✅ **ViewBinding** - Typ-sichere View-Referenzen

### Architektur
- ✅ **MVVM-Light** - Einfache Architektur
- ✅ **Single Activity** - Moderne Navigation
- ✅ **Kotlin Coroutines** - Async/Await Pattern
- ✅ **Dispatchers.IO** - Background-Operationen
- ✅ **SharedPreferences** - Settings-Speicherung
- ✅ **File-Based Storage** - JSON-Dateien lokal

### Abhängigkeiten
- ✅ **AndroidX** - Jetpack Libraries
- ✅ **Material Components** - Material Design 3
- ✅ **Sardine** - WebDAV Client (com.thegrizzlylabs)
- ✅ **Gson** - JSON Serialization
- ✅ **WorkManager** - Background Tasks
- ✅ **OkHttp** - HTTP Client (via Sardine)

### Build-Varianten
- ✅ **Standard** - Universal APK (100% FOSS, keine Google-Dependencies)
- ✅ **F-Droid** - Identisch mit Standard (100% FOSS)
- ✅ **Debug/Release** - Entwicklung und Production
- ✅ **Keine Google Services** - Komplett FOSS, keine proprietären Bibliotheken

---

## 📦 Server-Kompatibilität

### Getestete WebDAV-Server
- ✅ **Docker WebDAV** (empfohlen für Self-Hosting)
- ✅ **Nextcloud** - Vollständig kompatibel
- ✅ **ownCloud** - Funktioniert einwandfrei
- ✅ **Apache mod_dav** - Standard WebDAV
- ✅ **nginx + WebDAV** - Mit korrekter Konfiguration

### Server-Features
- ✅ **Basic Auth** - Username/Password
- ✅ **Directory Listing** - Für Download
- ✅ **PUT/GET** - Upload/Download
- ✅ **MKCOL** - Ordner erstellen
- ✅ **DELETE** - Notizen löschen (zukünftig)

---

## 🔮 Zukünftige Features

Geplant für kommende Versionen (siehe [TODO.md](project-docs/simple-notes-sync/planning/TODO.md)):

### v1.3.0 - Web Editor & Organisation
- ⏳ **Browser-basierter Editor** - Notizen im Webbrowser bearbeiten
- ⏳ **WebDAV-Zugriff via Browser** - Kein Mount nötig
- ⏳ **Mobile-optimiert** - Responsive Design
- ⏳ **Offline-fähig** - Progressive Web App (PWA)
- ⏳ **Tags/Labels** - Kategorisierung von Notizen
- ⏳ **Suche** - Volltextsuche in allen Notizen
- ⏳ **Sortierung** - Nach Datum, Titel, Tags
- ⏳ **Filter** - Nach Tags filtern

### v1.4.0 - Sharing & Export
- ⏳ **Notiz teilen** - Via Share-Intent
- ⏳ **Einzelne Notiz exportieren** - Als .txt oder .md
- ⏳ **Import von Text** - Via Share-Intent

### v1.5.0 - Erweiterte Editor-Features
- ⏳ **Markdown-Vorschau** - In-App Rendering
- ⏳ **Checklisten** - TODO-Listen in Notizen
- ⏳ **Syntax-Highlighting** - Für Code-Snippets

---

## 📊 Vergleich mit anderen Apps

| Feature | Simple Notes Sync | Google Keep | Nextcloud Notes |
|---------|------------------|-------------|-----------------|
| Offline-First | ✅ | ⚠️ Eingeschränkt | ⚠️ Eingeschränkt |
| Self-Hosted | ✅ | ❌ | ✅ |
| Auto-Sync | ✅ | ✅ | ✅ |
| Markdown-Export | ✅ | ❌ | ✅ |
| Desktop-Zugriff | ✅ (WebDAV) | ✅ (Web) | ✅ (Web + WebDAV) |
| Lokales Backup | ✅ | ❌ | ⚠️ Server-Backup |
| Kein Google-Account | ✅ | ❌ | ✅ |
| Open Source | ✅ MIT | ❌ | ✅ AGPL |
| APK-Größe | ~2 MB | ~50 MB | ~8 MB |
| Akku-Verbrauch | ~0.4%/Tag | ~1-2%/Tag | ~0.5%/Tag |

---

## ❓ FAQ

**Q: Brauche ich einen Server?**  
A: Nein! Die App funktioniert auch komplett offline. Der Server ist optional für Sync.

**Q: Welcher Server ist am besten?**  
A: Für Einstieg: Docker WebDAV (einfach, leicht). Für Profis: Nextcloud (viele Features).

**Q: Funktioniert Markdown-Export ohne Desktop-Integration?**  
A: Nein, du musst das Feature in den Einstellungen aktivieren.

**Q: Gehen meine Daten verloren wenn ich den Server wechsle?**  
A: Nein! Erstelle ein lokales Backup, wechsle Server, stelle wieder her.

**Q: Warum JSON + Markdown?**  
A: JSON ist zuverlässig und schnell (Master). Markdown ist menschenlesbar (Mirror für Desktop).

**Q: Kann ich die App ohne Google Play nutzen?**  
A: Ja! Lade die APK direkt von GitHub oder nutze F-Droid.

---

**Letzte Aktualisierung:** v1.2.1 (2026-01-05)
