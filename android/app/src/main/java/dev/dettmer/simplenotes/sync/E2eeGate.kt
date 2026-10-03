package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.io.IOException
import java.io.InputStream

/**
 * 🆕 v2.20.0 (E2EE-Slice 1): Erkennt, dass ein anderes Gerät den Sync-Ordner Ende-zu-Ende
 * verschlüsselt hat, und hält dann jeden Server-Zugriff an. Diese Version verschlüsselt selbst
 * nicht. Ohne das Gate schriebe sie still in einen Ordner, den niemand mehr liest, und meldete
 * „synchronisiert".
 *
 * Vertrag (beide Clients, Testvektoren): `project-docs/simple-notes-sync/e2ee/slice-1.md`.
 * Bleibt in der Sync-Schicht: nicht in NotesStorage, Application.onCreate oder Widgets.
 */
internal object E2eeGate {
    enum class Probe { ACTIVE, INACTIVE, ERROR }

    private const val TAG = "E2eeGate"

    /** Mit Anführungszeichen: sonst sperrte eine Soft-404-Seite, die den Pfad `/simple-notes-e2ee/` zurückgibt. */
    private const val MARKER = "\"simple-notes-e2ee\""
    private const val MAX_BODY_BYTES = 64 * 1024
    private const val HTTP_OK = 200
    private val TRANSIENT_4XX = setOf(401, 407, 408, 425, 429)

    /** Vertragstabelle. [bodyPrefix] sind höchstens die ersten 64 KiB, siehe [readPrefix]. */
    @Suppress("MagicNumber") // HTTP-Statusklassen
    fun classify(code: Int, bodyPrefix: String?): Probe = when (code) {
        in 200..299 -> if (bodyPrefix?.contains(MARKER) == true) Probe.ACTIVE else Probe.INACTIVE
        in TRANSIENT_4XX -> Probe.ERROR
        in 400..499 -> Probe.INACTIVE
        else -> Probe.ERROR
    }

    /** Ohne `readNBytes` (erst API 33). Ein längerer Body wird nicht weitergelesen. */
    fun readPrefix(input: InputStream): String {
        val buf = ByteArray(MAX_BODY_BYTES)
        var n = 0
        while (n < buf.size) {
            val read = input.read(buf, n, buf.size - n)
            if (read < 0) break
            n += read
        }
        return String(buf, 0, n, Charsets.UTF_8)
    }

    /**
     * Ein GET auf den Marker. Bei [Probe.ERROR] fliegt die ursprüngliche Exception weiter, damit
     * der [SyncExceptionMapper] 401 weiter als Auth-Fehler meldet und der Lauf ohne Schreibzugriff
     * endet. Direktpfade außerhalb des Syncs werten das fail-closed aus.
     */
    fun isActive(webdav: WebDavClient, markerUrl: String): Boolean {
        val probe = try {
            // get() wirft bei jedem Nicht-2xx, welcher 2xx es war, ändert nichts.
            webdav.get(markerUrl).use { classify(HTTP_OK, readPrefix(it)) }
        } catch (e: WebDavException) {
            classify(e.statusCode, null).also { if (it == Probe.ERROR) throw e }
        }
        Logger.d(TAG, "e2ee marker probe: $probe")
        return probe == Probe.ACTIVE
    }

    /**
     * Direktpfade außerhalb des Syncs: frisch prüfen, **fail-closed** (Prüffehler = gesperrt),
     * weil dort kein nächster Lauf nachfasst. Schreibt keinen Zustand.
     */
    fun isLocked(webdav: WebDavClient, markerUrl: String): Boolean = try {
        isActive(webdav, markerUrl)
    } catch (e: IOException) {
        Logger.w(TAG, "e2ee marker probe failed, treating server as locked: ${e.message}")
        true
    }

    /** Gesperrt, solange die gespeicherte Marker-URL die der aktuellen Konfiguration ist. Kein Request. */
    fun isBlocked(prefs: SharedPreferences, urlBuilder: SyncUrlBuilder): Boolean {
        val stored = prefs.getString(Constants.KEY_E2EE_BLOCKED_MARKER, null) ?: return false
        val serverUrl = urlBuilder.getServerUrl() ?: return false
        return stored == urlBuilder.getE2eeMarkerUrl(serverUrl)
    }

    /**
     * Zustand nach einer Prüfung. **Nur** der Sync ruft das: Restore und Direktpfade prüfen
     * Konfigurationen, die gleich wieder verworfen werden können. Protokoll nur beim Übergang,
     * sonst füllte jeder periodische Lauf das Aktivitätsprotokoll.
     */
    fun record(prefs: SharedPreferences, markerUrl: String, active: Boolean) {
        val stored = prefs.getString(Constants.KEY_E2EE_BLOCKED_MARKER, null)
        if (active && stored != markerUrl) {
            prefs.edit { putString(Constants.KEY_E2EE_BLOCKED_MARKER, markerUrl) }
            Logger.w(TAG, "🔒 Sync folder is end-to-end encrypted - sync paused")
            ActivityLog.log(ActivityLog.Op.SYNC_BLOCKED, ActivityLog.Src.LOCAL, why = "e2ee_active")
        } else if (!active && stored != null) {
            prefs.edit { remove(Constants.KEY_E2EE_BLOCKED_MARKER) }
            Logger.i(TAG, "🔓 Sync resumed (marker gone or another folder)")
            // Nach einem Ordnerwechsel weg vom gesperrten Ordner ist nichts „aufgehoben" worden.
            if (stored == markerUrl) ActivityLog.log(ActivityLog.Op.SYNC_UNBLOCKED, ActivityLog.Src.LOCAL)
        }
    }
}
