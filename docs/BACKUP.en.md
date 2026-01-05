# Backup & Restore 💾

**🌍 Languages:** [Deutsch](BACKUP.md) · **English**

> Secure your notes locally - independent from the server

---

## 📋 Overview

The backup system works **completely offline** and independent from the WebDAV server. Perfect for:
- 📥 Regular backups
- 📤 Migration to new server
- 🔄 Recovery after data loss
- 💾 Archiving old notes

---

## 📥 Create Backup

### Step-by-Step

1. **Open settings** (⚙️ icon top right)
2. **Find "Backup & Restore"** section
3. **Tap "📥 Create backup"**
4. **Choose location:**
   - 📁 Downloads
   - 💳 SD card
   - ☁️ Cloud folder (Nextcloud, Google Drive, etc.)
   - 📧 Email as attachment
5. **Done!** Backup file is saved

### File Format

**Filename:** `simplenotes_backup_YYYY-MM-DD_HHmmss.json`

**Example:** `simplenotes_backup_2026-01-05_143022.json`

**Content:**
```json
{
  "version": "1.2.1",
  "exported_at": "2026-01-05T14:30:22Z",
  "notes_count": 42,
  "notes": [
    {
      "id": "abc-123-def",
      "title": "Shopping List",
      "content": "Milk\nBread\nCheese",
      "createdAt": 1704467422000,
      "updatedAt": 1704467422000
    }
  ]
}
```

**Format details:**
- ✅ Human-readable (formatted JSON)
- ✅ All data included (title, content, IDs, timestamps)
- ✅ Version info for compatibility
- ✅ Note count for validation

---

## 📤 Restore Backup

### 3 Restore Modes

#### 1. Merge ⭐ _Recommended_

**What happens:**
- ✅ New notes from backup are added
- ✅ Existing notes remain unchanged
- ✅ No data loss

**When to use:**
- Import backup from another device
- Recover old notes
- Restore accidentally deleted notes

**Example:**
```
App:    [Note A, Note B, Note C]
Backup: [Note A, Note D, Note E]
Result: [Note A, Note B, Note C, Note D, Note E]
```

#### 2. Replace

**What happens:**
- ❌ ALL existing notes are deleted
- ✅ Backup notes are imported
- ⚠️ Irreversible (except through auto-backup)

**When to use:**
- Server migration (complete restart)
- Return to old backup state
- App reinstallation

**Example:**
```
App:    [Note A, Note B, Note C]
Backup: [Note X, Note Y]
Result: [Note X, Note Y]
```

**⚠️ Warning:** Automatic safety backup is created!

#### 3. Overwrite Duplicates

**What happens:**
- ✅ New notes from backup are added
- 🔄 On ID conflicts, backup wins
- ✅ Other notes remain unchanged

**When to use:**
- Backup is newer than app data
- Import desktop changes
- Conflict resolution

**Example:**
```
App:    [Note A (v1), Note B, Note C]
Backup: [Note A (v2), Note D]
Result: [Note A (v2), Note B, Note C, Note D]
```

### Restore Process

1. **Settings** → **"📤 Restore from file"**
2. **Select backup file** (`.json`)
3. **Choose mode:**
   - 🔵 Merge _(Default)_
   - 🟡 Overwrite duplicates
   - 🔴 Replace _(Caution!)_
4. **Confirm** - Automatic safety backup is created
5. **Wait** - Import runs
6. **Done!** - Success message with number of imported notes

---

## 🛡️ Automatic Safety Backup

**Before every restore:**
- ✅ Automatic backup is created
- 📁 Saved in: `Android/data/dev.dettmer.simplenotes/files/`
- 🏷️ Filename: `auto_backup_before_restore_YYYY-MM-DD_HHmmss.json`
- ⏱️ Timestamp: Right before restore

**Why?**
- Protection against accidental "Replace"
- Ability to undo
- Double security

**Access via file manager:**
```
/Android/data/dev.dettmer.simplenotes/files/auto_backup_before_restore_*.json
```

---

## 💡 Best Practices

