package dev.dettmer.simplenotes.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Persistent queue for server deletions that couldn't be executed
 * because the server was unreachable at the time of deletion.
 *
 * Stored as a JSON array in the app's files directory.
 * Processed during the next successful sync (Step 4.5 in syncNotes()).
 */
class PendingServerDeletions(context: Context) {

    private val file = File(context.filesDir, FILENAME)
    private val mutex = Mutex()
    private val gson = Gson()

    suspend fun add(noteIds: List<String>) = mutex.withLock {
        val current = loadInternal()
        val updated = (current + noteIds).distinct()
        saveInternal(updated)
        Logger.d(TAG, "📋 Pending server deletions: +${noteIds.size} → ${updated.size} total")
    }

    suspend fun getAll(): List<String> = mutex.withLock {
        loadInternal()
    }

    suspend fun remove(noteIds: List<String>) = mutex.withLock {
        val current = loadInternal()
        val updated = current - noteIds.toSet()
        saveInternal(updated)
        Logger.d(TAG, "📋 Pending server deletions: -${noteIds.size} → ${updated.size} remaining")
    }

    suspend fun isEmpty(): Boolean = mutex.withLock {
        loadInternal().isEmpty()
    }

    private fun loadInternal(): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText()
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load pending deletions: ${e.message}")
            emptyList()
        }
    }

    private fun saveInternal(noteIds: List<String>) {
        try {
            file.writeText(gson.toJson(noteIds))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save pending deletions: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PendingServerDeletions"
        const val FILENAME = "pending_server_deletions.json"
    }
}
