package dev.dettmer.simplenotes.utils

import android.content.SharedPreferences

/**
 * Minimal in-memory [SharedPreferences] fake — read-after-write semantics without Robolectric.
 * `SettingsViewModel` reads its own settings back from prefs at construction time
 * (theme, sync folder, offline mode, ...), which a plain relaxed mock cannot satisfy.
 */
internal class FakeSharedPreferences : SharedPreferences {
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
