package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto

/** One dex entry considered for [rankSuggestions] — just enough to score it (own typing, for STAB
 *  offense/defense) without a per-candidate movepool fetch. */
data class SuggestionCandidate(
    val name: String,
    val types: List<String>,
    val statTotal: Int
)

data class TeamSuggestion(val name: String, val statTotal: Int)

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
        .mapNotNull { candidate ->
            val ownTypeDetails = candidate.types.mapNotNull { typeDetailsByType[it] }
            val defensive = computeDefensiveMultipliers(ownTypeDetails)
            if (sharedWeaknesses.none { (defensive[it] ?: 1.0) < 1.0 }) return@mapNotNull null

            val offensiveByOwnType = ownTypeDetails.associate { it.name to computeOffensiveMultipliers(it) }
            val bestOffense = bestOffensiveMultipliers(candidate.types, offensiveByOwnType)
            if (coverageGaps.none { (bestOffense[it] ?: 0.0) > 1.0 }) return@mapNotNull null

            TeamSuggestion(candidate.name, candidate.statTotal)
        }
        .sortedBy { it.statTotal }
        .take(limit)
        .toList()
}
