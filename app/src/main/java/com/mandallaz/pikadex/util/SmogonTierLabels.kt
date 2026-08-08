package com.mandallaz.pikadex.util

/** Human-readable labels for Smogon's singles tier codes, since acronyms like "UU" or "PU" mean
 * nothing to someone unfamiliar with competitive Pokemon. Falls back to the raw code when a tier
 * isn't in this map (new/unusual tiers do show up in Showdown's data from time to time). */
object SmogonTierLabels {
    private val LABELS = mapOf(
        "AG" to "Anything Goes (AG)",
        "Uber" to "Uber",
        "OU" to "OverUsed (OU)",
        "UUBL" to "UU Borderline (UUBL)",
        "UU" to "UnderUsed (UU)",
        "RUBL" to "RU Borderline (RUBL)",
        "RU" to "RarelyUsed (RU)",
        "NUBL" to "NU Borderline (NUBL)",
        "NU" to "NeverUsed (NU)",
        "PUBL" to "PU Borderline (PUBL)",
        "PU" to "Practically Useless (PU)",
        "ZUBL" to "ZU Borderline (ZUBL)",
        "ZU" to "ZeroUsed (ZU)",
        "NFE" to "Not Fully Evolved (NFE)",
        "LC" to "Little Cup (LC)",
        "CAP" to "CAP",
        "CAP NFE" to "CAP – Not Fully Evolved",
        "CAP LC" to "CAP – Little Cup"
    )

    /** Roughly strongest/most-used to least, for a sensible default ordering in pickers. */
    private val ORDER = listOf(
        "AG", "Uber", "OU", "UUBL", "UU", "RUBL", "RU", "NUBL", "NU",
        "PUBL", "PU", "ZUBL", "ZU", "NFE", "LC", "CAP", "CAP NFE", "CAP LC"
    )

    fun labelFor(tierCode: String): String = LABELS[tierCode] ?: tierCode

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
