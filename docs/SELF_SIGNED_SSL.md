# Self-Signed SSL Certificate Support

**Since:** v1.7.0  
**Status:** ✅ Supported

---

## Overview

Simple Notes Sync now supports connecting to WebDAV servers with self-signed SSL certificates, such as:
- ownCloud/Nextcloud with self-signed certificates
- Synology NAS with default certificates
- Raspberry Pi or home servers
- Internal corporate servers with private CAs

## How to Use

### Step 1: Export Your Server's CA Certificate

**On your server:**

1. Locate your certificate file (usually `.crt`, `.pem`, or `.der` format)
2. If you created the certificate yourself, you already have it
3. For Synology NAS: Control Panel → Security → Certificate → Export
4. For ownCloud/Nextcloud: Usually in `/etc/ssl/certs/` on the server

### Step 2: Install Certificate on Android

**On your Android device:**

1. **Transfer** the `.crt` or `.pem` file to your phone (via email, USB, etc.)

2. **Open Settings** → Security → More security settings (or Encryption & credentials)

3. **Install from storage** / "Install a certificate"
   - Choose "CA certificate"
   - **Warning:** Android will display a security warning. This is normal.
   - Tap "Install anyway"

4. **Browse** to your certificate file and select it

5. **Name** it something recognizable (e.g., "My ownCloud CA")

6. ✅ **Done!** The certificate is now trusted system-wide

### Step 3: Connect Simple Notes Sync

1. Open Simple Notes Sync
2. Go to **Settings** → **Server Settings**
3. Enter your **`https://` server URL** as usual
4. The app will now trust your self-signed certificate ✅

---

## Security Notes

### ⚠️ Important

- Installing a CA certificate grants trust to **all** certificates signed by that CA
- Only install certificates from sources you trust
- Android will warn you before installation – read the warning carefully

### 🔒 Why This is Safe

- You **manually** install the certificate (conscious decision)
- The app uses Android's native trust store (no custom validation)
- You can remove the certificate anytime from Android Settings
- F-Droid and Google Play compliant (no "trust all" hack)

---

## Troubleshooting

### Certificate Not Trusted

**Problem:** App still shows SSL error after installing certificate

**Solutions:**
1. **Verify installation:** Settings → Security → Trusted credentials → User tab
2. **Check certificate type:** Must be a CA certificate, not a server certificate
3. **Restart app:** Close and reopen Simple Notes Sync
4. **Check URL:** Must use `https://` (not `http://`)

### "Network Security Policy" Error

**Problem:** Android 7+ restricts user certificates for apps

**Solution:** This app is configured to trust user certificates ✅  
If the problem persists, check:
- Certificate is installed in "User" tab (not "System")
- Certificate is not expired
- Server URL matches certificate's Common Name (CN) or Subject Alternative Name (SAN)

### Self-Signed vs. CA-Signed

| Type | Installation Required | Security |
|------|---------------------|----------|
| **Self-Signed** | ✅ Yes | Manual trust |
| **Let's Encrypt** | ❌ No | Automatic |
| **Private CA** | ✅ Yes (CA root) | Automatic for all CA-signed certs |

---

## Alternative: Use Let's Encrypt (Recommended)

If your server is publicly accessible, consider using **Let's Encrypt** for free, automatically-renewed SSL certificates:

- No manual certificate installation needed
- Trusted by all devices automatically
- Easier for end users

**Setup guides:**
- [ownCloud Let's Encrypt](https://doc.owncloud.com/server/admin_manual/installation/letsencrypt/)
- [Nextcloud Let's Encrypt](https://docs.nextcloud.com/server/latest/admin_manual/installation/letsencrypt.html)
- [Synology Let's Encrypt](https://kb.synology.com/en-us/DSM/tutorial/How_to_enable_HTTPS_and_create_a_certificate_signing_request_on_your_Synology_NAS)

---

## Technical Details

### Implementation

- Uses Android's **Network Security Config**
- Trusts both system and user CA certificates
- No custom TrustManager or hostname verifier
- F-Droid and Play Store compliant

### Configuration

File: `android/app/src/main/res/xml/network_security_config.xml`

```xml
<base-config>
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" />  <!-- ← Enables self-signed support -->
    </trust-anchors>
</base-config>
```

---

## FAQ

**Q: Do I need to reinstall the certificate after app updates?**  
A: No, certificates are stored system-wide, not per-app.

**Q: Can I use the same certificate for multiple apps?**  
A: Yes, once installed, it works for all apps that trust user certificates.

**Q: How do I remove a certificate?**  
A: Settings → Security → Trusted credentials → User tab → Tap certificate → Remove

**Q: Does this work on Android 14+?**  
A: Yes, tested on Android 7 through 15 (API 24-35).

---

## Related Issues

- [GitHub Issue #X](link) - User request for ownCloud support
- [Feature Analysis](../project-docs/simple-notes-sync/features/SELF_SIGNED_SSL_CERTIFICATES_ANALYSIS.md) - Technical analysis

---

**Need help?** Open an issue on [GitHub](https://github.com/inventory69/simple-notes-sync/issues)
