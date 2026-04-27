# WebDAV Server - Simple Notes Sync

**🌍 Sprachen:** **Deutsch** · [English](README.en.md)

> Basiert auf [hacdias/webdav](https://github.com/hacdias/webdav) — leichtgewichtiger, aktiv gewarteter WebDAV-Server.

---

## Quick Start

```bash
# 1. Konfiguration anlegen und Zugangsdaten setzen
cp config.yml.example config.yml
nano config.yml

# 2. Server starten
docker-compose up -d

# 3. Logs prüfen
docker-compose logs -f

# 4. Testen
curl -u noteuser:your_password http://localhost:8080/
```

## Server URL

**Lokal:** `http://localhost:8080/`

**Im Netzwerk:** `http://YOUR_IP:8080/` (z.B. `http://192.168.1.100:8080/`)

IP-Adresse herausfinden:
```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
```

## Credentials

Zugangsdaten werden in `config.yml` gesetzt (wird nicht ins Git eingecheckt):
```yaml
users:
  - username: noteuser
    password: dein_sicheres_passwort
```

## Port anpassen (optional)

Standardmäßig läuft der Server auf Port `8080`. Zum Ändern:
```bash
cp .env.example .env
# WEBDAV_PORT in .env setzen
```

## Management

```bash
# Status prüfen
docker-compose ps

# Logs anschauen
docker-compose logs -f

# Neustarten
docker-compose restart

# Stoppen
docker-compose down

# Image aktualisieren
docker-compose pull && docker-compose up -d

# Komplett löschen (inkl. Daten)
docker-compose down -v
```

## Daten

Notizen werden gespeichert in: `./notes-data/`

Backup erstellen:
```bash
tar -czf backup-$(date +%Y%m%d).tar.gz notes-data/
```
