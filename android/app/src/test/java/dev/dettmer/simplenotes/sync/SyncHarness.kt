package dev.dettmer.simplenotes.sync

import android.content.Context
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/**
 * Test-Fixture für ganze Sync-Zyklen: der echte [WebDavSyncService] gegen [FakeWebDav], einen
 * Mini-WebDAV im Speicher. Gebaut für E2EE-Slice 1, wiederverwendbar für Slice 3 und 4.
 *
 * Statt Android: ein relaxter [Context]-Mock mit Temp-`filesDir`, je Prefs-Name eine
 * [FakeSharedPreferences], `getString` liefert `R:<id>`. Zugangsdaten über `mockkObject`.
 *
 * Aufrufer: `@After` muss [close] rufen (Server, Mocks, Auth-Cache, [SyncStateManager]).
 */
internal class SyncHarness {
    val dav = FakeWebDav()
    val server = MockWebServer().apply {
        dispatcher = dav
        start()
    }
    private val dir: File = Files.createTempDirectory("sync-harness").toFile()
    private val prefsByName = mutableMapOf<String, FakeSharedPreferences>()

    val context: Context = mockk(relaxed = true) {
        every { applicationContext } returns this@mockk
        every { filesDir } returns File(dir, "files").apply { mkdirs() }
        every { cacheDir } returns File(dir, "cache").apply { mkdirs() }
        every { getSharedPreferences(any(), any()) } answers { prefsByName.getOrPut(firstArg()) { FakeSharedPreferences() } }
        every { getString(any()) } answers { "R:${firstArg<Int>()}" }
        every { getString(any(), *anyVararg()) } answers { "R:${firstArg<Int>()}" }
    }
    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    val storage = NotesStorage(context)

    init {
        ConnectionManager.clearSharedAuthCacheForTest()
        mockkObject(CredentialStore)
        every { CredentialStore.getUsername(any()) } returns "user"
        every { CredentialStore.getPassword(any()) } returns "pw"
        every { CredentialStore.hasCredentials(any()) } returns true
        ActivityLog.init(context)
        prefs.edit()
            .putString(Constants.KEY_SERVER_URL, server.url("/").toString())
            .putString(Constants.KEY_SYNC_FOLDER_NAME, "notes")
            .apply()
    }

    /** In `runTest` mit `UnconfinedTestDispatcher(testScheduler)` bauen, sonst zwei Scheduler. */
    fun service(ioDispatcher: CoroutineDispatcher) = WebDavSyncService(context, ioDispatcher)

    fun activityLog(): List<ActivityLog.Entry> = ActivityLog.readTail(context, maxLines = 100)

    fun close() {
        unmockkAll()
        SyncStateManager.reset()
        ConnectionManager.clearSharedAuthCacheForTest()
        server.shutdown()
        dir.deleteRecursively()
    }
}

/**
 * WebDAV im Speicher: GET/PUT/DELETE/MKCOL/HEAD/PROPFIND (Depth 0, 1, infinity), 404 für
 * Fehlendes, 409 bei fehlendem Elternordner. Pfade sind die kodierten Request-Pfade, Ordner
 * enden auf `/`.
 *
 * Verlangt bewusst **keine** Auth: sonst zählte die 401-Challenge des Authenticators als eigener
 * Request in [requests].
 */
internal class FakeWebDav : Dispatcher() {
    private class Entry(val bytes: ByteArray, val etag: String, val modified: Long)

    private val files = mutableMapOf<String, Entry>()
    private val dirs = mutableSetOf("/")
    private val forced = mutableMapOf<String, Pair<Int, String>>()
    private var etagCounter = 0

    /** Jeder Request als `"METHOD /pfad"`, in Eingangsreihenfolge. */
    val requests: MutableList<String> = mutableListOf()

    @Synchronized fun writes(): List<String> = requests.filter { it.substringBefore(' ') in WRITE_METHODS }

    @Synchronized fun clearRequests() = requests.clear()

    @Synchronized fun text(path: String): String? = files[path]?.bytes?.decodeToString()

    @Synchronized fun exists(path: String): Boolean = path in files || dirKey(path) in dirs

