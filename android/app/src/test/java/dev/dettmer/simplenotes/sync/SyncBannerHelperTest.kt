package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.res.Resources
import dev.dettmer.simplenotes.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🆕 v2.16.0: Das Abschluss-Banner muss einen Konflikt melden.
 *
 * Vorher kannte [buildSyncResultBanner] nur übertragene und gelöschte Notizen. Ein Sync, der
 * gerade eine Notiz als CONFLICT markiert hatte, lieferte deshalb `null` — die UI machte daraus
 * „Nichts zu synchronisieren", also die Gegenaussage zu dem, was passiert war.
 */
class SyncBannerHelperTest {
    private val resources = mockk<Resources>()
    private val context = mockk<Context> {
        every { resources } returns this@SyncBannerHelperTest.resources
    }

    init {
        every {
            resources.getQuantityString(R.plurals.sync_notes_synced_count, any(), any())
        } answers { "${thirdArg<Array<Any>>()[0]} synced" }
        every { context.getString(R.string.sync_markdown_import_failed) } returns "import failed"
        every { context.getString(R.string.sync_export_resolved) } returns "files transferred"
        every {
            resources.getQuantityString(R.plurals.sync_conflict_count, any(), any())
        } answers { "${thirdArg<Array<Any>>()[0]} conflicts" }
        every {
            resources.getQuantityString(R.plurals.sync_markdown_failed_count, any(), any())
        } answers { "${thirdArg<Array<Any>>()[0]} md failed" }
        every {
            resources.getQuantityString(R.plurals.sync_assets_failed_count, any(), any())
        } answers { "${thirdArg<Array<Any>>()[0]} images failed" }
    }

    @Test fun `a conflict is reported even when nothing was transferred`() {
        val banner = buildSyncResultBanner(context, SyncResult(isSuccess = true, conflictCount = 1))

        assertEquals("1 conflicts", banner)
    }

    @Test fun `the conflict comes first so it survives truncation`() {
        val result = SyncResult(isSuccess = true, syncedCount = 3, conflictCount = 2)

        assertEquals("2 conflicts · 3 synced", buildSyncResultBanner(context, result))
    }

    @Test fun `a retry that only transferred missing files says so`() {
        val result = SyncResult(isSuccess = true, exportProblemsResolved = true)

        assertEquals("files transferred", buildSyncResultBanner(context, result))
    }

    @Test fun `a quiet sync still reports nothing`() {
        assertNull(buildSyncResultBanner(context, SyncResult(isSuccess = true)))
    }

    @Test fun `export problems are reported after the conflict`() {
        val result = SyncResult(isSuccess = true, conflictCount = 1, markdownFailedCount = 2, assetFailedCount = 1)

        assertEquals("1 conflicts · 2 md failed · 1 images failed", buildSyncResultBanner(context, result))
    }

    @Test fun `a failed markdown import gets its own part, not a file count`() {
        val result = SyncResult(isSuccess = true, markdownImportFailed = true)

        assertEquals("import failed", buildSyncResultBanner(context, result))
    }
}
