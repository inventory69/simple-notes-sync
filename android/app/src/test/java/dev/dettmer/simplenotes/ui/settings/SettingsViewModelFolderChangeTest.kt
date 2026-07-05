package dev.dettmer.simplenotes.ui.settings

import android.app.Application
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.11.0 — Tests für den Ordnerwechsel-Bestätigungsdialog in [SettingsViewModel].
 *
 * `WebDavSyncService` und `NotesStorage` werden im ViewModel inline `new`-t (kein DI-Seam)
 * und nutzen intern echtes `Dispatchers.IO` — analog zu `syncNow()`/`testConnection()` bleibt
 * der Online-Switch-Pfad daher untested. Da echte Hintergrund-Threads nicht von
 * `TestCoroutineScheduler`/`advanceUntilIdle()` erfasst werden, wird auf einen Main-Dispatcher
 * mit Test-affinem, aber unconfined Verhalten gesetzt und asynchron gesetzter State per
 * kurzem, real wartendem Poll abgewartet (kein `Thread.sleep`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelFolderChangeTest {
    private lateinit var tmpDir: File
    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var app: Application
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tmpDir = Files.createTempDirectory("settings-vm-folder-test").toFile()
        fakePrefs = FakeSharedPreferences()
        // Baseline: bestehende Verbindung, sonst gated remoteTargetChangePending den
        // Ordnerwechsel nicht mehr (Erstsetup-Guard, siehe SettingsViewModel).
        fakePrefs.edit().putString(Constants.KEY_SERVER_URL, "https://example.com").apply()
        app = mockk(relaxed = true) {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns fakePrefs
            every { applicationContext } returns this
            every { getString(any()) } returns "msg"
            every { getString(any(), *anyVararg()) } returns "msg"
        }
        vm = SettingsViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tmpDir.deleteRecursively()
    }

    private fun seedNote(id: String, status: SyncStatus) = runBlocking {
        NotesStorage(app).saveNote(
            Note(id = id, title = "t-$id", content = "c", deviceId = "dev", syncStatus = status)
        )
    }

    /** Real (non-virtual) bounded wait for state set on a background thread (no DI seam to mock it away). */
    private fun awaitCondition(timeoutMs: Long = 2000, condition: () -> Boolean) = runBlocking {
        withTimeout(timeoutMs) {
            while (!condition()) delay(5)
        }
    }

    // ───── remoteTargetChangePending flow: false → true → false after revert ─────
    @Test
    fun `remoteTargetChangePending flips on change and back on revert`() {
        assertFalse(vm.remoteTargetChangePending.value)

        vm.updateSyncFolderName("archive")
        awaitCondition { vm.remoteTargetChangePending.value }
        assertTrue(vm.remoteTargetChangePending.value)

        vm.onFolderChangeCancelled()
        awaitCondition { !vm.remoteTargetChangePending.value }
        assertEquals(Constants.DEFAULT_SYNC_FOLDER_NAME, vm.syncFolderName.value)
    }

    // ───── requestRemoteChangeDecision(): unsyncedCount counts only PENDING + LOCAL_ONLY ─────
    @Test
    fun `requestRemoteChangeDecision counts only PENDING and LOCAL_ONLY notes`() {
        seedNote("synced", SyncStatus.SYNCED)
        seedNote("pending", SyncStatus.PENDING)
        seedNote("local-only", SyncStatus.LOCAL_ONLY)

        vm.updateSyncFolderName("archive")
        vm.requestRemoteChangeDecision()
        awaitCondition { vm.folderChangePrompt.value != null }

        val prompt = vm.folderChangePrompt.value
        assertEquals(2, prompt?.unsyncedCount)
        assertEquals(1, prompt?.localOnlyCount)
        assertEquals(Constants.DEFAULT_SYNC_FOLDER_NAME, prompt?.oldLabel)
        assertEquals("archive", prompt?.newLabel)
    }

    // ───── Cancel: syncFolderName reverted in state AND in prefs ─────
    @Test
    fun `onFolderChangeCancelled reverts state and prefs`() {
        val confirmed = vm.syncFolderName.value
        vm.updateSyncFolderName("archive")

        vm.onFolderChangeCancelled()

        assertEquals(confirmed, vm.syncFolderName.value)
        assertEquals(confirmed, fakePrefs.getString(Constants.KEY_SYNC_FOLDER_NAME, null))
        assertFalse(vm.remoteTargetChangePending.value)
    }

    // ───── Migrate: resetAllSyncStatusToPending() ran, confirmedSyncFolderName advanced ─────
    @Test
    fun `onFolderChangeConfirmedMigrate resets sync status and advances confirmed folder`() {
        seedNote("synced", SyncStatus.SYNCED)
        vm.updateSyncFolderName("archive")

        vm.onFolderChangeConfirmedMigrate()
        awaitCondition { !vm.remoteTargetChangePending.value }

        val reloaded = runBlocking { NotesStorage(app).loadAllNotes(forceReload = true) }
        assertTrue(reloaded.all { it.syncStatus == SyncStatus.PENDING })
    }

    // ───── Switch while offline: guard reverts without touching the network layer ─────
    @Test
    fun `onFolderChangeConfirmedSwitch reverts folder when offline`() {
        val confirmed = vm.syncFolderName.value
        vm.setOfflineMode(true)
        vm.updateSyncFolderName("archive")

        vm.onFolderChangeConfirmedSwitch()

        assertEquals(confirmed, vm.syncFolderName.value)
        assertEquals(confirmed, fakePrefs.getString(Constants.KEY_SYNC_FOLDER_NAME, null))
        assertFalse(vm.remoteTargetChangePending.value)
    }

    // ───── Completion event: Race-Fix (warm-plotting-thompson) — Navigation darf erst nach
    // diesem Signal erfolgen, auch im synchronen Offline-Zweig ─────
    @Test
    fun `onFolderChangeConfirmedSwitch emits completion event when offline`() = runBlocking {
        val confirmed = vm.syncFolderName.value
        vm.setOfflineMode(true)
        vm.updateSyncFolderName("archive")

        // Dispatchers.Main (UnconfinedTestDispatcher) statt runBlocking's Default-Dispatcher:
        // sonst startet der Collector erst beim nächsten Suspend-Point des Testkörpers, also
        // NACH dem synchronen tryEmit() unten — das Signal ginge am Subscriber vorbei.
        var completed = false
        val job = launch(Dispatchers.Main) { vm.folderChangeCompleted.collect { completed = true } }
        vm.onFolderChangeConfirmedSwitch()
        awaitCondition { completed }
        job.cancel()

        assertEquals(confirmed, vm.syncFolderName.value)
        assertFalse(vm.folderChangeInProgress.value)
    }

    // ───── Erstkonfiguration: ohne je bestätigte Verbindung kein Gate auf Ordnerwechsel ─────
    @Test
    fun `folder change does not gate during first-time setup (no confirmed server)`() {
        val freshPrefs = FakeSharedPreferences()
        val freshApp = mockk<Application>(relaxed = true) {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns freshPrefs
            every { applicationContext } returns this
            every { getString(any()) } returns "msg"
            every { getString(any(), *anyVararg()) } returns "msg"
        }
        val freshVm = SettingsViewModel(freshApp)
        freshVm.updateSyncFolderName("archive")
        assertFalse(freshVm.remoteTargetChangePending.value)
    }
}

/**
 * Minimal in-memory [SharedPreferences] fake — read-after-write semantics without Robolectric.
 * `SettingsViewModel` reads its own settings back from prefs at construction time
 * (theme, sync folder, offline mode, ...), which a plain relaxed mock cannot satisfy.
 */
private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map

    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        map[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }

        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }

        override fun putInt(key: String, value: Int) = apply { pending[key] = value }

        override fun putLong(key: String, value: Long) = apply { pending[key] = value }

        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

        override fun remove(key: String) = apply { removals += key }

        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) map.clear()
            removals.forEach { map.remove(it) }
            map.putAll(pending)
        }
    }
}
