package dev.dettmer.simplenotes.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetGcTest {
    private val now = 1_000_000_000L
    private val old = now - AssetGc.GRACE_PERIOD_MS - 1
    private val recent = now - AssetGc.GRACE_PERIOD_MS + 1

    @Test fun `referenced assets are never collected regardless of age`() {
        val targets = AssetGc.computeTargets(
            referenced = setOf("a.webp"),
            localMtimes = mapOf("a.webp" to old),
            serverMtimes = mapOf("a.webp" to old),
            now = now,
            allowRemoteSweep = true
        )
        assertTrue(targets.localToDelete.isEmpty())
        assertTrue(targets.remoteToDelete.isEmpty())
    }

    @Test fun `unreferenced but recent assets survive the grace period`() {
        val targets = AssetGc.computeTargets(
            referenced = emptySet(),
            localMtimes = mapOf("orphan.webp" to recent),
            serverMtimes = mapOf("orphan.webp" to recent),
            now = now,
            allowRemoteSweep = true
        )
        assertTrue(targets.localToDelete.isEmpty())
        assertTrue(targets.remoteToDelete.isEmpty())
    }

    @Test fun `unreferenced and old assets are collected locally and remotely`() {
        val targets = AssetGc.computeTargets(
            referenced = emptySet(),
            localMtimes = mapOf("orphan.webp" to old),
            serverMtimes = mapOf("orphan.webp" to old),
            now = now,
            allowRemoteSweep = true
        )
        assertEquals(setOf("orphan.webp"), targets.localToDelete)
        assertEquals(setOf("orphan.webp"), targets.remoteToDelete)
    }

    @Test fun `remote sweep is skipped entirely when guard disallows it`() {
        val targets = AssetGc.computeTargets(
            referenced = emptySet(),
            localMtimes = mapOf("orphan.webp" to old),
            serverMtimes = mapOf("orphan.webp" to old),
            now = now,
            allowRemoteSweep = false
        )
        assertEquals(setOf("orphan.webp"), targets.localToDelete)
        assertTrue("remote sweep must be skipped when guard is false", targets.remoteToDelete.isEmpty())
    }

    @Test fun `unknown server mtime is never collected`() {
        val targets = AssetGc.computeTargets(
            referenced = emptySet(),
            localMtimes = emptyMap(),
            serverMtimes = mapOf("mystery.webp" to null),
            now = now,
            allowRemoteSweep = true
        )
        assertTrue("null server mtime means unknown age — must not delete", targets.remoteToDelete.isEmpty())
    }

    @Test fun `trash and archive references still protect their assets`() {
        // extractAllReferenced() already covers trash+archive (Modul 1) — this just verifies
        // AssetGc treats any name present in `referenced` as protected, no matter its origin.
        val targets = AssetGc.computeTargets(
            referenced = setOf("trashed-note-image.webp"),
            localMtimes = mapOf("trashed-note-image.webp" to old),
            serverMtimes = mapOf("trashed-note-image.webp" to old),
            now = now,
            allowRemoteSweep = true
        )
        assertTrue(targets.localToDelete.isEmpty())
        assertTrue(targets.remoteToDelete.isEmpty())
    }
}
