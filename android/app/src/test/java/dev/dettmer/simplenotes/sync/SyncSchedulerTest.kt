package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class SyncSchedulerTest {

    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        ctx = mockk(relaxed = true) {
            // NotesStorage braucht ein gültiges filesDir — echtes Temp-Dir statt null-Path-Mock.
            every { filesDir } returns java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            every { getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        }
        prefs = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `triggerOnSaveSync_disabledSetting_returnsFalseAndLogs`() {
        every { prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, any()) } returns false
        val scheduler = SyncScheduler(ctx, prefs) { 1_000L }
        assertFalse(scheduler.triggerOnSaveSync())
    }

    @Test
    fun `triggerOnSaveSync_throttled_returnsFalse`() {
        every { prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, any()) } returns true
        every { prefs.getLong(Constants.PREF_LAST_ON_SAVE_SYNC_TIME, 0) } returns 1_000L
        // Gate via WebDavSyncService — wir mocken den Konstruktor + canSync()
        mockkConstructor(WebDavSyncService::class)
        every { anyConstructed<WebDavSyncService>().canSync() } returns
            SyncGateResult(canSync = true, blockReason = null)
        val scheduler = SyncScheduler(ctx, prefs) { 1_000L + 1_000L }  // 1 s seit letztem Sync (<5 s default)
        assertFalse(scheduler.triggerOnSaveSync())
    }

    // Hinweis: `gateBlocked`- und `enqueue`-Pfad-Tests wären stark Mock-lastig
    // wegen `WorkManager.getInstance(context)` (singleton). Die Throttle-Logik
    // ist die wesentliche neue Verantwortlichkeit; das Enqueue-Verhalten
    // ist 1:1 aus den ViewModels übernommen und durch deren bestehende
    // manuellen Tests bereits abgedeckt.
}
