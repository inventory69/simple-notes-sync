# Backup

A backup is a single file on your device, independent of the server. Use it before switching phones or servers, or just to have a copy that no sync can touch.

## Create a backup

**Settings → Backup & Restore → Create Backup.** Android asks where to save the file, the name is `simplenotes_backup_<date>_<time>.json`.

The file contains all notes, folders and images. Two switches above the button change what goes in:

- **Encrypt Backup** protects the file with a password of at least 8 characters (AES-256-GCM, key derived with PBKDF2). Without the password nobody can read it, you included, so keep it somewhere safe.
- **Include App Settings** adds the server connection, sync, display and notification settings. This includes your server password in plain text, so turn on encryption as well.

Encrypted backups made with 2.19.0 or newer use a new format and can only be restored by 2.19.0 or newer. Older backups, encrypted or not, can still be restored.

## Restore

**Restore from File** reads a backup file. **Restore from Server** downloads all notes from the WebDAV server instead, which is the quickest way to set up a new phone.

Then pick a mode:

| Mode | Notes in the app | Notes in the backup |
|---|---|---|
| **Merge** (default) | stay as they are | added if their ID is not in the app yet |
| **Overwrite duplicates** | stay, unless the backup has the same ID | added, and win on the same ID |
| **Replace** | all deleted | imported |

If the backup contains app settings, a checkbox decides whether they are restored too.

Notes that cannot be read are skipped and counted, the rest is restored. The restore only fails when not a single note is readable, and in that case your settings are not touched either.

## Safety backup

Right before every restore, the app saves the current notes as a safety backup in its private storage and keeps it for 7 days. A file manager cannot reach that folder, so before a **Replace** it is worth creating a regular backup yourself.

## Moving to a new phone or server

- **New phone, same server:** set up the server connection and use **Restore from Server**, or just let the first sync download everything.
- **New server:** enter the new server in the settings and choose **Take notes along**. The next sync uploads everything there. A backup beforehand does not hurt.

## File format

Unencrypted backups are readable JSON:

```json
{
  "backup_version": 1,
  "created_at": 1759568400000,
  "notes_count": 42,
  "app_version": "2.20.0",
  "notes": [ ... ],
  "folders": [ ... ],
  "assets": [ { "name": "...", "data_base64": "..." } ],
  "app_settings": { ... }
}
```

`created_at` is in milliseconds since 1970. `app_settings` is only present when you included it, `assets` holds the images as Base64. Encrypted backups are binary and start with `SNE2`.
