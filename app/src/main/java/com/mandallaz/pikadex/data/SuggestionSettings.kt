package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The competitive tier ceiling applied to team-builder Suggestions (BACKLOG.md F17) — e.g.
 *  picking "UU" keeps UU and everything below it (RU, NU, PU...) but drops Uber/OU suggestions,
 *  the same "usable at this tier or below" rule Smogon's own tier list follows. Defaults to "OU"
 *  (drops only Uber/AG) rather than no limit — OU is the tier the overwhelming majority of
 *  competitive play happens at, so it's a more useful out-of-the-box Suggestions list than
 *  including Ubers by default. Same SharedPreferences-backed-StateFlow pattern as
 *  [PrefetchSettings]/[FavoritesRepository]/[TeamRepository]. */
object SuggestionSettings {
    private const val PREFS_NAME = "suggestion_settings"
    private const val KEY_MAX_TIER = "max_tier"
    private const val DEFAULT_MAX_TIER = "OU"

    /** Persisted in place of null when the user explicitly picks "No limit" — SharedPreferences has
     *  no way to store a real null distinct from "key absent", and "key absent" already means
     *  [DEFAULT_MAX_TIER] (never configured). No real Smogon tier code is ever empty, so this can't
     *  collide with an actual selection. */
    private const val NO_LIMIT_SENTINEL = ""

    private var prefs: SharedPreferences? = null

    private val _maxTier = MutableStateFlow<String?>(DEFAULT_MAX_TIER)
    val maxTier: StateFlow<String?> = _maxTier.asStateFlow()

    fun init(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        _maxTier.value = resolveMaxTier(sharedPrefs.getString(KEY_MAX_TIER, DEFAULT_MAX_TIER))
    }

    fun setMaxTier(tier: String?) {
        val p = prefs ?: return
        _maxTier.value = tier
        p.edit { putString(KEY_MAX_TIER, tier ?: NO_LIMIT_SENTINEL) }
    }

    /** Translates a raw stored value (already defaulted to [DEFAULT_MAX_TIER] by [init]'s own
     *  `getString` call when the key was never written) into what [maxTier] should actually be —
     *  [NO_LIMIT_SENTINEL] means the user explicitly chose "No limit", not "unconfigured". Internal,
     *  not private, so it's unit-testable directly without a real Context/SharedPreferences. */
    internal fun resolveMaxTier(stored: String?): String? = stored?.takeIf { it != NO_LIMIT_SENTINEL }
}
