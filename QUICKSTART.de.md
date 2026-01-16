# Quick Start Guide - Simple Notes Sync 📝

> Schritt-für-Schritt Anleitung zur Installation und Einrichtung

**🌍 Sprachen:** **Deutsch** · [English](QUICKSTART.md)

---

## Voraussetzungen

- ✅ Android 8.0+ Smartphone/Tablet
- ✅ WLAN-Verbindung
- ✅ Eigener Server mit Docker (optional - für Self-Hosting)

---

## Option 1: Mit eigenem Server (Self-Hosted) 🏠

### Schritt 1: WebDAV Server einrichten

Auf deinem Server (z.B. Raspberry Pi, NAS, VPS):

```bash
# Repository klonen
git clone https://github.com/inventory69/simple-notes-sync.git
cd simple-notes-sync/server

# Umgebungsvariablen konfigurieren
cp .env.example .env
nano .env
```

**In `.env` anpassen:**
```env
WEBDAV_PASSWORD=dein-sicheres-passwort-hier
```

**Server starten:**
```bash
docker compose up -d
```

**IP-Adresse finden:**
```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
```

➡️ **Notiere dir:** `http://DEINE-SERVER-IP:8080/`

---

### Schritt 2: App installieren

1. **APK herunterladen:** [Neueste Version](https://github.com/inventory69/simple-notes-sync/releases/latest)
   - Wähle: `simple-notes-sync-vX.X.X-standard-universal.apk`
   
2. **Installation erlauben:**
   - Android: Einstellungen → Sicherheit → "Unbekannte Quellen" für deinen Browser aktivieren
   
3. **APK öffnen und installieren**

---

### Schritt 3: App konfigurieren

1. **App öffnen**

2. **Einstellungen öffnen** (⚙️ Icon oben rechts)

3. **Server-Einstellungen konfigurieren:**
   
   | Feld | Wert |
   |------|------|
   | **WebDAV Server URL** | `http://DEINE-SERVER-IP:8080/` |
   | **Benutzername** | `noteuser` |
   | **Passwort** | (dein Passwort aus `.env`) |

   > **💡 Hinweis:** Gib nur die Base-URL ein (ohne `/notes`). Die App erstellt automatisch `/notes/` für JSON-Dateien und `/notes-md/` für Markdown-Export.

4. **"Verbindung testen"** drücken
   - ✅ Erfolg? → Weiter zu Schritt 4
   - ❌ Fehler? → Siehe [Troubleshooting](#troubleshooting)

5. **Auto-Sync aktivieren** (Toggle Switch)

6. **Sync-Intervall wählen:**
   - **15 Min** - Maximale Aktualität (~0.8% Akku/Tag)
   - **30 Min** - Empfohlen (~0.4% Akku/Tag) ⭐
   - **60 Min** - Maximale Akkulaufzeit (~0.2% Akku/Tag)

---

### Schritt 4: Erste Notiz erstellen

1. Zurück zur Hauptansicht (← Pfeil)

2. **"Notiz hinzufügen"** (+ Icon)

3. Titel und Text eingeben

4. **Speichern** (💾 Icon)

5. **Warten auf Auto-Sync** (oder manuell: ⚙️ → "Jetzt synchronisieren")

🎉 **Fertig!** Deine Notizen werden automatisch synchronisiert!

---

## Option 2: Nur lokale Notizen (kein Server) 📱

Du kannst Simple Notes auch **ohne Server** nutzen:

1. **App installieren** (siehe Schritt 2 oben)

2. **Ohne Server-Konfiguration verwenden:**
   - Notizen werden nur lokal gespeichert
   - Kein Auto-Sync
   - Perfekt für reine Offline-Nutzung

---

## 🔋 Akku-Optimierung deaktivieren

Für zuverlässigen Auto-Sync:

1. **Einstellungen** → **Apps** → **Simple Notes Sync**

2. **Akku** → **Akkuverbrauch**

3. Wähle: **"Nicht optimieren"** oder **"Unbeschränkt"**

💡 **Hinweis:** Android Doze Mode kann trotzdem Sync im Standby verzögern (~60 Min). Das ist normal und betrifft alle Apps.

---

## 📊 Sync-Intervalle im Detail

| Intervall | Syncs/Tag | Akku/Tag | Akku/Sync | Anwendungsfall |
|-----------|-----------|----------|-----------|----------------|
| **15 Min** | ~96 | ~0.8% (~23 mAh) | ~0.008% | ⚡ Maximal aktuell (mehrere Geräte) |
| **30 Min** | ~48 | ~0.4% (~12 mAh) | ~0.008% | ✓ **Empfohlen** - ausgewogen |
| **60 Min** | ~24 | ~0.2% (~6 mAh) | ~0.008% | 🔋 Maximale Akkulaufzeit |

---

## 🐛 Troubleshooting

### Verbindungstest schlägt fehl

**Problem:** "Verbindung fehlgeschlagen" beim Test

**Lösungen:**

1. **Server läuft?**
   ```bash
   docker compose ps
   # Sollte "Up" zeigen
   ```

2. **Gleiches Netzwerk?**
   - Smartphone und Server müssen im selben Netzwerk sein

3. **IP-Adresse korrekt?**
   ```bash
   ip addr show | grep "inet "
   # Prüfe ob IP in URL stimmt
   ```

4. **Firewall?**
   ```bash
   # Port 8080 öffnen (falls Firewall aktiv)
   sudo ufw allow 8080/tcp
   ```

5. **Server-Logs prüfen:**
   ```bash
   docker compose logs -f
   ```

---

### Auto-Sync funktioniert nicht

**Problem:** Notizen werden nicht automatisch synchronisiert

**Lösungen:**

1. **Auto-Sync aktiviert?**
   - ⚙️ Einstellungen → Toggle "Auto-Sync" muss **AN** sein

2. **Akku-Optimierung deaktiviert?**
   - Siehe [Akku-Optimierung](#-akku-optimierung-deaktivieren)

3. **Mit WiFi verbunden?**
   - Auto-Sync triggert bei jeder WiFi-Verbindung
   - Prüfe, ob du mit einem WLAN verbunden bist

4. **Manuell testen:**
   - ⚙️ Einstellungen → "Jetzt synchronisieren"
   - Funktioniert das? → Auto-Sync sollte auch funktionieren

---

### Notizen werden nicht angezeigt

**Problem:** Nach Installation sind keine Notizen da, obwohl welche auf dem Server liegen

**Lösung:**

1. **Einmalig manuell synchronisieren:**
   - ⚙️ Einstellungen → "Jetzt synchronisieren"

2. **Server-Daten prüfen:**
   ```bash
   docker compose exec webdav ls -la /data/
   # Sollte .json Dateien zeigen
   ```

---

### Fehler beim Sync

**Problem:** Fehlermeldung beim Synchronisieren

**Lösungen:**

1. **"401 Unauthorized"** → Passwort falsch
   - Prüfe Passwort in App-Einstellungen
   - Vergleiche mit `.env` auf Server

2. **"404 Not Found"** → URL falsch
   - Sollte enden mit `/` (z.B. `http://192.168.1.100:8080/`)

3. **"Network error"** → Keine Verbindung
   - Siehe [Verbindungstest schlägt fehl](#verbindungstest-schlägt-fehl)

---

## 📱 Updates

### Automatisch mit Obtainium (empfohlen)

1. **[Obtainium installieren](https://github.com/ImranR98/Obtanium/releases/latest)**

2. **App hinzufügen:**
   - URL: `https://github.com/inventory69/simple-notes-sync`
   - Auto-Update aktivieren

3. **Fertig!** Obtainium benachrichtigt dich bei neuen Versionen

### Manuell

1. Neue APK von [Releases](https://github.com/inventory69/simple-notes-sync/releases/latest) herunterladen

2. Installieren (überschreibt alte Version)

3. Alle Daten bleiben erhalten!

---

## 🆘 Weitere Hilfe

- **GitHub Issues:** [Problem melden](https://github.com/inventory69/simple-notes-sync/issues)
- **Vollständige Docs:** [DOCS.md](DOCS.md)
- **Server Setup Details:** [server/README.md](server/README.md)

---

**Version:** 1.1.0 · **Erstellt:** Dezember 2025
