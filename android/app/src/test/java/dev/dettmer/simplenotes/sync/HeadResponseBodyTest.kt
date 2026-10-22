package dev.dettmer.simplenotes.sync

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression v2.20.1: Manche Server hängen an die 401 auf einen HEAD eine Fehlerseite an, obwohl
 * HTTP das verbietet. OkHttp erwartet bei HEAD keinen Body und legt die Verbindung in den Pool, der
 * nächste Request darauf liest die Fehlerseite als Statuszeile (`ProtocolException`, kein Retry).
 * Seit dem geteilten Pool traf das jeden Sync nach dem Gate-HEAD.
 */
class HeadResponseBodyTest {
    private val h = SyncHarness()

    @After fun tearDown() {
        ConnectionManager.closeIdleConnections()
        h.close()
    }

    /** Ohne `Authorization` → 401 samt HTML-Body, auch bei HEAD. Sonst [FakeWebDav]. */
    private fun useBodyOnHeadServer() {
        h.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Authorization") == null) {
                    MockResponse().setResponseCode(401)
                        .setHeader("WWW-Authenticate", "Basic realm=\"dav\"")
                        .setBody("<html><body><h1>401 Unauthorized</h1></body></html>")
                } else {
                    h.dav.dispatch(request)
                }
        }
    }

    @Test fun `request after unauthenticated gate HEAD succeeds`() = runTest {
        ConnectionManager.closeIdleConnections()
        useBodyOnHeadServer()
        val service = h.service(UnconfinedTestDispatcher(testScheduler))

        assertTrue(service.isServerReachable())
        // isServerLocked() ist fail-closed: Ein Fehler auf der Verbindung liefert true
        assertFalse(service.isServerLocked())
    }

    @Test fun `testConnection survives 401 with body on HEAD`() = runTest {
        ConnectionManager.closeIdleConnections()
        useBodyOnHeadServer()
        val service = h.service(UnconfinedTestDispatcher(testScheduler))

        val result = service.testConnection()

        assertTrue(result.errorMessage, result.isSuccess)
    }
}
