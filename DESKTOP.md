# Desktop-Integration 🖥️

**🌍 Languages:** **Deutsch** · [English](DESKTOP.en.md)

> Bearbeite deine Notizen mit jedem Markdown-Editor auf dem Desktop

---

## 📋 Übersicht

Die Desktop-Integration ermöglicht dir, Notizen auf dem PC/Mac zu bearbeiten:
- 📝 Jeder Markdown-Editor funktioniert
- 🔄 Automatische Synchronisation über WebDAV
- 💾 Dual-Format: JSON (Master) + Markdown (Mirror)
- ⚡ Last-Write-Wins Konfliktauflösung

---

## 🎯 Warum Markdown?

### Dual-Format Architektur

```
┌─────────────────────────────────────┐
│         Android App                 │
│                                     │
│  ┌──────────┐      ┌─────────────┐ │
│  │   JSON   │ ──→  │  Markdown   │ │
│  │ (Master) │      │  (Mirror)   │ │
│  └──────────┘      └─────────────┘ │
└────────┬────────────────┬───────────┘
         │                │
         ↓                ↓
    WebDAV Server
         │                │
    ┌────┴────┐      ┌────┴──────┐
    │ /notes/ │      │ /notes-md/│
    │ *.json  │      │ *.md      │
    └─────────┘      └───────────┘
         ↑                ↑
         │                │
    ┌────┴────────────────┴───────────┐
    │      Desktop Editor              │
    │  (VS Code, Typora, etc.)         │
    └──────────────────────────────────┘
```

### Vorteile

**JSON (Master):**
- ✅ Zuverlässig und schnell
- ✅ Strukturierte Daten (IDs, Timestamps)
- ✅ Primärer Sync-Mechanismus
- ✅ Immer aktiv

**Markdown (Mirror):**
- ✅ Menschenlesbar
- ✅ Desktop-Editor kompatibel
- ✅ Syntax-Highlighting
- ✅ Optional aktivierbar

---

## 🚀 Schnellstart

### 1. Erste Synchronisation

**Wichtig:** Führe ZUERST einen Sync durch, bevor du Desktop-Integration aktivierst!

1. **App einrichten** (siehe [QUICKSTART.md](QUICKSTART.md))
2. **Server-Verbindung testen**
3. **Erste Notiz erstellen**
4. **Synchronisieren** (Pull-to-Refresh oder Auto-Sync)
5. ✅ Server erstellt automatisch `/notes/` und `/notes-md/` Ordner

### 2. Desktop-Integration aktivieren

1. **Einstellungen** → **Desktop-Integration**
2. **Toggle aktivieren**
3. **Initial Export startet** - Zeigt Progress (X/Y)
4. ✅ Alle bestehenden Notizen werden als `.md` exportiert

### 3. WebDAV als Netzlaufwerk mounten

#### Windows

```
1. Explorer öffnen
2. Rechtsklick auf "Dieser PC"
3. "Netzlaufwerk verbinden"
4. URL eingeben: http://DEIN-SERVER:8080/notes-md/
5. Benutzername: noteuser
6. Passwort: (dein WebDAV-Passwort)
7. Laufwerksbuchstabe: Z:\ (oder beliebig)
8. Fertig!
```

**Zugriff:** `Z:\` im Explorer

#### macOS

```
1. Finder öffnen
2. Menü "Gehe zu" → "Mit Server verbinden" (⌘K)
3. Server-Adresse: http://DEIN-SERVER:8080/notes-md/
4. Verbinden
5. Benutzername: noteuser
6. Passwort: (dein WebDAV-Passwort)
7. Fertig!
```

**Zugriff:** Finder → Netzwerk → notes-md

#### Linux (GNOME)

```
1. Files / Nautilus öffnen
2. "Andere Orte"
3. "Mit Server verbinden"
4. Server-Adresse: dav://DEIN-SERVER:8080/notes-md/
5. Benutzername: noteuser
6. Passwort: (dein WebDAV-Passwort)
7. Fertig!
```

**Zugriff:** `/run/user/1000/gvfs/dav:host=...`

#### Linux (davfs2 - permanent)

```bash
# Installation
sudo apt install davfs2

