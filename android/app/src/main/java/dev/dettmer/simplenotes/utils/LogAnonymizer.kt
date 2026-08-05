package dev.dettmer.simplenotes.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 🆕 v2.14.0: Entfernt identifizierende Daten aus Logs, **bevor sie das Gerät verlassen**.
 *
 * Die Logdateien auf dem Gerät bleiben unangetastet — anonymisiert wird nur die Kopie, die der
 * Export-Button teilt. Damit behält der Nutzer (und der Maintainer auf dem eigenen Gerät) die
 * volle Information, und was in einem GitHub-Issue oder einer Mail landet, ist entschärft.
 *
 * Ersetzt wird:
 * - der Server-Host aus der konfigurierten Sync-URL → `<server>`
 * - der **Pfad-Anteil** der Sync-URL → `<path>`
 * - der WebDAV-Benutzername → `<user>` (steckt bei Nextcloud im Pfad `/dav/files/<user>/`)
 * - **die tatsächlichen Notiztitel** → `<note>`
 * - **die Ordnernamen** → `<folder>`
 * - Markdown-Dateinamen in Pfaden → `/<note>.md`
 *
 * Die Titel kommen aus dem Storage und werden als exakte Strings ersetzt, nicht geraten. Das ist
 * nötig, weil der Sync-Log Titel überwiegend **blank** schreibt (`⏭️ MD skip: <Titel>`,
 * `✅ Imported new: <Titel>`) und nicht als Dateiname — ein Muster auf `.md` würde die Mehrheit
 * der Titel-Leaks verfehlen. Der zusätzliche Pfad-Fall deckt Dateinamen ab, die durch
 * Umlaut-Ersetzung vom Titel abweichen (`Steuererklärung` → `Steuererklaerung.md`) und solche,
 * zu denen es **keine lokale Notiz mehr gibt** — verwaiste MD-Mirrors gelöschter oder
 * umbenannter Notizen bleiben auf dem Server liegen und tauchen weiter im Log auf. Deshalb loggt
 * `MarkdownSyncManager` Ressourcen als `path` und nicht als `name`: nur mit führendem `/` greift
 * [MARKDOWN_PATH] als Auffangnetz für alles, was die Titelliste nicht kennt.
 *
 * Ordnernamen kommen inklusive Tombstones aus dem [dev.dettmer.simplenotes.storage.FolderStore] —
 * gelöschte Ordner stehen noch in wochenalten Logzeilen.
 *
 * Der Pfad ist **nicht** nur Deko: OX App Suite legt den Userstore unter dem Klarnamen an
 * (`/servlet/webdav.infostore/Userstore/Max Mustermann/`). Der steht in keiner Titel-, Ordner-
 * oder Benutzernamen-Liste — der Login ist dort eine Mailadresse — und blieb deshalb bis v2.14.0
 * im exportierten Log stehen. Ersetzt wird der Pfad in roher, percent-enkodierter **und**
 * percent-dekodierter Form (die `path`-Felder der PROPFIND-Antworten tragen die dekodierte):
 * seit v2.14.0 kanonisiert [dev.dettmer.simplenotes.sync.SyncUrlBuilder.getServerUrl] die URL,
 * geloggt wird also `Max%20Mustermann`, während in den Prefs `Max Mustermann` steht — und ältere
 * Logzeilen desselben Tages enthalten noch die rohe Variante.
 *
 * Bewusst **nicht** ersetzt: Notiz-UUIDs. Eine UUID identifiziert niemanden, ist aber der einzige
 * stabile Faden, an dem sich ein Sync-Problem über mehrere Logzeilen verfolgen lässt.
 *
 * Passwörter tauchen in Logs nicht auf: Auth läuft über den OkHttp-Authenticator, es wird kein
 * `Authorization`-Header geloggt, und der Auth-Cache-Key ist ein SHA-256, der nie in ein Log geht.
 */
object LogAnonymizer {
    private const val SERVER_PLACEHOLDER = "<server>"
    private const val PATH_PLACEHOLDER = "<path>"
    private const val USER_PLACEHOLDER = "<user>"
    private const val NOTE_PLACEHOLDER = "<note>"
    private const val FOLDER_PLACEHOLDER = "<folder>"

    /**
     * Kürzester Wert, der noch ersetzt wird.
     *
     * ponytail: Titel wie „ok" oder „TODO" unter dieser Länge bleiben stehen — sie einzeln zu
     * ersetzen würde jedes Vorkommen im Log zerschießen und die Datei unlesbar machen. Wer so
     * kurze Titel für sensibel hält, muss vor dem Senden selbst nachbessern.
     */
    private const val MIN_REPLACEABLE_LENGTH = 3

    /**
     * Dateiname zwischen einem `/` und `.md`. Links durch den Pfadtrenner begrenzt, damit der
     * Ausdruck nicht in den umgebenden Log-Text hineinläuft — Leerzeichen im Dateinamen sind
     * dadurch unproblematisch. Quotes und Klammern begrenzen rechts die Log-Syntax.
     */
    private val MARKDOWN_PATH = Regex("""/[^/\\"'()<>]+\.md""")

