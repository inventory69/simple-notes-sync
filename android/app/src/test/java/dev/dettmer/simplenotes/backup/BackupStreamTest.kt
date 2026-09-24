package dev.dettmer.simplenotes.backup

import com.google.gson.GsonBuilder
import dev.dettmer.simplenotes.models.Note
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStreamTest {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val backup = BackupData(
        backupVersion = 1,
        createdAt = 1_700_000_000_000L,
        notesCount = 1,
        appVersion = "2.19.0",
        notes = listOf(Note(id = "n1", title = "Bilder", content = "![](.assets/a.jpg)", deviceId = "dev")),
        appSettings = AppSettings(serverUrl = "https://example.org", syncFolder = "notes"),
        localOnlyFolders = listOf("Privat")
    )
    private val assets = listOf(BackupAsset("a.jpg", "aGVsbG8="), BackupAsset("b.png", "d29ybGQ="))

    private fun written(): ByteArray =
        ByteArrayOutputStream().also { BackupStream.write(it, gson, backup, assets.asSequence()) }.toByteArray()

    private suspend fun read(json: ByteArray, collect: Boolean = true): Pair<BackupData, List<BackupAsset>> {
        val received = mutableListOf<BackupAsset>()
        val data = BackupStream.read(json.inputStream(), gson, if (collect) { a -> received += a } else null)
        return data to received
    }

    @Test
    fun `streamed backup reads back with the old whole-file parser`() {
        val old = gson.fromJson(String(written()), BackupData::class.java)

        assertEquals(backup.copy(assets = assets), old)
    }

    @Test
    fun `old pretty-printed backup streams head and every asset`() = runTest {
        val oldFile = gson.toJson(backup.copy(assets = assets)).toByteArray()

        val (head, received) = read(oldFile)

        assertEquals(backup, head)
        assertEquals(assets, received)
    }

    @Test
    fun `assets are skipped without a consumer`() = runTest {
        val (head, received) = read(written(), collect = false)

        assertEquals(backup, head)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `asset with a path in its name is skipped`() = runTest {
        val evil = BackupAsset("../../shared_prefs/x.xml", "aGVsbG8=")
        val file = gson.toJson(backup.copy(assets = listOf(evil) + assets)).toByteArray()

        val (_, received) = read(file)

        assertEquals(assets, received)
    }

    @Test
    fun `release backups up to v2_18 keep their image names`() = runTest {
        // Ausschnitt aus einem echten v2.18.1-Release-Backup: R8 hatte BackupAsset.name in "a" umbenannt.
        val release = """{"app_version":"2.18.1","assets":[{"a":"9eda08509df34f14.webp","data_base64":"aGVsbG8="}],
            |"backup_version":1,"created_at":1,"notes":[],"notes_count":0}""".trimMargin().toByteArray()

        val (_, received) = read(release)

        assertEquals(listOf(BackupAsset("9eda08509df34f14.webp", "aGVsbG8=")), received)
    }
}
