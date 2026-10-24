# Troubleshooting

Find your symptom, then go through the checks in order. Most problems are solved by the first two.

The **sync status dialog** is a good first stop: tap the icon next to the sync status in the note list. It shows when the last sync succeeded and lists failed syncs, conflicts and missing files with a next step for each.

## Test Connection fails

1. **Is the server running?** On the server: `docker compose ps` should show the container as up. `docker compose logs` shows why it is not.
2. **Can the phone reach it?** Open `http://SERVER-IP:8080/` in the phone's browser. If it does not load, the problem is the network, not the app: wrong IP address, phone on mobile data or a guest Wi-Fi, a firewall on the server (`sudo ufw allow 8080/tcp`), or a VPN on the phone that does not route to your home network.
3. **Is the URL right?** It needs `http://` or `https://`, the port, and for Nextcloud and ownCloud the full WebDAV path, see [other WebDAV servers](../QUICKSTART.md#other-webdav-servers). Enter the base URL only, the app adds `notes/` itself.
4. **"HTTP is only allowed for local servers"**: plain `http://` works only for addresses in your home network. Use the IP address instead of a host name, or set up HTTPS.

## 401 Unauthorized

Username or password is wrong. For the Docker server, compare with `users:` in `config.yml`. After changing `config.yml`, restart the server with `docker compose restart`.

## Nothing syncs automatically

1. **Is Offline Mode off?** Settings → Server Settings.
2. **Are the triggers on?** Settings → Sync & Notifications. Note that autosave alone does not start a sync, only the save button does. See [when the app syncs](../QUICKSTART.md#3-when-the-app-syncs).
3. **Background triggers wait for Wi-Fi.** "On WiFi Connection" and the periodic sync never run on mobile data. With **WiFi-only sync** on, nothing does.
4. **Battery optimization.** Set the app to Unrestricted in the Android settings, see [battery optimization](../QUICKSTART.md#battery-optimization). Some manufacturers have extra "app sleep" lists, check those too.
5. **Does Sync now work?** If a manual sync fails too, go back to [Test Connection fails](#test-connection-fails).

## Notes from the server do not show up

1. **Pull down the note list** to sync once by hand.
2. **Same sync folder?** All devices need the same server URL and the same sync folder name (Settings → Server Settings).
3. **Is the note in a folder or in the archive?** The folder filter and the archive view hide notes. Search looks through all folders.
4. **Are the files on the server?** For the Docker server, `ls notes-data/notes` in the `server` folder lists them.

## "Sync paused"

Another device turned on end-to-end encryption for this sync folder, and this version of the app cannot read encrypted notes. Nothing on the server is changed while the app is paused, and your notes and edits stay on the device. Update the app, or turn encryption off on the other device, then tap **Try again** in the sync status dialog. Details in [SYNC.md](SYNC.md#sync-paused).

## A note shows a warning icon

The note was changed on this device and somewhere else before the two could sync. Open it and pick **Keep mine**, **Use server version** or **Compare**. Until you decide, the note is neither uploaded nor overwritten. Details in [SYNC.md](SYNC.md#conflicts).

## Certificate error with HTTPS

1. **Self-signed or own CA?** Tap **Test Connection** in the server settings, compare the fingerprint with the server and trust the certificate. Or install your CA certificate on the phone. Both ways are described in [self-signed certificates](../QUICKSTART.md#4-self-signed-certificates). A CA has to be installed as a CA certificate, not as a VPN or Wi-Fi certificate. Check under Android settings → Security → Trusted credentials → User.
2. **Host name matches?** With a CA certificate, the name or IP in the URL must be in the certificate. A certificate trusted in the app does not need this.
3. **Not expired?** Check the date on the certificate and on the phone.

## Reporting a bug

[Open an issue](https://github.com/inventory69/simple-notes-sync/issues/new/choose) with your app version (Settings → About this App), Android version and the steps that lead to the problem.

For sync problems, a log helps a lot:

1. Settings → Debug & Diagnostics → turn on **File Logging**.
2. Reproduce the problem.
3. Tap **Export & Share Logs**.

The shared copy has your server address, username, note titles and folder names replaced with placeholders, the file on the device stays complete. Still, read it before posting it publicly.
