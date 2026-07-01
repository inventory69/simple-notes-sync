# Desktop Integration 🖥️

**🌍 Languages:** [Deutsch](DESKTOP.de.md) · **English**

> Edit your notes with any Markdown editor on desktop

---

## 📋 Overview

Desktop integration allows you to edit notes on PC/Mac:
- 📝 Any Markdown editor works
- 🔄 Automatic synchronization via WebDAV
- 💾 Dual-format: JSON (master) + Markdown (mirror)
- ⚡ Last-Write-Wins conflict resolution

---

## 🎯 Why Markdown?

### Dual-Format Architecture

```
┌─────────────────────────────────────┐
│         Android App                 │
│                                     │
│  ┌──────────┐      ┌─────────────┐ │
│  │   JSON   │ ──→  │  Markdown   │ │
│  │ (Master) │      │  (Mirror)   │ │
│  └──────────┘      └─────────────┘ │
└────────┬────────────────┬───────────┘
         │                │
         ↓                ↓
    WebDAV Server
         │                │
    ┌────┴────┐      ┌────┴──────┐
    │ /notes/ │      │ /notes-md/│
    │ *.json  │      │ *.md      │
    └─────────┘      └───────────┘
         ↑                ↑
         │                │
    ┌────┴────────────────┴───────────┐
    │      Desktop Editor              │
    │  (VS Code, Typora, etc.)         │
    └──────────────────────────────────┘
```

### Advantages

**JSON (Master):**
- ✅ Reliable and fast
- ✅ Structured data (IDs, timestamps)
- ✅ Primary sync mechanism
- ✅ Always active

**Markdown (Mirror):**
- ✅ Human-readable
- ✅ Desktop editor compatible
- ✅ Syntax highlighting
- ✅ Optionally activatable

---

## 🚀 Quick Start

### 1. First Synchronization

**Important:** Perform a sync FIRST before activating desktop integration!

1. **Set up app** (see [QUICKSTART.en.md](QUICKSTART.en.md))
2. **Test server connection**
3. **Create first note**
4. **Synchronize** (pull-to-refresh or auto-sync)
5. ✅ Server automatically creates `/notes/` and `/notes-md/` folders

### 2. Activate Desktop Integration

1. **Settings** → **Desktop Integration**
2. **Toggle ON**
3. **Initial export starts** - Shows progress (X/Y)
4. ✅ All existing notes are exported as `.md`

### 3. Mount WebDAV as Network Drive

#### Windows

```
1. Open Explorer
2. Right-click on "This PC"
3. "Map network drive"
4. Enter URL: http://YOUR-SERVER:8080/notes-md/
5. Username: noteuser
6. Password: (your WebDAV password)
7. Drive letter: Z:\ (or any)
8. Done!
```

**Access:** `Z:\` in Explorer

#### macOS

```
1. Open Finder
2. Menu "Go" → "Connect to Server" (⌘K)
3. Server address: http://YOUR-SERVER:8080/notes-md/
4. Connect
5. Username: noteuser
6. Password: (your WebDAV password)
7. Done!
```

**Access:** Finder → Network → notes-md

#### Linux (GNOME)

```
1. Open Files / Nautilus
2. "Other Locations"
3. "Connect to Server"
4. Server address: dav://YOUR-SERVER:8080/notes-md/
5. Username: noteuser
6. Password: (your WebDAV password)
7. Done!
```

**Access:** `/run/user/1000/gvfs/dav:host=...`

#### Linux (davfs2 - permanent)

```bash
# Installation
sudo apt install davfs2

# Create mount point
sudo mkdir -p /mnt/notes-md

# Mount once
sudo mount -t davfs http://YOUR-SERVER:8080/notes-md/ /mnt/notes-md

