package com.mandallaz.pikadex.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The competitive tier ceiling applied to team-builder Suggestions (issue #11) — e.g.
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

    private val store = PrefsStore<String?>(DEFAULT_MAX_TIER)
    val maxTier: StateFlow<String?> = store.flow.asStateFlow()

    fun init(context: Context) {
        store.init(
            context = context,
            name = PREFS_NAME,
            key = KEY_MAX_TIER,
            default = DEFAULT_MAX_TIER,
            encode = { key, value -> putString(key, value ?: NO_LIMIT_SENTINEL) },
            decode = { key, default -> resolveMaxTier(getString(key, default)) }
        )
    }

    fun setMaxTier(tier: String?) {
        store.set(tier)
    }

    /** Translates a raw stored value (already defaulted to [DEFAULT_MAX_TIER] by [init]'s own
     *  `getString` call when the key was never written) into what [maxTier] should actually be —
     *  [NO_LIMIT_SENTINEL] means the user explicitly chose "No limit", not "unconfigured". Internal,
     *  not private, so it's unit-testable directly without a real Context/SharedPreferences. */
    internal fun resolveMaxTier(stored: String?): String? = stored?.takeIf { it != NO_LIMIT_SENTINEL }
}
