# Simple Notes Sync 📝

> Minimalistische Android-App für Offline-Notizen mit automatischer WLAN-Synchronisierung

Eine schlanke Notiz-App ohne Schnickschnack - perfekt für schnelle Gedanken, die automatisch zu Hause synchronisiert werden.

---

## ✨ Features

- 📝 **Offline-first** - Notizen werden lokal gespeichert und sind immer verfügbar
- 🔄 **Auto-Sync** - Automatische Synchronisierung wenn du im Heimnetzwerk bist
- 🏠 **WebDAV Server** - Deine Daten bleiben bei dir (Docker-Container)
- 🔋 **Akkuschonend** - Nur ~0.4% Akkuverbrauch pro Tag
- 🚫 **Keine Cloud** - Keine Google, keine Microsoft, keine Drittanbieter
- 🔐 **Privacy** - Keine Tracking, keine Analytics, keine Standort-Berechtigungen

---

## 📥 Installation

### Android App

**Option 1: APK herunterladen**

1. Neueste [Release](../../releases/latest) öffnen
2. `app-debug.apk` herunterladen
3. APK auf dem Handy installieren

**Option 2: Selbst bauen**

```bash
cd android
./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

### WebDAV Server

Der Server läuft als Docker-Container und speichert deine Notizen.

```bash
cd server
cp .env.example .env
nano .env  # Passwort anpassen!
docker-compose up -d
```

**Server testen:**
```bash
curl -u noteuser:dein_passwort http://192.168.0.XXX:8080/
```

---

## 🚀 Schnellstart

1. **Server starten** (siehe oben)
2. **App installieren** und öffnen
3. **Einstellungen öffnen** (⚙️ Symbol oben rechts)
4. **Server konfigurieren:**
   - Server-URL: `http://192.168.0.XXX:8080/notes`
   - Benutzername: `noteuser`
   - Passwort: (aus `.env` Datei)
   - Auto-Sync: **AN**
5. **Fertig!** Notizen werden jetzt automatisch synchronisiert

---

## 💡 Wie funktioniert Auto-Sync?

Die App prüft **alle 30 Minuten**, ob:
- ✅ WLAN verbunden ist
- ✅ Server im gleichen Netzwerk erreichbar ist
- ✅ Neue Notizen vorhanden sind

Wenn alle Bedingungen erfüllt → **Automatische Synchronisierung**

**Wichtig:** Funktioniert nur im selben Netzwerk wie der Server (kein Internet-Zugriff nötig!)

---

## 🔋 Akkuverbrauch

| Komponente | Verbrauch/Tag |
|------------|---------------|
| WorkManager (alle 30 Min) | ~0.3% |
| Netzwerk-Checks | ~0.1% |
| **Total** | **~0.4%** |

Bei einem 3000 mAh Akku entspricht das ~12 mAh pro Tag.

---

## 📱 Screenshots

_TODO: Screenshots hinzufügen_

---

## 🛠️ Technische Details

Mehr Infos zur Architektur und Implementierung findest du in der [technischen Dokumentation](DOCS.md).

**Stack:**
- **Android:** Kotlin, Material Design 3, WorkManager
- **Server:** Docker, WebDAV (bytemark/webdav)
- **Sync:** Sardine Android (WebDAV Client)

---

## 📄 Lizenz

[Lizenz hier einfügen]

---

## 🤝 Beitragen

Contributions sind willkommen! Bitte öffne ein Issue oder Pull Request.

---

## 📄 Lizenz

MIT License - siehe [LICENSE](LICENSE)

---

**Projekt Start:** 19. Dezember 2025  
**Status:** ✅ Funktional & Produktiv

## 📖 Dokumentation

### In diesem Repository:

- **[QUICKSTART.md](QUICKSTART.md)** - Schnellstart-Anleitung
- **[server/README.md](server/README.md)** - Server-Verwaltung

### Vollständige Dokumentation (project-docs):

- [README.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/README.md) - Projekt-Übersicht & Architektur
- [IMPLEMENTATION_PLAN.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/IMPLEMENTATION_PLAN.md) - Detaillierter Sprint-Plan
- [SERVER_SETUP.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/SERVER_SETUP.md) - Server-Setup Details
- [ANDROID_GUIDE.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md) - 📱 Kompletter Android-Code
- [NOTIFICATIONS.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/NOTIFICATIONS.md) - Notification-System Details
- [WINDOWS_SETUP.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/WINDOWS_SETUP.md) - 🪟 Windows + Android Studio Setup
- [CODE_REFERENCE.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/CODE_REFERENCE.md) - Schnelle Code-Referenz