# Permanent in /etc/fstab
echo "http://YOUR-SERVER:8080/notes-md/ /mnt/notes-md davfs rw,user,noauto 0 0" | sudo tee -a /etc/fstab
```

**Access:** `/mnt/notes-md/`

---

## 📝 Markdown Editors

### Recommended Editors

#### 1. VS Code ⭐ _Recommended_

**Advantages:**
- ✅ Free & open source
- ✅ Markdown preview (Ctrl+Shift+V)
- ✅ Syntax highlighting
- ✅ Git integration
- ✅ Extensions (spell check, etc.)

**Setup:**
```
1. Install VS Code
2. Mount WebDAV drive
3. Open folder: Z:\notes-md\ (Windows) or /mnt/notes-md (Linux)
4. Done! Edit Markdown files
```

**Extensions (optional):**
- `Markdown All in One` - Shortcuts & preview
- `Markdown Preview Enhanced` - Better preview
- `Code Spell Checker` - Spell checking

#### 2. Typora

**Advantages:**
- ✅ WYSIWYG Markdown editor
- ✅ Minimalist design
- ✅ Live preview
- ⚠️ Paid (~15€)

**Setup:**
```
1. Install Typora
2. Mount WebDAV
3. Open folder in Typora
4. Edit notes
```

#### 3. Notepad++

**Advantages:**
- ✅ Lightweight
- ✅ Fast
- ✅ Syntax highlighting
- ⚠️ No Markdown preview

**Setup:**
```
1. Install Notepad++
2. Mount WebDAV
3. Open files directly
```

#### 4. Obsidian

**Advantages:**
- ✅ Second brain philosophy
- ✅ Graph view for links
- ✅ Many plugins
- ⚠️ Sync conflicts possible (2 masters)

**Setup:**
```
1. Install Obsidian
2. Open WebDAV as vault
3. Caution: Obsidian creates own metadata!
```

**⚠️ Not recommended:** Can alter frontmatter

---

## 📄 Markdown File Format

### Structure

Each note is exported as `.md` file with YAML frontmatter:

```markdown
---
id: abc-123-def-456
created: 2026-01-05T14:30:22Z
updated: 2026-01-05T14:30:22Z
tags: []
---

# Note Title

Note content here...
```

### Frontmatter Fields

| Field | Type | Description | Required |
|-------|------|-------------|----------|
| `id` | UUID | Unique note ID | ✅ Yes |
| `created` | ISO8601 | Creation date | ✅ Yes |
| `updated` | ISO8601 | Modification date | ✅ Yes |
| `tags` | Array | Tags (future) | ❌ No |

### Filenames

**Sanitization rules:**
```
Title: "My Shopping List 🛒"
→ Filename: "My_Shopping_List.md"

Removed:
- Emojis: 🛒 → removed
- Special chars: / \ : * ? " < > | → removed
- Multiple spaces → single space
- Spaces → underscore _
```

**Examples:**
```
"Meeting Notes 2026" → "Meeting_Notes_2026.md"
"To-Do: Project" → "To-Do_Project.md"
"Vacation ☀️" → "Vacation.md"
```

---

## 🔄 Synchronization

### Workflow: Android → Desktop

1. **Create/edit note in app**
2. **Run sync** (auto or manual)
3. **JSON is uploaded** (`/notes/abc-123.json`)
4. **Markdown is exported** (`/notes-md/Note_Title.md`) _(only if Desktop Integration ON)_
5. **Desktop editor shows changes** (after refresh)

> 📁 **Folders** _(v2.7.0)_ - Notes inside a folder sync to a matching subdirectory, e.g. `/notes/Work/abc-123.json` and `/notes-md/Work/Note_Title.md`. Folders marked **local-only** stay on the device and are never uploaded.

### Workflow: Desktop → Android

1. **Edit Markdown file** (in mounted folder)
2. **Save** - File is immediately on server
3. **In app: Run Markdown import**
   - Settings → "Import Markdown Changes"
   - Or: Auto-import on every sync (future)
4. **App adopts changes** (if desktop version is newer)

### Conflict Resolution: Last-Write-Wins

**Rule:** Newest version (by `updated` timestamp) wins

**Example:**
```
App version:     updated: 2026-01-05 14:00
Desktop version: updated: 2026-01-05 14:30
→ Desktop wins (newer timestamp)
```

**Automatic:**
- ✅ On Markdown import
- ✅ On JSON sync
- ⚠️ No merge conflicts - only complete overwrite

---

## ⚙️ Settings

### Desktop Integration Toggle

**Settings → Desktop Integration**

**ON (activated):**
- ✅ New notes → automatically exported as `.md`
- ✅ Updated notes → `.md` update
- ✅ Deleted notes → `.md` remains (future: also delete)

**OFF (deactivated):**
- ❌ No Markdown export
- ✅ JSON sync continues normally
- ✅ Existing `.md` files remain

### Initial Export

**What happens on activation:**
1. All existing notes are scanned
2. Progress dialog shows progress (e.g., "23/42")
3. Each note is exported as `.md`
4. On errors: Individual note is skipped
5. Success message with number of exported notes

**Time:** ~1-2 seconds per 50 notes

---

## 🛠️ Advanced Usage

### Manual Markdown Creation

You can create `.md` files manually:

```markdown
---
id: 00000000-0000-0000-0000-000000000001
created: 2026-01-05T12:00:00Z
updated: 2026-01-05T12:00:00Z
---