    /**
     * @param serverUrl konfigurierte Sync-URL, für die Host-Extraktion. Darf null/leer sein.
     * @param username WebDAV-Benutzername. Darf null/leer sein.
     * @param noteTitles alle bekannten Notiztitel. Leer, wenn sie nicht geladen werden konnten —
     *        dann bleiben Titel im Klartext stehen, der Rest wird trotzdem anonymisiert.
     * @param folderNames alle bekannten Ordnernamen inkl. gelöschter. Gleiche Fallback-Regel.
     */
    fun anonymize(
        text: String,
        serverUrl: String?,
        username: String?,
        noteTitles: Collection<String> = emptyList(),
        folderNames: Collection<String> = emptyList()
    ): String {
        var result = text

        hostOf(serverUrl)?.let { host ->
            result = result.replace(host, SERVER_PLACEHOLDER, ignoreCase = true)
        }

        // Vor dem Benutzernamen: bei Nextcloud steckt der im Pfad, und ein bereits ersetztes
        // `/dav/files/<user>` würde den Pfad-Vergleich nicht mehr treffen.
        basePathsOf(serverUrl).forEach { path ->
            result = result.replace(path, PATH_PLACEHOLDER)
        }

        // Nach dem Host, sonst bliebe ein Benutzername stehen, der auch im Host vorkommt.
        username?.takeIf { it.length >= MIN_REPLACEABLE_LENGTH }?.let { user ->
            result = result.replace(user, USER_PLACEHOLDER)
        }

        // Titel und Ordner in einem Durchgang, längste zuerst: sonst macht „Urlaub" aus
        // „Urlaub 2026" ein „<note> 2026", und ein kurzer Ordnername zersägt einen längeren Titel,
        // der ihn enthält.
        val replacements = noteTitles.map { it to NOTE_PLACEHOLDER } +
            folderNames.map { it to FOLDER_PLACEHOLDER }
        replacements.asSequence()
            .filter { (value, _) -> value.length >= MIN_REPLACEABLE_LENGTH }
            .distinct()
            .sortedByDescending { (value, _) -> value.length }
            .forEach { (value, placeholder) -> result = result.replace(value, placeholder) }

        return MARKDOWN_PATH.replace(result, "/$NOTE_PLACEHOLDER.md")
    }

    /**
     * Host aus einer URL, ohne `java.net.URI` — die wirft bei den halbfertigen URLs, die während
     * der Server-Einrichtung in den Prefs stehen können.
     */
    private fun hostOf(serverUrl: String?): String? {
        val withoutScheme = serverUrl.orEmpty().substringAfter("://", "")
        val host = withoutScheme.substringBefore('/').substringBefore(':')
        return host.takeIf { it.length >= MIN_REPLACEABLE_LENGTH }
    }

    /**
     * Der Pfad-Anteil der Sync-URL mit führendem `/` und ohne Trailing-Slash — roh wie in den
     * Prefs, percent-enkodiert wie in URL-Logzeilen **und** percent-dekodiert wie in den
     * `path`-Feldern der PROPFIND-Antworten. Alle drei, weil in derselben Datei alle
     * Schreibweisen stehen können (siehe Klassen-KDoc); ohne Sonderzeichen im Pfad fallen die
     * Varianten zusammen und `distinct()` lässt eine übrig.
     *
     * Die dekodierte Variante ist nicht optional: seit v2.14.0 kanonisiert
     * [dev.dettmer.simplenotes.sync.SyncUrlBuilder.getServerUrl] die URL, in den Prefs steht also
     * bereits `David%20Jany` — damit wären „roh" und „enkodiert" dieselbe Zeichenkette und der
     * Klarname aus `path='/servlet/webdav.infostore/Userstore/David Jany/…'` bliebe stehen.
     * `MarkdownSyncManager` loggt bewusst `path` statt `name`, der Basispfad hängt deshalb an
     * jeder einzelnen Skip-Zeile.
     *
     * Längste zuerst, damit eine Variante nicht an einem gemeinsamen Präfix einer anderen
     * hängen bleibt.
     */
    private fun basePathsOf(serverUrl: String?): List<String> {
        val raw = serverUrl.orEmpty().substringAfter("://", "").substringAfter('/', "")
        val url = serverUrl?.toHttpUrlOrNull()
        val encoded = url?.encodedPath.orEmpty().removePrefix("/")
        // pathSegments liefert die Segmente bereits dekodiert — kein URLDecoder, der „+" zu
        // einem Leerzeichen machen würde.
        val decoded = url?.pathSegments?.joinToString("/").orEmpty()
        return listOf(raw, encoded, decoded)
            .map { it.trim('/') }
            .filter { it.length >= MIN_REPLACEABLE_LENGTH }
            .distinct()
            .sortedByDescending { it.length }
            .map { "/$it" }
    }
}