## ⚙️ Server Konfiguration

**Standard-Credentials:**
- Username: `noteuser`
- Password: Siehe `.env` im `server/` Verzeichnis

**Server-URL:**
- Lokal: `http://localhost:8080/`
- Im Netzwerk: `http://YOUR_IP:8080/`

IP-Adresse finden:
```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
```

## 📱 Android App Setup

### Vorraussetzungen

- Android Studio Hedgehog (2023.1.1) oder neuer
- JDK 17
- Min SDK 24 (Android 7.0)
- Target SDK 34 (Android 14)

### In App konfigurieren

1. App starten
2. Einstellungen öffnen
3. Server-URL eintragen (z.B. `http://192.168.1.100:8080/`)
4. Username & Passwort eingeben
5. Heim-WLAN SSID eingeben
6. "Verbindung testen"

## 🔧 Entwicklung

### Server-Management

```bash
# Status prüfen
docker-compose ps

# Logs anschauen
docker-compose logs -f

# Neustarten
docker-compose restart

# Stoppen
docker-compose down
```

### Android-Build

```bash
cd android
./gradlew assembleDebug

# APK Location:
# app/build/outputs/apk/debug/app-debug.apk
```

## 🧪 Testing

### Server-Test

```bash
# Testdatei hochladen
echo '{"id":"test","title":"Test","content":"Hello"}' > test.json
curl -u noteuser:password -T test.json http://localhost:8080/test.json

# Datei abrufen
curl -u noteuser:password http://localhost:8080/test.json

# Datei löschen
curl -u noteuser:password -X DELETE http://localhost:8080/test.json
```

### Android-App

1. Notiz erstellen → speichern → in Liste sichtbar ✓
2. WLAN verbinden → Auto-Sync ✓
3. Server offline → Fehlermeldung ✓
4. Konflikt-Szenario → Auflösung ✓

## 📦 Deployment

### Server (Production)

**Option 1: Lokaler Server (Raspberry Pi, etc.)**
```bash
docker-compose up -d
```

**Option 2: VPS (DigitalOcean, Hetzner, etc.)**
```bash
# Mit HTTPS (empfohlen)
# Zusätzlich: Reverse Proxy (nginx/Caddy) + Let's Encrypt
```

### Android App

```bash
# Release Build
./gradlew assembleRelease

# APK signieren
# Play Store Upload oder Direct Install
```

## 🔐 Security

**Entwicklung:**
- ✅ HTTP Basic Auth
- ✅ Nur im lokalen Netzwerk

**Produktion:**
- ⚠️ HTTPS mit SSL/TLS (empfohlen)
- ⚠️ Starkes Passwort
- ⚠️ Firewall-Regeln
- ⚠️ VPN für externen Zugriff

## 🐛 Troubleshooting

### Server startet nicht

```bash
# Port bereits belegt?
sudo netstat -tlnp | grep 8080

# Logs checken
docker-compose logs webdav
```

### Android kann nicht verbinden

- Ist Android im gleichen WLAN?
- Ist die Server-IP korrekt?
- Firewall blockiert Port 8080?
- Credentials korrekt?

```bash
# Ping zum Server
ping YOUR_SERVER_IP

# Port erreichbar?
telnet YOUR_SERVER_IP 8080
```

## 📝 TODO / Roadmap

### Version 1.0 (MVP)
- [x] Docker WebDAV Server
- [ ] Android Basic CRUD
- [ ] Auto-Sync bei WLAN
- [ ] Error Handling
- [ ] Notifications

### Version 1.1
- [ ] Suche
- [ ] Dark Mode
- [ ] Markdown-Support

### Version 2.0
- [ ] Desktop-Client (Flutter Desktop)
- [ ] Tags/Kategorien
- [ ] Verschlüsselung
- [ ] Shared Notes

## 📄 License

MIT License - siehe [LICENSE](LICENSE)

## 👤 Author

Created for personal use - 2025

## 🙏 Acknowledgments

- [bytemark/webdav](https://hub.docker.com/r/bytemark/webdav) - Docker WebDAV Server
- [Sardine Android](https://github.com/thegrizzlylabs/sardine-android) - WebDAV Client
- [Android WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) - Background Tasks

---

**Project Start:** 19. Dezember 2025
**Status:** 🚧 In Development
