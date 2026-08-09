package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** True-black variant of dark mode for AMOLED screens (BACKLOG.md F19) — off by default, since it
 *  replaces Material's dark grey background/surface with pure black, a look not everyone wants.
 *  Same SharedPreferences-backed-StateFlow pattern as [PrefetchSettings]/[SuggestionSettings]. */
object DisplaySettings {
    private const val PREFS_NAME = "display_settings"
    private const val KEY_AMOLED = "amoled_enabled"

    private var prefs: SharedPreferences? = null

    private val _amoledEnabled = MutableStateFlow(false)
    val amoledEnabled: StateFlow<Boolean> = _amoledEnabled.asStateFlow()

    fun init(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        _amoledEnabled.value = sharedPrefs.getBoolean(KEY_AMOLED, false)
    }

    fun setAmoledEnabled(enabled: Boolean) {
        val p = prefs ?: return
        _amoledEnabled.value = enabled
        p.edit { putBoolean(KEY_AMOLED, enabled) }
    }
}
