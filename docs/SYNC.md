# Sync

How the app keeps your notes in step with the server, and what to expect when two devices change the same thing. Setting up the connection is in the [quick start](../QUICKSTART.md).

## What lands on the server

The app works below the URL you entered. With the default sync folder name `notes`:

| Path | Content |
|---|---|
| `notes/` | One JSON file per note. App folders become subfolders. `folders.json` holds folder names and colors. |
| `notes-md/` | Markdown copies of the notes, only if you turned them on. Same subfolders. |
| `notes-assets/` | Images, all in one flat folder. |
| `notes-e2ee/e2ee.json` | Only exists once another device turned on end-to-end encryption, see [Sync paused](#sync-paused). |

You can rename the sync folder in the server settings, the other folders follow (`work`, `work-md`, ...). Notes in a folder you marked local only are never uploaded.

The JSON files are the source of truth. Do not edit them by hand, use the Markdown copies for that.

## When it syncs

After saving, on app start and when Wi-Fi connects, plus optional periodic and after-restart syncs. The full table, and which triggers wait for Wi-Fi, is in the [quick start](../QUICKSTART.md#3-when-the-app-syncs).

## Conflicts

A conflict means a note was changed on this device and, before that change reached the server, also somewhere else.

The app checks for this before every upload: it lists the folder on the server and compares each file's E-Tag with the one it saw last. If they differ, the note is not uploaded. This works with every WebDAV server, it does not rely on the server honoring `If-Match`.

A note in conflict shows a warning icon in the list, and the sync that found it posts a notification. It is neither uploaded nor overwritten until you decide. Open it and the editor shows a banner:

- **Keep mine** uploads your version with the next sync and replaces the server copy.
- **Use server version** replaces your version with the one on the server.
- **Compare** shows both side by side first.

There is no automatic merge and no conflict copy. A merge needs the common earlier version, which a plain WebDAV server does not keep, and for prose the result is rarely what anyone wants. A second copy of the note would just move the decision to later.

The sync status dialog (the icon next to the sync status) lists all notes in conflict.

## Deleting

Deleting a note moves it to the trash on every device. It is removed from the server only when the trash is emptied or the note expires there (after 30 days by default).

If a note disappears from the server in some other way, for example because a file was deleted by hand, the app does not just drop it. It moves the note to the local trash, so you can restore it. If more than half of the synced notes are missing at once (from ten notes up), the app assumes something is wrong with the server and deletes nothing.

## Sync paused

Optional end-to-end encryption for the sync folder is in the works ([#9](https://github.com/inventory69/simple-notes-sync/issues/9)). Once a device turns it on, a device that cannot read encrypted notes must not keep writing into that folder, otherwise it would report "synced" while nobody reads its notes. The app already guards against this today, and [Simple Notes Desktop](https://github.com/inventory69/simple-notes-desktop) follows the same rule.

So every sync first checks for `notes-e2ee/e2ee.json`. If it is there, the app pauses: no uploads, downloads or deletions on the server, Markdown files and images included. Your notes stay on the device and changes you make are kept for later. The sync status dialog shows **Sync paused** with a **Try again** button, and you get one notification.

To continue, update the app on this device, or turn encryption off on the device that turned it on.

## Markdown copies and editing on the desktop

Turn on **Settings → Markdown Desktop Integration → Markdown Auto-Sync**. Every sync then also writes each note as a `.md` file to `notes-md/` and reads changes back.

A note looks like this:

```markdown
---
id: 3f2a8c1e-7b4d-4e2a-9c1f-5d6e7f8a9b0c
created: 2026-10-04T09:12:00Z
updated: 2026-10-04T09:30:00Z
device: android-9f3c2a71b8e4d605
type: checklist
---

# Shopping

- [ ] Milk
- [x] Bread
```

Optional fields appear when set: `folder`, `color`, `pinned`, `labels`, `sort`, `archived`, `imported`. Checklists are written as `- [ ]` and `- [x]`.

To edit the files, mount the WebDAV folder on your computer (Explorer "Map network drive", Finder "Connect to Server", `dav://` or `davs://` in your Linux file manager) and open `notes-md/` in any editor, Obsidian included. Things to know:

- A changed file wins when it was modified after the note's last change in the app. The Markdown copy is a mirror, so there is no conflict check like for the JSON files. Do not edit the same note in the app and in an editor at the same time.
- A new `.md` file becomes a new note. The app writes an `id` into its front matter and renames the file to the note's title on the next sync. Links that point to the old file name break.
- Keep the front matter block intact, especially the `id`. Without it the app cannot tell which note a file belongs to.

## Simple Notes Desktop

[Simple Notes Desktop](https://github.com/inventory69/simple-notes-desktop) is a desktop app for Windows and Linux that reads and writes the same `notes/` folder. It uses the JSON files directly, so you get the same conflict handling as between two phones and do not need the Markdown copies for it.