# Mount-Point erstellen
sudo mkdir -p /mnt/notes-md

# Einmalig mounten
sudo mount -t davfs http://DEIN-SERVER:8080/notes-md/ /mnt/notes-md

# Permanent in /etc/fstab
echo "http://DEIN-SERVER:8080/notes-md/ /mnt/notes-md davfs rw,user,noauto 0 0" | sudo tee -a /etc/fstab
```

**Zugriff:** `/mnt/notes-md/`

---

## 📝 Markdown-Editoren

### Empfohlene Editoren

#### 1. VS Code ⭐ _Empfohlen_

**Vorteile:**
- ✅ Kostenlos & Open Source
- ✅ Markdown-Preview (Ctrl+Shift+V)
- ✅ Syntax-Highlighting
- ✅ Git-Integration
- ✅ Erweiterungen (Spell Check, etc.)

**Setup:**
```
1. VS Code installieren
2. WebDAV-Laufwerk mounten
3. Ordner öffnen: Z:\notes-md\ (Windows) oder /mnt/notes-md (Linux)
4. Fertig! Markdown-Dateien bearbeiten
```

**Extensions (optional):**
- `Markdown All in One` - Shortcuts & Preview
- `Markdown Preview Enhanced` - Bessere Preview
- `Code Spell Checker` - Rechtschreibprüfung

#### 2. Typora

**Vorteile:**
- ✅ WYSIWYG Markdown-Editor
- ✅ Minimalistisches Design
- ✅ Live-Preview
- ⚠️ Kostenpflichtig (~15€)

**Setup:**
```
1. Typora installieren
2. WebDAV mounten
3. Ordner in Typora öffnen
4. Notizen bearbeiten
```

#### 3. Notepad++

**Vorteile:**
- ✅ Leichtgewichtig
- ✅ Schnell
- ✅ Syntax-Highlighting
- ⚠️ Keine Markdown-Preview

**Setup:**
```
1. Notepad++ installieren
2. WebDAV mounten
3. Dateien direkt öffnen
```

#### 4. Obsidian

**Vorteile:**
- ✅ Zweite Gehirn-Philosophie
- ✅ Graph-View für Verlinkungen
- ✅ Viele Plugins
- ⚠️ Sync-Konflikte möglich (2 Master)

**Setup:**
```
1. Obsidian installieren
2. WebDAV als Vault öffnen
3. Vorsicht: Obsidian erstellt eigene Metadaten!
```

**⚠️ Nicht empfohlen:** Kann Frontmatter verändern

---

## 📄 Markdown-Dateiformat

### Struktur

Jede Notiz wird als `.md` Datei mit YAML-Frontmatter exportiert:

```markdown
---
id: abc-123-def-456
created: 2026-01-05T14:30:22Z
updated: 2026-01-05T14:30:22Z
tags: []
---

# Notiz-Titel

Notiz-Inhalt hier...
```

### Frontmatter-Felder

| Feld | Typ | Beschreibung | Pflicht |
|------|-----|--------------|---------|
| `id` | UUID | Eindeutige Notiz-ID | ✅ Ja |
| `created` | ISO8601 | Erstellungsdatum | ✅ Ja |
| `updated` | ISO8601 | Änderungsdatum | ✅ Ja |
| `tags` | Array | Tags (zukünftig) | ❌ Nein |

### Dateinamen

**Sanitization-Regeln:**
```
Titel: "Meine Einkaufsliste 🛒"
→ Dateiname: "Meine_Einkaufsliste.md"