    /** Legt eine Datei samt fehlender Elternordner an, ohne Request. */
    @Synchronized fun putFile(path: String, body: String) {
        var parent = parentOf(path)
        while (dirs.add(parent)) parent = parentOf(parent)
        files[path] = Entry(body.toByteArray(), nextEtag(), System.currentTimeMillis())
    }

    /** Erzwingt für [path] diese Antwort, egal welche Methode. */
    @Synchronized fun respond(path: String, code: Int, body: String = "") {
        forced[path] = code to body
    }

    @Synchronized override fun dispatch(request: RecordedRequest): MockResponse {
        val method = request.method.orEmpty()
        val path = request.requestUrl?.encodedPath ?: "/"
        requests += "$method $path"
        forced[path]?.let { (code, body) -> return MockResponse().setResponseCode(code).setBody(body) }
        return when (method) {
            "GET" -> files[path]?.let { status(200).setBody(Buffer().write(it.bytes)).setHeader("ETag", it.etag) }
                ?: status(404)
            "HEAD" -> status(if (exists(path)) 200 else 404)
            "PUT" -> put(path, request.body.readByteArray())
            "DELETE" -> delete(path)
            "MKCOL" -> mkcol(dirKey(path))
            "PROPFIND" -> propfind(path, request.getHeader("Depth") ?: "infinity")
            else -> status(405)
        }
    }

    private fun put(path: String, bytes: ByteArray): MockResponse {
        if (parentOf(path) !in dirs) return status(409)
        val isNew = path !in files
        val entry = Entry(bytes, nextEtag(), System.currentTimeMillis())
        files[path] = entry
        return status(if (isNew) 201 else 204).setHeader("ETag", entry.etag)
    }

    private fun delete(path: String): MockResponse {
        if (files.remove(path) != null) return status(204)
        val dir = dirKey(path)
        if (dir == "/" || dir !in dirs) return status(404)
        files.keys.removeAll { it.startsWith(dir) }
        dirs.removeAll { it.startsWith(dir) }
        return status(204)
    }

    private fun mkcol(dir: String): MockResponse = when {
        dir in dirs -> status(405)
        parentOf(dir) !in dirs -> status(409)
        else -> status(201).also { dirs += dir }
    }

    private fun propfind(path: String, depth: String): MockResponse {
        val entries = when {
            path in files -> listOf(path)
            dirKey(path) in dirs -> {
                val dir = dirKey(path)
                val below = (files.keys + dirs).filter { it != dir && it.startsWith(dir) }
                listOf(dir) + when (depth) {
                    "0" -> emptyList()
                    "1" -> below.filter { parentOf(it) == dir }
                    else -> below
                }.sorted()
            }
            else -> return status(404)
        }
        val xml = entries.joinToString("", PROPFIND_HEAD, PROPFIND_TAIL) { responseXml(it) }
        return status(207).setHeader("Content-Type", "application/xml; charset=utf-8").setBody(xml)
    }

    private fun responseXml(path: String): String {
        val file = files[path]
        val props = if (file == null) {
            "<d:resourcetype><d:collection/></d:resourcetype>"
        } else {
            "<d:resourcetype/><d:getcontentlength>${file.bytes.size}</d:getcontentlength>" +
                "<d:getetag>${file.etag}</d:getetag>" +
                "<d:getlastmodified>${httpDate.format(Date(file.modified))}</d:getlastmodified>"
        }
        return "<d:response><d:href>$path</d:href><d:propstat><d:prop>$props</d:prop>" +
            "<d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
    }

    private fun nextEtag() = "\"e${++etagCounter}\""

    private fun status(code: Int) = MockResponse().setResponseCode(code)

    private companion object {
        val WRITE_METHODS = setOf("PUT", "DELETE", "MKCOL", "MOVE", "COPY", "PROPPATCH")
        const val PROPFIND_HEAD = """<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:">"""
        const val PROPFIND_TAIL = "</d:multistatus>"
        val httpDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        fun dirKey(path: String) = if (path.endsWith("/")) path else "$path/"

        fun parentOf(path: String) = path.trimEnd('/').substringBeforeLast('/') + "/"
    }
}
