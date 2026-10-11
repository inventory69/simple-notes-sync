# Quick start

This guide gets a server running, connects the app and explains when the app syncs. The app also works without any server: just install it and start writing.

## 1. Server

You need a WebDAV server. If you already have one (Nextcloud, ownCloud, a NAS), skip to [Other WebDAV servers](#other-webdav-servers).

Otherwise, this repository contains a small Docker setup based on [hacdias/webdav](https://github.com/hacdias/webdav). On any machine with Docker:

```bash
git clone https://github.com/inventory69/simple-notes-sync.git
cd simple-notes-sync/server
cp config.yml.example config.yml
```

Open `config.yml` and set a password under `users:`. The username `noteuser` can stay. Then start the server:

```bash
docker compose up -d
```

The server listens on port 8080. To use another port, create a file `.env` next to `docker-compose.yml` with `WEBDAV_PORT=9090`. Updates, logs and backups of the server are in [server/README.md](server/README.md).

Find the IP address of the machine, for example with `ip addr` on Linux. You need it in the next step.

### Other WebDAV servers

Enter the WebDAV address of your account. The app creates a folder `notes/` below it and keeps everything in there. Examples:

| Server | URL |
|---|---|
| Nextcloud | `https://cloud.example.com/remote.php/dav/files/USERNAME/` |
| ownCloud | `https://cloud.example.com/remote.php/webdav/` |
| Docker server from this repo | `http://192.168.1.10:8080/` |

Basic and Digest authentication are both detected automatically.

### HTTP or HTTPS

Plain `http://` is only allowed for addresses in your home network: `192.168.x.x`, `10.x.x.x`, `172.16.x.x` to `172.31.x.x`, `localhost` and names ending in `.local`. Everything else needs `https://`, because your password would otherwise travel unencrypted through the internet.

## 2. App

1. Install the app from [F-Droid](https://f-droid.org/packages/dev.dettmer.simplenotes), [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/dev.dettmer.simplenotes), [Google Play](https://play.google.com/store/apps/details?id=dev.dettmer.simplenotes) or [GitHub Releases](https://github.com/inventory69/simple-notes-sync/releases/latest).
2. Open **Settings → Server Settings** and switch off **Offline Mode**. The fields below are locked until you do.
3. Enter the server URL, username and password. Enter the base URL only, without `notes/`.
4. Tap **Test Connection**.

That's it. Notes you already wrote are uploaded with the next sync, and notes that are already on the server are downloaded.

## 3. When the app syncs

You can change all of this in **Settings → Sync & Notifications**.

| Trigger | Default | Needs Wi-Fi |
|---|---|---|
| After Saving | on | no |
| On App Start | on | no |
| On WiFi Connection | on | yes |
| Automatically every 15, 30 or 60 minutes | off | yes |
| After Device Restart (restarts the background sync) | off | yes |
| Sync now, pull to refresh | always | no |

**After Saving** fires when you tap the save button. Autosave alone does not start a sync, the next trigger picks the change up.

The background triggers wait for Wi-Fi to save battery and mobile data. The others use any connection. If you turn on **WiFi-only sync**, every sync waits for Wi-Fi.

The app does not need to know your Wi-Fi name. If the server is not reachable (you are on another network), a background sync simply ends without changing anything.

### Battery optimization

Android delays or skips background work for apps under battery optimization. If the background triggers should fire reliably, set the app to **Unrestricted** (or **Not optimized**) in the Android settings under Apps → Simple Notes → Battery. The app offers to open that screen when you switch off Offline Mode.

Even then Android may hold back a periodic sync for a while when the phone sleeps. Syncing after saving and on app start is not affected.

## 4. Self-signed certificates

If your server uses HTTPS with a certificate from your own CA, install that CA certificate on the phone:

1. Copy the CA certificate (`.crt` or `.pem`) to the phone.
2. Android settings → Security → Encryption & credentials → Install a certificate → **CA certificate**. The menu names differ between manufacturers.
3. Enter the `https://` URL in the app as usual.

The app trusts CA certificates installed by the user, many apps do not. It does not accept certificates that fail validation in any other way, so the host name in the URL must match the certificate. A certificate from Let's Encrypt needs none of this.

## 5. Updates

F-Droid, IzzyOnDroid and Google Play update the app on their own. For APKs from GitHub, [Obtainium](https://github.com/ImranR98/Obtainium) can watch the releases for you. An update never touches your notes.

## Something not working?

See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).