Entfernt werden:
- Emojis: 🛒 → entfernt
- Sonderzeichen: / \ : * ? " < > | → entfernt
- Mehrfache Leerzeichen → einzelnes Leerzeichen
- Leerzeichen → Unterstrich _
```

**Beispiele:**
```
"Meeting Notes 2026" → "Meeting_Notes_2026.md"
"To-Do: Projekt" → "To-Do_Projekt.md"
"Urlaub ☀️" → "Urlaub.md"
```

---

## 🔄 Synchronisation

### Workflow: Android → Desktop

1. **Notiz in App erstellen/bearbeiten**
2. **Sync ausführen** (Auto oder manuell)
3. **JSON wird hochgeladen** (`/notes/abc-123.json`)
4. **Markdown wird exportiert** (`/notes-md/Notiz_Titel.md`) _(nur wenn Desktop-Integration AN)_
5. **Desktop-Editor zeigt Änderungen** (nach Refresh)

### Workflow: Desktop → Android

1. **Markdown-Datei bearbeiten** (im gemounteten Ordner)
2. **Speichern** - Datei liegt sofort auf Server
3. **In App: Markdown-Import ausführen**
   - Einstellungen → "Import Markdown Changes"
   - Oder: Auto-Import bei jedem Sync (zukünftig)
4. **App übernimmt Änderungen** (wenn Desktop-Version neuer)

### Konfliktauflösung: Last-Write-Wins

**Regel:** Neueste Version (nach `updated` Timestamp) gewinnt

**Beispiel:**
```
App-Version:     updated: 2026-01-05 14:00
Desktop-Version: updated: 2026-01-05 14:30
→ Desktop gewinnt (neuerer Timestamp)
```

**Automatisch:**
- ✅ Beim Markdown-Import
- ✅ Beim JSON-Sync
- ⚠️ Keine Merge-Konflikte - nur komplettes Überschreiben

---

## ⚙️ Einstellungen

### Desktop-Integration Toggle

**Einstellungen → Desktop-Integration**

**AN (aktiviert):**
- ✅ Neue Notizen → automatisch als `.md` exportiert
- ✅ Aktualisierte Notizen → `.md` Update
- ✅ Gelöschte Notizen → `.md` bleibt (zukünftig: auch löschen)

**AUS (deaktiviert):**
- ❌ Kein Markdown-Export
- ✅ JSON-Sync läuft normal weiter
- ✅ Bestehende `.md` Dateien bleiben erhalten

### Initial Export

**Was passiert beim Aktivieren:**
1. Alle bestehenden Notizen werden gescannt
2. Progress-Dialog zeigt Fortschritt (z.B. "23/42")
3. Jede Notiz wird als `.md` exportiert
4. Bei Fehlern: Einzelne Notiz wird übersprungen
5. Erfolgsmeldung mit Anzahl exportierter Notizen

**Zeit:** ~1-2 Sekunden pro 50 Notizen

---

## 🛠️ Erweiterte Nutzung

### Manuelle Markdown-Erstellung

Du kannst `.md` Dateien manuell erstellen:

```markdown
---
id: 00000000-0000-0000-0000-000000000001
created: 2026-01-05T12:00:00Z
updated: 2026-01-05T12:00:00Z
---

# Neue Desktop-Notiz

Inhalt hier...
```

**⚠️ Wichtig:**
- `id` muss gültige UUID sein (z.B. mit uuidgen.io)
- Timestamps in ISO8601-Format
- Frontmatter mit `---` umschließen

### Bulk-Operations

**Mehrere Notizen auf einmal bearbeiten:**

1. WebDAV mounten
2. Alle `.md` Dateien in VS Code öffnen
3. Suchen & Ersetzen über alle Dateien (Ctrl+Shift+H)
4. Speichern
5. In App: "Import Markdown Changes"

### Scripting

**Beispiel: Alle Notizen nach Datum sortieren**

```bash
#!/bin/bash
cd /mnt/notes-md/

# Alle .md Dateien nach Update-Datum sortieren
for file in *.md; do
  updated=$(grep "^updated:" "$file" | cut -d' ' -f2)
  echo "$updated $file"
