package com.mandallaz.pikadex.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** True-black variant of dark mode for AMOLED screens (issue #4) — off by default, since it
 *  replaces Material's dark grey background/surface with pure black, a look not everyone wants.
 *  Same SharedPreferences-backed-StateFlow pattern as [PrefetchSettings]/[SuggestionSettings]. */
object DisplaySettings {
    private const val PREFS_NAME = "display_settings"
    private const val KEY_AMOLED = "amoled_enabled"

    private val store = PrefsStore(false)
    val amoledEnabled: StateFlow<Boolean> = store.flow.asStateFlow()

    fun init(context: Context) {
        store.init(context, PREFS_NAME, KEY_AMOLED, false)
    }

    fun setAmoledEnabled(enabled: Boolean) {
        store.set(enabled)
    }
}
