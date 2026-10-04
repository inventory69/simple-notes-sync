# Quick Start Guide - Simple Notes Sync 📝

> Step-by-step installation and setup guide

**🌍 Languages:** [Deutsch](QUICKSTART.de.md) · **English**

---

## Prerequisites

- ✅ Android 7.0+ smartphone/tablet
- ✅ Network connection to your server (automatic background sync waits for WiFi)
- ✅ Own server with Docker (optional - for self-hosting)

---

## Option 1: With own server (Self-Hosted) 🏠

### Step 1: Setup WebDAV Server

On your server (e.g. Raspberry Pi, NAS, VPS):

```bash
# Clone repository
git clone https://github.com/inventory69/simple-notes-sync.git
cd simple-notes-sync/server

# Configure environment variables
cp .env.example .env
nano .env
```

**Adjust in `.env`:**
```env
WEBDAV_PASSWORD=your-secure-password-here
```

**Start server:**
```bash
docker compose up -d
```

**Find IP address:**
```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
```

➡️ **Note down:** `http://YOUR-SERVER-IP:8080/`

---

### Step 2: Install App

1. **Download APK:** [Latest version](https://github.com/inventory69/simple-notes-sync/releases/latest)
   - Choose: `simple-notes-sync-vX.X.X-standard.apk`
   
2. **Allow installation:**
   - Android: Settings → Security → Enable "Unknown sources" for your browser
   
3. **Open and install APK**

---

### Step 3: Configure App

1. **Open app**

2. **Open settings** (⚙️ icon top right)

3. **Configure server settings:**
   
   | Field | Value |
   |------|------|
   | **WebDAV Server URL** | `http://YOUR-SERVER-IP:8080/` |
   | **Username** | `noteuser` |
   | **Password** | (your password from `.env`) |

   > **💡 Note:** Enter only the base URL (without `/notes`). The app automatically creates `/notes/` for JSON files and `/notes-md/` for Markdown export.

4. **Press "Test connection"**
   - ✅ Success? → Continue to step 4
   - ❌ Error? → See [Troubleshooting](#-troubleshooting)

5. **Enable auto-sync** (toggle switch)

6. **Choose sync interval:**
   - **15 min** - Maximum currency (~0.8% battery/day)
   - **30 min** - Recommended (~0.4% battery/day) ⭐
   - **60 min** - Maximum battery life (~0.2% battery/day)

---

### Step 4: Create First Note

1. Back to main view (← arrow)

2. **"Add note"** (+ icon)

3. Enter title and text

4. **Save** (💾 icon)

5. **Wait for auto-sync** (or manually: ⚙️ → "Sync now")

🎉 **Done!** Your notes will be automatically synchronized!

---

## Option 2: Local notes only (no server) 📱

You can also use Simple Notes **without a server**:

1. **Install app** (see step 2 above)

2. **Use without server configuration:**
   - Notes are only stored locally
   - No auto-sync
   - Perfect for offline-only use

---

## 🔋 Disable Battery Optimization

For reliable auto-sync:

1. **Settings** → **Apps** → **Simple Notes Sync**

2. **Battery** → **Battery usage**

3. Select: **"Don't optimize"** or **"Unrestricted"**

💡 **Note:** Android Doze Mode may still delay sync in standby (~60 min). This is normal and affects all apps.

---

## 📊 Sync Intervals in Detail

| Interval | Syncs/day | Battery/day | Battery/sync | Use case |
|-----------|-----------|----------|-----------|----------------|
| **15 min** | ~96 | ~0.8% (~23 mAh) | ~0.008% | ⚡ Maximum currency (multiple devices) |
| **30 min** | ~48 | ~0.4% (~12 mAh) | ~0.008% | ✓ **Recommended** - balanced |
| **60 min** | ~24 | ~0.2% (~6 mAh) | ~0.008% | 🔋 Maximum battery life |

---

## 🐛 Troubleshooting

### Connection test fails

**Problem:** "Connection failed" during test

**Solutions:**

1. **Server running?**
   ```bash
   docker compose ps
   # Should show "Up"
   ```

2. **Same network?**
   - Smartphone and server must be on same network

3. **IP address correct?**
   ```bash
   ip addr show | grep "inet "
   # Check if IP in URL matches
   ```

4. **Firewall?**
   ```bash
   # Open port 8080 (if firewall active)
   sudo ufw allow 8080/tcp
   ```

5. **Check server logs:**
   ```bash
   docker compose logs -f
   ```

---

### Auto-sync not working

**Problem:** Notes are not automatically synchronized

**Solutions:**

1. **Auto-sync enabled?**
   - ⚙️ Settings → Toggle "Auto-sync" must be **ON**

2. **Battery optimization disabled?**
   - See [Disable Battery Optimization](#-disable-battery-optimization)

3. **Connected to WiFi?**
   - Auto-sync triggers on any WiFi connection
   - Check if you're connected to a WiFi network

4. **Test manually:**
   - ⚙️ Settings → "Sync now"
   - Works? → Auto-sync should work too

---

### Notes not showing up

**Problem:** After installation, no notes visible even though they exist on server

**Solution:**

1. **Manually sync once:**
   - ⚙️ Settings → "Sync now"

2. **Check server data:**
   ```bash
   docker compose exec webdav ls -la /data/
   # Should show .json files
   ```

---

### Sync errors

**Problem:** Error message during sync

**Solutions:**

1. **"401 Unauthorized"** → Wrong password
   - Check password in app settings
   - Compare with `.env` on server

2. **"404 Not Found"** → Wrong URL
   - Should end with `/` (e.g. `http://192.168.1.100:8080/`)

3. **"Network error"** → No connection
   - See [Connection test fails](#connection-test-fails)

---

## 📱 Updates

### Automatic with Obtainium (recommended)

1. **[Install Obtainium](https://github.com/ImranR98/Obtainium/releases/latest)**

2. **Add app:**
   - URL: `https://github.com/inventory69/simple-notes-sync`
   - Enable auto-update

3. **Done!** Obtainium notifies you of new versions

### Manual

1. Download new APK from [Releases](https://github.com/inventory69/simple-notes-sync/releases/latest)

2. Install (overwrites old version)

3. All data remains intact!

---

## 🆘 Further Help

- **GitHub Issues:** [Report problem](https://github.com/inventory69/simple-notes-sync/issues)
- **Complete docs:** [DOCS.md](docs/DOCS.md)
- **Server setup details:** [server/README.md](server/README.md)

---

**Version:** 1.1.0 · **Created:** December 2025
