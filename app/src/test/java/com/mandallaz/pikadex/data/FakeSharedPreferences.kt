package com.mandallaz.pikadex.data

import android.content.SharedPreferences

/**
 * Minimal in-memory [SharedPreferences], for unit-testing SharedPreferences-backed singletons
 * (e.g. [SuggestionSettings]) without a real Context/Robolectric — same "swap the real dependency
 * for a plain-JVM fake via reflection" approach [com.mandallaz.pikadex.data.JsonDiskCacheTest]
 * already uses for its own File-backed cache. Only the handful of members these settings objects
 * actually call are implemented for real; everything else is unused by any current caller.
 */
class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String> ?: defValues)
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?) = apply { key?.let { pending[it] = value } }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { key?.let { pending[it] = values } }
        override fun putInt(key: String?, value: Int) = apply { key?.let { pending[it] = value } }
        override fun putLong(key: String?, value: Long) = apply { key?.let { pending[it] = value } }
        override fun putFloat(key: String?, value: Float) = apply { key?.let { pending[it] = value } }
        override fun putBoolean(key: String?, value: Boolean) = apply { key?.let { pending[it] = value } }
        override fun remove(key: String?) = apply { key?.let { toRemove.add(it) } }
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) values.clear()
            toRemove.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}