# New Desktop Note

Content here...
```

**⚠️ Important:**
- `id` must be valid UUID (e.g., with uuidgen.io)
- Timestamps in ISO8601 format
- Frontmatter enclosed with `---`

### Bulk Operations

**Edit multiple notes at once:**

1. Mount WebDAV
2. Open all `.md` files in VS Code
3. Find & Replace across all files (Ctrl+Shift+H)
4. Save
5. In app: "Import Markdown Changes"

### Scripting

**Example: Sort all notes by date**

```bash
#!/bin/bash
cd /mnt/notes-md/

# Sort all .md files by update date
for file in *.md; do
  updated=$(grep "^updated:" "$file" | cut -d' ' -f2)
  echo "$updated $file"
done | sort
```

---

## ❌ Troubleshooting

### "404 Not Found" when mounting WebDAV

**Cause:** `/notes-md/` folder doesn't exist

**Solution:**
1. **Perform first sync** - Folder is created automatically
2. OR: Create manually via terminal:
   ```bash
   curl -X MKCOL -u noteuser:password http://server:8080/notes-md/
   ```

### Markdown files don't appear

**Cause:** Desktop integration not activated

**Solution:**
1. Settings → "Desktop Integration" ON
2. Wait for initial export
3. Refresh WebDAV folder

### Changes from desktop don't appear in app

**Cause:** Markdown import not executed

**Solution:**
1. Settings → "Import Markdown Changes"
2. OR: Wait for auto-sync (future feature)

### "Frontmatter missing" error

**Cause:** `.md` file without valid YAML frontmatter

**Solution:**
1. Open file in editor
2. Add frontmatter at the beginning:
   ```yaml
   ---
   id: NEW-UUID-HERE
   created: 2026-01-05T12:00:00Z
   updated: 2026-01-05T12:00:00Z
   ---
   ```
3. Save and import again

---

## 🔒 Security & Best Practices

### Do's ✅

- ✅ **Backup before bulk edits** - Create local backup
- ✅ **One editor at a time** - Don't edit in app AND desktop in parallel
- ✅ **Wait for sync** - Run sync before desktop editing
- ✅ **Respect frontmatter** - Don't change manually (unless you know what you're doing)

### Don'ts ❌

- ❌ **Parallel editing** - App and desktop simultaneously → conflicts
- ❌ **Delete frontmatter** - Note can't be imported anymore
- ❌ **Change IDs** - Note is recognized as new
- ❌ **Manipulate timestamps** - Conflict resolution doesn't work

### Recommended Workflow

```
1. Sync in app (pull-to-refresh)
2. Open desktop
3. Make changes
4. Save
5. In app: "Import Markdown Changes"
6. Verify
7. Run another sync
```

---

## 📊 Comparison: JSON vs Markdown

| Aspect | JSON | Markdown |
|--------|------|----------|
| **Format** | Structured | Flowing text |
| **Readability (human)** | ⚠️ Medium | ✅ Good |
| **Readability (machine)** | ✅ Perfect | ⚠️ Parsing needed |
| **Metadata** | Native | Frontmatter |
| **Editors** | Code editors | All text editors |
| **Sync speed** | ✅ Fast | ⚠️ Slower |
| **Reliability** | ✅ 100% | ⚠️ Frontmatter errors possible |
| **Mobile-first** | ✅ Yes | ❌ No |
| **Desktop-first** | ❌ No | ✅ Yes |

**Conclusion:** Using both formats = Best experience on both platforms!

---

## 🔮 Future Features

Planned for v1.3.0+:

- ⏳ **Auto-Markdown-import** - Automatically on every sync
- ⏳ **Bidirectional sync** - Without manual import
- ⏳ **Markdown preview** - In the app
- ⏳ **Conflict UI** - On simultaneous changes
- ⏳ **Tags in frontmatter** - Synchronized with app
- ⏳ **Attachments** - Images/files in Markdown

---

**📚 See also:**
- [QUICKSTART.en.md](../QUICKSTART.en.md) - App setup
- [FEATURES.en.md](FEATURES.en.md) - Complete feature list
- [BACKUP.en.md](BACKUP.en.md) - Backup & restore

**Last update:** v2.7.0 (2026-05-30)
