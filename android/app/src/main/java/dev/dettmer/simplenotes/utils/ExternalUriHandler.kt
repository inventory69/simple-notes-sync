package dev.dettmer.simplenotes.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

/**
 * Öffnet Links aus Notizen und Settings in einem **eigenen Task**.
 *
 * Composes Default-`AndroidUriHandler` ruft `startActivity` ohne Flags auf. Aus einer Activity
 * heraus landet die fremde App damit auf *unserem* Task-Stack, sobald sie `standard`-launchMode
 * hat und kein `allowTaskReparenting` setzt — z.B. die ARTE-TV-App. Danach holt der Launcher
 * beim Tippen auf das Simple-Notes-Icon immer diesen Task nach vorn: Der Nutzer sieht die fremde
 * App statt seiner Notizen, bis er die Karte aus "Zuletzt verwendet" wischt. Per Mail gemeldet,
 * im Emulator mit einer Stellvertreter-App reproduziert.
 *
 * `FLAG_ACTIVITY_NEW_TASK` schickt die fremde Activity in ihren eigenen Task. Der Fang von
 * [ActivityNotFoundException] kommt dazu, weil der Default-Handler dort eine
 * `IllegalArgumentException` wirft und die App mitreißt (etwa bei `foo://` in einer Notiz).
 */
class ExternalUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Kein e.message: das enthält den kompletten Intent samt URL aus der Notiz.
            Logger.w(TAG, "No app installed to open this link")
        }
    }

    private companion object {
        const val TAG = "ExternalUriHandler"
    }
}
