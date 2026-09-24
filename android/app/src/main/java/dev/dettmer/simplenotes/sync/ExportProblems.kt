package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.utils.Constants

/**
 * 🆕 v2.19.0: Offene Export-Probleme, egal ob Worker oder UI gesynct hat.
 * Einzige Quelle für Badge, Legenden-Karte und Hintergrund-Benachrichtigung.
 *
 * Ist-Zustand, kein Ereignis: Eine MD-ID bleibt stehen, bis ihre Kopie wirklich auf dem Server liegt.
 *
 * @param markdownFailedIds Notizen, deren MD-Kopie fehlt oder veraltet ist.
 * @param markdownImportFailed Auto-Import-Schritt abgebrochen — eigener Text, kein „nicht exportiert".
 * @param markdownReason bereits übersetzte Meldung aus [SyncExceptionMapper].
 */
data class ExportProblems(
    val markdownFailedIds: Set<String> = emptySet(),
    val markdownImportFailed: Boolean = false,
    val assetsFailed: Int = 0,
    val markdownReason: String? = null,
    val assetsReason: String? = null
) {
    /** Für Badge und Zähler: jede fehlende Kopie plus der abgebrochene Import. */
    val markdownFailedCount: Int get() = markdownFailedIds.size + if (markdownImportFailed) 1 else 0

    val isEmpty: Boolean get() = markdownFailedCount + assetsFailed == 0

    companion object {
        fun load(prefs: SharedPreferences): ExportProblems? = ExportProblems(
            // Kopie: das Set aus getStringSet darf nicht verändert werden.
            markdownFailedIds = prefs.getStringSet(Constants.KEY_EXPORT_MD_FAILED_IDS, null)?.toSet().orEmpty(),
            markdownImportFailed = prefs.getBoolean(Constants.KEY_EXPORT_MD_IMPORT_FAILED, false),
            assetsFailed = prefs.getInt(Constants.KEY_EXPORT_ASSETS_FAILED, 0),
            markdownReason = prefs.getString(Constants.KEY_EXPORT_MD_REASON, null),
            assetsReason = prefs.getString(Constants.KEY_EXPORT_ASSETS_REASON, null)
        ).takeUnless { it.isEmpty }

        fun save(prefs: SharedPreferences, p: ExportProblems) = prefs.edit {
            if (p.markdownFailedIds.isEmpty()) {
                remove(Constants.KEY_EXPORT_MD_FAILED_IDS)
            } else {
                putStringSet(Constants.KEY_EXPORT_MD_FAILED_IDS, p.markdownFailedIds)
            }
            if (p.markdownImportFailed) {
                putBoolean(Constants.KEY_EXPORT_MD_IMPORT_FAILED, true)
            } else {
                remove(Constants.KEY_EXPORT_MD_IMPORT_FAILED)
            }
            if (p.assetsFailed > 0) {
                putInt(Constants.KEY_EXPORT_ASSETS_FAILED, p.assetsFailed)
            } else {
                remove(Constants.KEY_EXPORT_ASSETS_FAILED)
            }
            putOrRemove(Constants.KEY_EXPORT_MD_REASON, p.markdownReason.takeIf { p.markdownFailedCount > 0 })
            putOrRemove(Constants.KEY_EXPORT_ASSETS_REASON, p.assetsReason.takeIf { p.assetsFailed > 0 })
        }

        /** MD-Export abgeschaltet: Was nicht mehr exportiert wird, kann auch nicht fehlen. */
        fun clearMarkdown(prefs: SharedPreferences) = prefs.edit {
            remove(Constants.KEY_EXPORT_MD_FAILED_IDS)
            remove(Constants.KEY_EXPORT_MD_IMPORT_FAILED)
            remove(Constants.KEY_EXPORT_MD_REASON)
        }

        private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) =
            if (value != null) putString(key, value) else remove(key)
    }
}
