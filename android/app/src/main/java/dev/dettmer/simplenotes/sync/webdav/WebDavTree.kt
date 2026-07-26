package dev.dettmer.simplenotes.sync.webdav

import dev.dettmer.simplenotes.utils.Logger
import java.net.URI

private const val TAG = "WebDavTree"

/**
 * Statuscodes, mit denen Server ein `Depth: infinity` ablehnen:
 * 403 (sabre/dav `propfind-finite-depth`), 400 (generelle Ablehnung), 507 (Ergebnis zu groß).
 */
private val DEPTH_INFINITY_REFUSED = setOf(400, 403, 507)

/** Die App kennt genau eine Ordnerebene unterhalb der Basis. */
private const val MAX_DEPTH = 2

/**
 * 🆕 v2.14.0: Holt die komplette Hierarchie unter [baseUrl] mit **einem** PROPFIND und gruppiert
 * sie nach Ordnername — `null` als Key steht für die Basis-Ebene.
 *
 * Ersetzt das bisherige 1+N-Listing (ein PROPFIND für die Basis, je eins pro Unterordner). Der
 * Gewinn wächst linear mit der Ordnerzahl.
 *
 * Gibt `null` zurück, wenn der Server kein `Depth: infinity` kann oder der Request scheitert —
 * dann muss der Aufrufer sein bisheriges Einzel-Listing fahren. Lehnt der Server ab
 * ([DEPTH_INFINITY_REFUSED]), wird [onRefused] gerufen, damit der Fehlversuch nicht bei jedem
 * Sync erneut anfällt.
 *
 * Die Basis-Gruppe enthält Dateien **und** Unterverzeichnisse (wie ein `Depth: 1`-Listing, nur
 * ohne den Self-Eintrag); jedes Unterverzeichnis ist zusätzlich ein eigener Key — auch wenn es
 * leer ist. Damit unterscheidet der Aufrufer „Ordner ist leer" von „Ordner nicht im Ergebnis".
 */
fun WebDavClient.listTreeOrNull(baseUrl: String, onRefused: () -> Unit): Map<String?, List<WebDavResource>>? {
    val resources = try {
        listDeep(baseUrl)
    } catch (e: WebDavException) {
        if (e.statusCode in DEPTH_INFINITY_REFUSED) {
            Logger.d(TAG, "Server refuses Depth: infinity (${e.statusCode}) — falling back to per-folder listings")
            onRefused()
        } else {
            Logger.w(TAG, "deep PROPFIND failed (${e.statusCode}), falling back: ${e.message}")
        }
        return null
    } catch (e: Exception) {
        Logger.w(TAG, "deep PROPFIND failed, falling back: ${e.message}")
        return null
    }
    // Ein PROPFIND auf eine existierende Collection enthält immer mindestens deren Self-Eintrag.
    // Eine leere Antwort ist also nicht verwertbar — klassisch listen statt „Server ist leer"
    // anzunehmen (sonst sähe der Sync alle Notizen als serverseitig gelöscht).
    if (resources.isEmpty()) {
        Logger.w(TAG, "deep PROPFIND returned no resources for $baseUrl, falling back")
        return null
    }
    return resources.groupByFolder(baseUrl)
}

/**
 * Gruppiert ein Deep-Listing nach dem ersten Pfadsegment unterhalb von [baseUrl].
 * Tiefer verschachtelte Einträge fallen raus — die App legt keine Unter-Unterordner an.
 */
internal fun List<WebDavResource>.groupByFolder(baseUrl: String): Map<String?, List<WebDavResource>> {
    val basePath = URI(baseUrl).path.orEmpty().trimEnd('/')
    val grouped = linkedMapOf<String?, MutableList<WebDavResource>>(null to mutableListOf())
    for (resource in this) {
        val relative = resource.path.removeSuffix("/").removePrefix(basePath).trim('/')
        if (relative.isEmpty()) continue // die Basis selbst
        val segments = relative.split('/')
        when {
            // Direktes Kind: kommt in die Basis-Gruppe; Verzeichnisse zusätzlich als eigener Key.
            segments.size == 1 -> {
                grouped.getValue(null).add(resource)
                if (resource.isDirectory) grouped.getOrPut(resource.name) { mutableListOf() }
            }
            segments.size == MAX_DEPTH && !resource.isDirectory ->
                grouped.getOrPut(segments[0]) { mutableListOf() }.add(resource)
        }
    }
    return grouped
}