done | sort
```

---

## ❌ Fehlerbehebung

### "404 Not Found" beim WebDAV-Mount

**Ursache:** `/notes-md/` Ordner existiert nicht

**Lösung:**
1. **Erste Sync durchführen** - Ordner wird automatisch erstellt
2. ODER: Manuell erstellen via Terminal:
   ```bash
   curl -X MKCOL -u noteuser:password http://server:8080/notes-md/
   ```

### Markdown-Dateien erscheinen nicht

**Ursache:** Desktop-Integration nicht aktiviert

**Lösung:**
1. Einstellungen → "Desktop-Integration" AN
2. Warten auf Initial Export
3. WebDAV-Ordner refreshen

### Änderungen vom Desktop erscheinen nicht in App

**Ursache:** Markdown-Import nicht ausgeführt

**Lösung:**
1. Einstellungen → "Import Markdown Changes"
2. ODER: Auto-Sync abwarten (zukünftiges Feature)

### "Frontmatter fehlt" Fehler

**Ursache:** `.md` Datei ohne gültiges YAML-Frontmatter

**Lösung:**
1. Datei in Editor öffnen
2. Frontmatter am Anfang hinzufügen:
   ```yaml
   ---
   id: NEUE-UUID-HIER
   created: 2026-01-05T12:00:00Z
   updated: 2026-01-05T12:00:00Z
   ---
   ```
3. Speichern und erneut importieren

---

## 🔒 Sicherheit & Best Practices

### Do's ✅

- ✅ **Backup vor Bulk-Edits** - Lokales Backup erstellen
- ✅ **Ein Editor zur Zeit** - Nicht parallel in App UND Desktop bearbeiten
- ✅ **Sync abwarten** - Vor Desktop-Bearbeitung Sync durchführen
- ✅ **Frontmatter respektieren** - Nicht manuell ändern (außer du weißt was du tust)

### Don'ts ❌

- ❌ **Parallel bearbeiten** - App und Desktop gleichzeitig → Konflikte
- ❌ **Frontmatter löschen** - Notiz kann nicht mehr importiert werden
- ❌ **IDs ändern** - Notiz wird als neue erkannt
- ❌ **Timestamps manipulieren** - Konfliktauflösung funktioniert nicht

### Empfohlener Workflow

```
1. Sync in App (Pull-to-Refresh)
2. Desktop öffnen
3. Änderungen machen
4. Speichern
5. In App: "Import Markdown Changes"
6. Überprüfen
7. Weiteren Sync durchführen
```

---

## 📊 Vergleich: JSON vs Markdown

| Aspekt | JSON | Markdown |
|--------|------|----------|
| **Format** | Strukturiert | Fließtext |
| **Lesbarkeit (Mensch)** | ⚠️ Mittel | ✅ Gut |
| **Lesbarkeit (Maschine)** | ✅ Perfekt | ⚠️ Parsing nötig |
| **Metadata** | Native | Frontmatter |
| **Editoren** | Code-Editoren | Alle Text-Editoren |
| **Sync-Geschwindigkeit** | ✅ Schnell | ⚠️ Langsamer |
| **Zuverlässigkeit** | ✅ 100% | ⚠️ Frontmatter-Fehler möglich |
| **Mobile-First** | ✅ Ja | ❌ Nein |
| **Desktop-First** | ❌ Nein | ✅ Ja |

**Fazit:** Beide Formate nutzen = Beste Erfahrung auf beiden Plattformen!

---

## 🔮 Zukünftige Features

Geplant für v1.3.0+:

- ⏳ **Auto-Markdown-Import** - Bei jedem Sync automatisch
- ⏳ **Bidirektionaler Sync** - Ohne manuellen Import
- ⏳ **Markdown-Vorschau** - In der App
- ⏳ **Konflikts-UI** - Bei gleichzeitigen Änderungen
- ⏳ **Tags in Frontmatter** - Synchronisiert mit App
- ⏳ **Attachments** - Bilder/Dateien in Markdown

---

**📚 Siehe auch:**
- [QUICKSTART.md](QUICKSTART.md) - App-Einrichtung
- [FEATURES.md](FEATURES.md) - Vollständige Feature-Liste
- [BACKUP.md](BACKUP.md) - Backup & Wiederherstellung

**Letzte Aktualisierung:** v1.2.1 (2026-01-05)
