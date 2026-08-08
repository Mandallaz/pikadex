package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto

/** One dex entry considered for [rankSuggestions] — just enough to score it (own typing, for STAB
 *  offense/defense) without a per-candidate movepool fetch. */
data class SuggestionCandidate(
    val name: String,
    val types: List<String>,
    val statTotal: Int
)

data class TeamSuggestion(
    val name: String,
    val statTotal: Int,
    val types: List<String>,
    /** Same-species forms (mega/gmax/regional/...) whose typing differs from [types] and would
     *  NOT keep the resist-a-weakness/hit-a-gap property this suggestion was made for — e.g. Mega
     *  Charizard X (Fire/Dragon) losing what base Charizard (Fire/Flying) qualified on. A
     *  suggestion is always scored against exactly one typing (the base species' — alt forms are
     *  excluded from [rankSuggestions]' candidate pool entirely), so a form that changes the
     *  typing can silently invalidate the reason the species was suggested. Populated by
     *  [findConflictingForms], not [rankSuggestions] itself — see that function's doc. */
    val conflictingForms: List<String> = emptyList()
)

/** True if [types] resists at least one of [sharedWeaknesses] (<1.0) *and* hits at least one of
 *  [coverageGaps] (>1.0) with its own typing (STAB only) — the "both required" rule shared by
 *  [rankSuggestions] and [findConflictingForms]. */
private fun qualifies(
    types: List<String>,
    sharedWeaknesses: List<String>,
    coverageGaps: List<String>,
    typeDetailsByType: Map<String, TypeDetailDto>
): Boolean {
    val details = types.mapNotNull { typeDetailsByType[it] }
    val defensive = computeDefensiveMultipliers(details)
    if (sharedWeaknesses.none { (defensive[it] ?: 1.0) < 1.0 }) return false
    val offensiveByType = details.associate { it.name to computeOffensiveMultipliers(it) }
    val bestOffense = bestOffensiveMultipliers(types, offensiveByType)
    return coverageGaps.any { (bestOffense[it] ?: 0.0) > 1.0 }
}

/**
 * Candidates that would help a team stuck with [sharedWeaknesses] and [coverageGaps] at once — a
 * candidate qualifies only if it resists (<1.0) at least one shared weakness *and* hits (>1.0) at
 * least one coverage gap with its own typing, both required. Sorted by [SuggestionCandidate.statTotal]
 * ascending (the user's explicit choice, not score-weighted) and capped at [limit].
 *
 * Offense is STAB only (a candidate's own types, not its movepool) — see BACKLOG.md F11.
 * [typeDetailsByType] must have an entry for every type referenced by [sharedWeaknesses],
 * [coverageGaps], or a candidate's [SuggestionCandidate.types]; a missing entry just drops that
 * type's contribution rather than failing the whole candidate.
 */
fun rankSuggestions(
    sharedWeaknesses: List<String>,
    coverageGaps: List<String>,
    candidates: List<SuggestionCandidate>,
    typeDetailsByType: Map<String, TypeDetailDto>,
    excludeNames: Set<String>,
    limit: Int = 10
): List<TeamSuggestion> {
    if (sharedWeaknesses.isEmpty() || coverageGaps.isEmpty()) return emptyList()
    return candidates.asSequence()
        .filter { it.name !in excludeNames }
        .filter { qualifies(it.types, sharedWeaknesses, coverageGaps, typeDetailsByType) }
        .map { TeamSuggestion(it.name, it.statTotal, it.types) }
        .sortedBy { it.statTotal }
        .take(limit)
        .toList()
}

/**
 * Same-species forms of [baseName] (names prefixed `"$baseName-"`, e.g. `"charizard-mega-x"`)
 * whose typing in [typesByName] differs from [scoredTypes] and does *not* itself
 * [qualifies] against [sharedWeaknesses]/[coverageGaps] — the ones worth warning the user away
 * from. A form sharing [scoredTypes] (most cosmetic/gmax forms) is silently skipped: there's
 * nothing to disambiguate when the typing didn't actually change. Sorted for a stable UI order.
 */
fun findConflictingForms(
    baseName: String,
    scoredTypes: List<String>,
    sharedWeaknesses: List<String>,
    coverageGaps: List<String>,
    typesByName: Map<String, List<String>>,
    typeDetailsByType: Map<String, TypeDetailDto>
): List<String> = typesByName
    .filterKeys { it != baseName && it.startsWith("$baseName-") }
    .filterValues { it != scoredTypes }
    .filterValues { !qualifies(it, sharedWeaknesses, coverageGaps, typeDetailsByType) }
    .keys
    .sorted()
