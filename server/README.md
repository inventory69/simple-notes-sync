# WebDAV server

A minimal WebDAV server for Simple Notes Sync, based on [hacdias/webdav](https://github.com/hacdias/webdav). Any other WebDAV server works too, this one is just the shortest way to get started.

## Setup

```bash
cp config.yml.example config.yml
```

Edit `config.yml` and set a password under `users:`. The container does not start without this file. `config.yml` is in `.gitignore`, so your password stays out of git.

```bash
docker compose up -d
```

Check that it answers (expects `207 Multi-Status`, a wrong password gives `401`):

```bash
curl -u noteuser:YOUR_PASSWORD -X PROPFIND -H "Depth: 1" http://localhost:8080/
```

In the app, enter `http://SERVER-IP:8080/` as the server URL.

## Port

The server listens on 8080. To change it, create `.env` in this folder:

```env
WEBDAV_PORT=9090
```

## Day to day

```bash
docker compose logs -f                          # logs
docker compose pull && docker compose up -d     # update
docker compose down                             # stop
```

## Data and backup

The notes are plain files in `./notes-data/`: JSON in `notes/`, images in `notes-assets/` and, if you turned them on, Markdown copies in `notes-md/`. To back them up, copy the folder:

```bash
tar -czf notes-backup-$(date +%Y%m%d).tar.gz notes-data/
```

## HTTPS

The container speaks plain HTTP. That is fine inside your home network. The app refuses `http://` for anything else, so for access from outside put a reverse proxy with a certificate in front of it. With Caddy, for example:

```
notes.example.com {
    reverse_proxy localhost:8080
}
```

Then use `https://notes.example.com/` in the app.

## Conflict detection does not depend on the server

This server ignores write preconditions: a `PUT` with a stale `If-Match` header overwrites the file instead of answering `412`. That is fine. Before uploading, the app lists the folder and compares the E-Tags itself, which works with any WebDAV server. Servers that do honor `If-Match` (Nextcloud, ownCloud) get it as an extra safeguard.
