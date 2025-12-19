# WebDAV Server - Simple Notes Sync

## Quick Start

```bash
# 1. Umgebungsvariablen anpassen
cp .env.example .env
nano .env

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

Standard (siehe `.env`):
- Username: `noteuser`
- Password: Siehe `.env` Datei

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

# Komplett löschen (inkl. Daten)
docker-compose down -v
```

## Daten

Notizen werden gespeichert in: `./notes-data/`

Backup erstellen:
```bash
tar -czf backup-$(date +%Y%m%d).tar.gz notes-data/
```
