package com.mandallaz.pikadex.util

import androidx.annotation.StringRes
import com.mandallaz.pikadex.R

/** Human-readable labels for Smogon's singles tier codes, since acronyms like "UU" or "PU" mean
 * nothing to someone unfamiliar with competitive Pokemon. Falls back to the raw code when a tier
 * isn't in this map (new/unusual tiers do show up in Showdown's data from time to time).
 *
 * issue #71 (B21) — labels used to be hardcoded English String literals baked directly into this
 * plain-Kotlin object, bypassing stringResource() like every other user-facing string in the app.
 * Now a @StringRes id, resolved at render time by a `localizedTierLabel` Composable extension
 * defined in `ui/components/OptionsDialog.kt` — kept out of this file, same reasoning as
 * [MoveCategory]'s `localizedLabel()` extension in `PokedexDetailScreen.kt`: this plain-Kotlin
 * object shouldn't gain a Compose dependency just for label lookup. */
object SmogonTierLabels {
    /** Internal, not private: [SmogonTierLabelsTest] checks the tierCode -> resource-id mapping
     *  directly, since a JVM unit test can't resolve an actual string resource. */
    internal val LABELS: Map<String, Int> = mapOf(
        "AG" to R.string.smogon_tier_label_ag,
        "Uber" to R.string.smogon_tier_label_uber,
        "OU" to R.string.smogon_tier_label_ou,
        "UUBL" to R.string.smogon_tier_label_uubl,
        "UU" to R.string.smogon_tier_label_uu,
        "RUBL" to R.string.smogon_tier_label_rubl,
        "RU" to R.string.smogon_tier_label_ru,
        "NUBL" to R.string.smogon_tier_label_nubl,
        "NU" to R.string.smogon_tier_label_nu,
        "PUBL" to R.string.smogon_tier_label_publ,
        "PU" to R.string.smogon_tier_label_pu,
        "ZUBL" to R.string.smogon_tier_label_zubl,
        "ZU" to R.string.smogon_tier_label_zu,
        "NFE" to R.string.smogon_tier_label_nfe,
        "LC" to R.string.smogon_tier_label_lc,
        "CAP" to R.string.smogon_tier_label_cap,
        "CAP NFE" to R.string.smogon_tier_label_cap_nfe,
        "CAP LC" to R.string.smogon_tier_label_cap_lc
    )

    /** Roughly strongest/most-used to least, for a sensible default ordering in pickers. */
    private val ORDER = listOf(
        "AG", "Uber", "OU", "UUBL", "UU", "RUBL", "RU", "NUBL", "NU",
        "PUBL", "PU", "ZUBL", "ZU", "NFE", "LC", "CAP", "CAP NFE", "CAP LC"
    )

    /** The label's string resource, or null for a tier not in [LABELS] — the caller (see
     *  [localizedTierLabel]) falls back to the raw code in that case, same as the old plain-String
     *  behaviour. */
    @StringRes
    fun labelResFor(tierCode: String): Int? = LABELS[tierCode]

    fun sortedTiers(tierCodes: Collection<String>): List<String> =
        tierCodes.sortedBy { code -> ORDER.indexOf(code).let { if (it >= 0) it else Int.MAX_VALUE } }

    /** True if [tier] is usable at a format capped at [ceiling] — i.e. [tier] is [ceiling] itself
     *  or anything weaker, the same "this tier or below" rule Smogon's own tier list follows (a UU
     *  format allows UU, RU, NU... but not OU/Uber). An unrecognized [tier] (not in [ORDER]) is
     *  treated as weaker than every known tier, so an unfamiliar/new tier code isn't excluded by a
     *  ceiling filter just because it's unrecognized. An unrecognized [ceiling] allows everything,
     *  since there's nothing to compare against. */
    fun isAtOrBelowCeiling(tier: String, ceiling: String): Boolean {
        val ceilingIndex = ORDER.indexOf(ceiling)
        if (ceilingIndex < 0) return true
        val tierIndex = ORDER.indexOf(tier).let { if (it >= 0) it else Int.MAX_VALUE }
        return tierIndex >= ceilingIndex
    }
}
