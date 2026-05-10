package dev.dettmer.simplenotes.noteimport.keep.zip

/**
 * v2.5.0 — In-memory-Repräsentation eines ZIP-Entries (siehe Commit #6).
 * Wird hier vorgezogen, damit der Parser-Vertrag (Commit #5) typisiert ist.
 */
data class KeepZipEntry(
    val name: String,
    val bytes: ByteArray,
    val sizeBytes: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeepZipEntry) return false
        return name == other.name && sizeBytes == other.sizeBytes && bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + sizeBytes.hashCode()
        return result
    }
}
