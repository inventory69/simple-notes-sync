# Simple Notes Sync

Minimalistische Offline-Notiz-App mit automatischer WLAN-Synchronisierung.

## 📱 Features

- ✅ Offline-first: Notizen lokal erstellen und bearbeiten
- ✅ Auto-Sync: Automatische Synchronisierung im Heim-WLAN
- ✅ WebDAV: Docker-basierter Server
- ✅ Simpel: Fokus auf Funktionalität
- ✅ Robust: Fehlerbehandlung und Konfliktauflösung

## 🏗️ Projekt-Struktur

```
simple-notes-sync/
├── server/              # Docker WebDAV Server
│   ├── docker-compose.yml
│   ├── .env.example
│   └── README.md
│
└── android/             # Android App (Kotlin)
    └── (Android Studio Projekt)
```

## 🚀 Quick Start

### 1. Server starten

```bash
cd server
cp .env.example .env
nano .env  # Passwort anpassen
docker-compose up -d
```

### 2. Server testen

```bash
curl -u noteuser:your_password http://localhost:8080/
```

### 3. Android App entwickeln

```bash
cd android
# In Android Studio öffnen
# Build & Run
```

## 📖 Dokumentation

Vollständige Dokumentation: [project-docs/simple-notes-sync](https://github.com/inventory69/project-docs/tree/main/simple-notes-sync)

- [README.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/README.md) - Projekt-Übersicht
- [IMPLEMENTATION_PLAN.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/IMPLEMENTATION_PLAN.md) - Sprint-Plan
- [SERVER_SETUP.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/SERVER_SETUP.md) - Server-Setup
- [ANDROID_GUIDE.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md) - Android-Entwicklung
- [NOTIFICATIONS.md](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/NOTIFICATIONS.md) - Notification-System

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
