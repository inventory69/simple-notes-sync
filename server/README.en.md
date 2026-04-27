# WebDAV Server - Simple Notes Sync

**🌍 Languages:** [Deutsch](README.md) · **English**

> Based on [hacdias/webdav](https://github.com/hacdias/webdav) — a lightweight, actively maintained WebDAV server.

---

## Quick Start

```bash
# 1. Create config and set credentials
cp config.yml.example config.yml
nano config.yml

# 2. Start server
docker-compose up -d

# 3. Check logs
docker-compose logs -f

# 4. Test
curl -u noteuser:your_password http://localhost:8080/
```

## Server URL

**Local:** `http://localhost:8080/`

**On network:** `http://YOUR_IP:8080/` (e.g. `http://192.168.1.100:8080/`)

Find IP address:
```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
```

## Credentials

Credentials are set in `config.yml` (not committed to git):
```yaml
users:
  - username: noteuser
    password: your_secure_password
```

## Custom Port (optional)

Default port is `8080`. To change it:
```bash
cp .env.example .env
# Set WEBDAV_PORT in .env
```

## Management

```bash
# Check status
docker-compose ps

# View logs
docker-compose logs -f

# Restart
docker-compose restart

# Stop
docker-compose down

# Update image
docker-compose pull && docker-compose up -d
```

## Data Location

Your notes are stored in: `./notes-data/`

**Backup:**
```bash
# Create backup
tar -czf notes-backup-$(date +%Y%m%d).tar.gz notes-data/

# Restore backup
tar -xzf notes-backup-YYYYMMDD.tar.gz
```

## External Access (HTTPS)

For access from outside your home network, use a reverse proxy like Caddy or nginx:

**Example with Caddy:**

```Caddyfile
notes.yourdomain.com {
    reverse_proxy localhost:8080
}
```

**Important:** Always use HTTPS for external access to protect your credentials!