### Backup Strategy

#### Regular Backups
```
Daily:   ❌ Too often (server sync is enough)
Weekly:  ✅ Recommended for important notes
Monthly: ✅ Archiving
Before updates: ✅ Safety
```

#### 3-2-1 Rule
1. **3 copies** - Original + 2 backups
2. **2 media** - e.g., SD card + cloud
3. **1 offsite** - e.g., cloud storage

### Backup Locations

**Local (fast):**
- 📱 Internal storage / Downloads
- 💳 SD card
- 🖥️ PC (via USB)

**Cloud (secure):**
- ☁️ Nextcloud (self-hosted)
- 📧 Email to yourself
- 🗄️ Syncthing (sync between devices)

**⚠️ Avoid:**
- ❌ Google Drive / Dropbox (privacy)
- ❌ Only one copy
- ❌ Only on server (if server fails)

---

## 🔧 Advanced Usage

### Edit Backup File

The `.json` file can be edited with any text editor:

1. **Open with:** VS Code, Notepad++, nano
2. **Add/remove notes**
3. **Change title/content**
4. **Adjust IDs** (for migration)
5. **Save** and import to app

**⚠️ Important:**
- Keep valid JSON format
- IDs must be unique (UUIDs)
- Timestamps in milliseconds (Unix Epoch)

### Bulk Import

Merge multiple backups:

1. Import backup 1 (Mode: Merge)
2. Import backup 2 (Mode: Merge)
3. Import backup 3 (Mode: Merge)
4. Result: All notes combined

### Server Migration

Step-by-step:

1. **Create backup** on old server
2. **Set up new server** (see [QUICKSTART.en.md](QUICKSTART.en.md))
3. **Change server URL** in app settings
4. **Restore backup** (Mode: Replace)
5. **Test sync** - All notes on new server

---

## ❌ Troubleshooting

### "Invalid backup file"

**Causes:**
- Corrupt JSON file
- Wrong file extension (must be `.json`)
- Incompatible app version

**Solution:**
1. Check JSON file with validator (e.g., jsonlint.com)
2. Verify file extension
3. Create backup with current app version

### "No permission to save"

**Causes:**
- Storage permission missing
- Write-protected folder

**Solution:**
1. Android: Settings → Apps → Simple Notes → Permissions
2. Activate "Storage"
3. Choose different location

### "Import failed"

**Causes:**
- Not enough storage space
- Corrupt backup file
- App crash during import

**Solution:**
1. Free up storage space
2. Create new backup file
3. Restart app and try again

---

## 🔒 Security & Privacy

### Data Protection
- ✅ **Locally stored** - No cloud upload without your action
- ✅ **No encryption** - Plain text format for readability
- ⚠️ **Sensitive data?** - Encrypt backup file yourself (e.g., 7-Zip with password)

### Recommendations
- 🔐 Store backup files in encrypted container
- 🗑️ Regularly delete old backups
- 📧 Don't send via unencrypted email
- ☁️ Use self-hosted cloud (Nextcloud)

---

## 📊 Technical Details

### Format Specification

**JSON structure:**
```json
{
  "version": "string",        // App version at export
  "exported_at": "ISO8601",   // Export timestamp
  "notes_count": number,      // Number of notes
  "notes": [
    {
      "id": "UUID",           // Unique ID
      "title": "string",      // Note title
      "content": "string",    // Note content
      "createdAt": number,    // Unix timestamp (ms)
      "updatedAt": number     // Unix timestamp (ms)
    }
  ]
}
```

### Compatibility
- ✅ v1.2.0+ - Fully compatible
- ⚠️ v1.1.x - Basic functions (without auto-backup)
- ❌ v1.0.x - Not supported

---

**📚 See also:**
- [QUICKSTART.en.md](../QUICKSTART.en.md) - App installation and setup
- [FEATURES.en.md](FEATURES.en.md) - Complete feature list
- [DESKTOP.en.md](DESKTOP.en.md) - Desktop integration with Markdown

**Last update:** v1.2.1 (2026-01-05)
