package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.utils.ActivityLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncWorkerTriggerTest {
    @Test
    fun `triggerFromTags picks the sync tag, ignoring the WorkManager-injected class name`() {
        // getTags() ist ein ungeordnetes Set und enthält den FQCN des Workers selbst —
        // Regressionstest für die tagOrUnknown()-Falle (firstOrNull() auf diesem Set).
        val tags = setOf("dev.dettmer.simplenotes.sync.SyncWorker", "notes_sync", "wifi-connect")
        assertEquals(ActivityLog.Trigger.WIFI_CONNECT, SyncWorker.triggerFromTags(tags))
    }

    @Test
    fun `triggerFromTags returns null for unrecognized tags`() {
        assertNull(SyncWorker.triggerFromTags(setOf("notes_sync")))
    }
}
