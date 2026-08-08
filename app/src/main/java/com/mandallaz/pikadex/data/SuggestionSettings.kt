package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The competitive tier ceiling applied to team-builder Suggestions (BACKLOG.md F17) — e.g.
 *  picking "UU" keeps UU and everything below it (RU, NU, PU...) but drops Uber/OU suggestions,
 *  the same "usable at this tier or below" rule Smogon's own tier list follows. Null means no
 *  limit, the default so existing behavior is unchanged until the user opts in. Same
 *  SharedPreferences-backed-StateFlow pattern as [PrefetchSettings]/[FavoritesRepository]/
 *  [TeamRepository]. */
object SuggestionSettings {
    private const val PREFS_NAME = "suggestion_settings"
    private const val KEY_MAX_TIER = "max_tier"

    private lateinit var prefs: SharedPreferences

    private val _maxTier = MutableStateFlow<String?>(null)
    val maxTier: StateFlow<String?> = _maxTier.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _maxTier.value = prefs.getString(KEY_MAX_TIER, null)
    }

    fun setMaxTier(tier: String?) {
        _maxTier.value = tier
        prefs.edit { if (tier == null) remove(KEY_MAX_TIER) else putString(KEY_MAX_TIER, tier) }
    }
}
