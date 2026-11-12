package dev.dettmer.simplenotes.sync.webdav

import dev.dettmer.simplenotes.utils.Logger
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

private const val TAG = "PropfindParser"

/**
 * 🆕 v2.14.0: Minimaler Parser für PROPFIND-`multistatus`-Antworten.
 *
 * Bewusst SAX (`javax.xml.parsers`, Plattform-API auf Android **und** JVM) statt
 * XmlPullParser: streamend (kein DOM-Peak bei großen Ordnern), keine Dependency,
 * und in reinen Unit-Tests ohne Robolectric lauffähig.
 *
 * Namespace-tolerant: verglichen wird nur der Local-Name, der Prefix (`D:`, `d:`,
 * `lp1:` …) wird abgeschnitten. Properties werden ausschließlich aus
 * `<propstat>`-Blöcken mit HTTP-Status 200 übernommen — 404-Blöcke (vom Server nicht
 * unterstützte Properties) werden ignoriert.
 */
object PropfindParser {
    /**
     * @param input XML-Stream der Server-Antwort. Wird **nicht** geschlossen —
     *              das übernimmt der Aufrufer via `response.use { }`.
     */
    fun parse(input: InputStream): List<WebDavResource> {
        val handler = MultistatusHandler()
        val reader = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            // XXE-Schutz: die Antwort kommt von einem fremden Server.
            // Feature-Namen werden nicht von jeder Implementierung unterstützt → best effort,
            // der EntityResolver unten ist der harte Riegel.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.entityResolver = EntityResolver { _, _ -> InputSource(java.io.StringReader("")) }
        reader.parse(InputSource(AttributeStrippingStream(input)))
        return handler.resources
    }
}

private const val LT = '<'.code
private const val GT = '>'.code
private const val SLASH = '/'.code
private const val QUESTION = '?'.code
private const val EXCLAMATION = '!'.code
private const val DASH = '-'.code
private const val BRACKET_OPEN = '['.code
private const val BRACKET_CLOSE = ']'.code
private const val QUOT = '"'.code
private const val APOS = '\''.code
private const val SPACE = ' '.code
private const val DROP = -1

/**
 * 🆕 Issue #175: Reicht die Antwort ohne Attribute an den Parser weiter. Manche Server schicken
 * Start-Tags mit doppeltem Attribut, daran bricht jeder konforme Parser mit "duplicate attribute"
 * ab und mit ihm das ganze Listing. [MultistatusHandler] liest keine Attribute (nicht
 * namespace-aware, nur Local-Names), es fehlt also nichts. Kommentare, CDATA, PIs (samt
 * XML-Deklaration mit `encoding`) und End-Tags laufen unverändert durch.
 *
 * ponytail: arbeitet auf Bytes, Markup ist ASCII, das trägt UTF-8 und Latin-1. Eine UTF-16-Antwort
 * würde zerlegt; schickt ein Server je eine, am BOM erkennen und ungefiltert durchreichen.
 */
private class AttributeStrippingStream(private val source: InputStream) : InputStream() {
    private enum class State { TEXT, TAG_OPEN, NAME, ATTRS, END_TAG, BANG, COMMENT, CDATA, DECL, PI }

    private var state = State.TEXT
    private var quote = 0
    private var selfClosing = false
    private var pending = DROP
    private var prev1 = 0
    private var prev2 = 0

    override fun read(): Int {
        if (pending != DROP) return pending.also { pending = DROP }
        while (true) {
            val b = source.read()
            if (b < 0) return -1
            val out = step(b)
            prev2 = prev1
            prev1 = b
            if (out != DROP) return out
        }
    }

    /** Auszugebendes Byte oder [DROP]. */
    private fun step(b: Int): Int = when (state) {
        State.TEXT -> b.also { if (it == LT) state = State.TAG_OPEN }
        State.TAG_OPEN -> b.also { state = afterLt(it) }
        State.NAME -> name(b)
        State.ATTRS -> attrs(b)
        State.BANG -> b.also { state = afterBang(it) }
        State.END_TAG, State.DECL -> b.also { if (it == GT) state = State.TEXT }
        State.PI -> b.also { if (it == GT && prev1 == QUESTION) state = State.TEXT }
        State.COMMENT -> b.also { if (it == GT && prev1 == DASH && prev2 == DASH) state = State.TEXT }
        State.CDATA -> b.also { if (it == GT && prev1 == BRACKET_CLOSE && prev2 == BRACKET_CLOSE) state = State.TEXT }
    }

    private fun afterLt(b: Int) = when (b) {
        SLASH -> State.END_TAG
        QUESTION -> State.PI
        EXCLAMATION -> State.BANG
        else -> State.NAME
    }

    private fun afterBang(b: Int) = when (b) {
        DASH -> State.COMMENT
        BRACKET_OPEN -> State.CDATA
        else -> State.DECL
    }

