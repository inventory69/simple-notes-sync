package dev.dettmer.simplenotes.sync

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: Leerlauf-Verbindungen aus Gate-Check und Session-Client müssen für
 * [ConnectionManager.closeIdleConnections] erreichbar sein. Vorher hatte jeder Client einen
 * eigenen Pool, die Verbindung blieb liegen und weckte das Gerät über die Server-Keepalives.
 */
class SharedConnectionPoolTest {
    private val h = SyncHarness()

    @After fun tearDown() {
        ConnectionManager.closeIdleConnections()
        h.close()
    }

    @Test fun `gate HEAD stays out of the pool, closeIdleConnections empties it`() = runTest {
        ConnectionManager.closeIdleConnections()
        val service = h.service(UnconfinedTestDispatcher(testScheduler))

        assertTrue(service.isServerReachable())
        service.isServerLocked()

        // Der HEAD schließt seine Verbindung (v2.20.2), der GET öffnet eine neue und lässt sie im
        // geteilten Pool liegen
        assertEquals(0, h.server.takeRequest().sequenceNumber)
        assertEquals(0, h.server.takeRequest().sequenceNumber)
        assertEquals(1, ConnectionManager.sharedPool.idleConnectionCount())

        ConnectionManager.closeIdleConnections()

        assertEquals(0, ConnectionManager.sharedPool.connectionCount())
    }
}
