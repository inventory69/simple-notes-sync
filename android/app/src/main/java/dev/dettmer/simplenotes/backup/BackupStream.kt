package dev.dettmer.simplenotes.backup

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Backup-JSON als Stream schreiben und lesen.
 *
 * Seit den Bildanhängen trägt ein Backup alle Bilder als Base64. Komplett im Speicher gebaut
 * (`gson.toJson` → String → ByteArray) brauchte das ein Vielfaches der Bildgröße und endete ab
 * einigen Dutzend Bildern mit `OutOfMemoryError`. Hier liegt immer nur ein Bild im Speicher.
 * Das Format bleibt dasselbe JSON wie bisher, ältere App-Versionen lesen es weiter.
 */
internal object BackupStream {
    private const val KEY_ASSETS = "assets"

    /**
     * Schreibt [head] (ohne dessen `assets`) und danach [assets] als `assets`-Array. Die Sequenz
     * wird erst beim Schreiben durchlaufen, jedes Bild also erst dann gelesen. Schließt [out].
     */
    fun write(out: OutputStream, gson: Gson, head: BackupData, assets: Sequence<BackupAsset>) {
        JsonWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { writer ->
            writer.setIndent("  ")
            writer.beginObject()
            for ((key, value) in gson.toJsonTree(head.copy(assets = null)).asJsonObject.entrySet()) {
                writer.name(key)
                gson.toJson(value, writer)
            }
            writer.name(KEY_ASSETS).beginArray()
            assets.forEach { gson.toJson(it, BackupAsset::class.java, writer) }
            writer.endArray()
            writer.endObject()
        }
    }

    /**
     * Liest ein Backup in einem Durchlauf. Jedes Bild geht einzeln an [onAsset] (bei `null`
     * übersprungen), der Rest wird zu [BackupData] mit `assets = null`. Alte Backups haben
     * `assets` vor `notes` (Gson schreibt die Felder alphabetisch), neue am Ende.
     */
    suspend fun read(input: InputStream, gson: Gson, onAsset: (suspend (BackupAsset) -> Unit)?): BackupData {
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        val head = JsonObject()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (key != KEY_ASSETS || reader.peek() != JsonToken.BEGIN_ARRAY) {
                head.add(key, JsonParser.parseReader(reader))
                continue
            }
            reader.beginArray()
            while (reader.hasNext()) {
                if (onAsset == null) {
                    reader.skipValue()
                    continue
                }
                onAsset(gson.fromJson(reader, BackupAsset::class.java))
            }
            reader.endArray()
        }
        reader.endObject()
        return gson.fromJson(head, BackupData::class.java)
    }
}