    private fun name(b: Int): Int {
        if (b == GT) state = State.TEXT
        if (b != SLASH && b > SPACE) return b
        state = State.ATTRS
        selfClosing = b == SLASH
        quote = 0
        return DROP
    }

    private fun attrs(b: Int): Int {
        when {
            quote != 0 -> if (b == quote) quote = 0
            b == QUOT || b == APOS -> quote = b
            b == SLASH -> selfClosing = true
            b == GT -> {
                state = State.TEXT
                if (!selfClosing) return GT
                pending = GT
                return SLASH
            }
            b > SPACE -> selfClosing = false
        }
        return DROP
    }
}

private const val COLLECTION_CONTENT_TYPE = "httpd/unix-directory"
private const val NO_CONTENT_LENGTH = -1L

/**
 * Datumsformate für `getlastmodified`. RFC 1123 ist der Standardfall; die beiden
 * anderen decken Server ab, die sich nicht daran halten. Reihenfolge = Häufigkeit.
 */
private val DATE_PATTERNS = listOf(
    "EEE, dd MMM yyyy HH:mm:ss zzz",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "EEE MMM d HH:mm:ss yyyy"
)

private class MultistatusHandler : DefaultHandler() {
    val resources = mutableListOf<WebDavResource>()

    private val formats = DATE_PATTERNS.map {
        SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
    }
    private val text = StringBuilder()

    // Zustand der aktuellen <response>
    private var href: String? = null
    private var modified: Date? = null
    private var contentLength: Long = NO_CONTENT_LENGTH
    private var isDirectory = false
    private var etag: String? = null

    // Zustand des aktuellen <propstat> — erst bei Status 200 übernommen
    private var inPropstat = false
    private var inResourcetype = false
    private var blockOk = false
    private var blockModified: Date? = null
    private var blockLength: Long = NO_CONTENT_LENGTH
    private var blockIsDirectory = false
    private var blockEtag: String? = null

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        text.setLength(0)
        when (local(qName)) {
            "response" -> resetResponse()
            "propstat" -> resetBlock()
            "resourcetype" -> inResourcetype = true
            "collection" -> if (inResourcetype) blockIsDirectory = true
        }
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if (ch != null) text.appendRange(ch, start, start + length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val value = text.toString().trim()
        when (local(qName)) {
            // Nur der erste href zählt — <lockdiscovery> kann weitere enthalten.
            "href" -> if (href == null) href = value
            "getlastmodified" -> blockModified = parseDate(value)
            "getcontentlength" -> blockLength = value.toLongOrNull() ?: NO_CONTENT_LENGTH
            "getetag" -> blockEtag = value.takeIf { it.isNotEmpty() }
            "getcontenttype" -> if (value == COLLECTION_CONTENT_TYPE) blockIsDirectory = true
            "resourcetype" -> inResourcetype = false
            "status" -> if (inPropstat) blockOk = value.contains(" 200")
            "propstat" -> commitBlock()
            "response" -> emitResource()
        }
        text.setLength(0)
    }

    private fun local(qName: String?): String =
        qName.orEmpty().substringAfterLast(':').lowercase(Locale.US)

    private fun resetResponse() {
        href = null
        modified = null
        contentLength = NO_CONTENT_LENGTH
        isDirectory = false
        etag = null
        resetBlock()
    }

    private fun resetBlock() {
        inPropstat = true
        inResourcetype = false
        blockOk = false
        blockModified = null
        blockLength = NO_CONTENT_LENGTH
        blockIsDirectory = false
        blockEtag = null
    }

    private fun commitBlock() {
        if (blockOk) {
            blockModified?.let { modified = it }
            if (blockLength != NO_CONTENT_LENGTH) contentLength = blockLength
            if (blockIsDirectory) isDirectory = true
            blockEtag?.let { etag = it }
        }
        inPropstat = false
    }

    private fun emitResource() {
        val rawHref = href ?: return
        // Server mit nicht-URI-konformen hrefs (unkodierte Leerzeichen o. ä.):
        // 🆕 Issue #128: erst mit escapten Leerzeichen erneut versuchen — ein verworfener
        // Ordner-Eintrag ließ dessen Notizen früher als „auf dem Server gelöscht" erscheinen.
        // Erst wenn auch das scheitert, Eintrag überspringen statt die ganze Liste zu verlieren.
        val uri = parseHref(rawHref) ?: parseHref(rawHref.replace(" ", "%20")) ?: run {
            Logger.w(TAG, "Dropping resource with unparsable href: $rawHref")
            return
        }
        resources.add(WebDavResource(uri, modified, contentLength, isDirectory, etag))
    }

    private fun parseHref(value: String): URI? = try {
        URI(value)
    } catch (_: URISyntaxException) {
        null
    }

    private fun parseDate(value: String): Date? {
        if (value.isEmpty()) return null
        for (format in formats) {
            try {
                return format.parse(value)
            } catch (_: ParseException) {
                // nächstes Format probieren
            }
        }
        return null
    }
}
