package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.SmogonTierDataSource
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
    /** Which of the team's shared weaknesses this suggestion resists, and which of its coverage
     *  gaps it hits with STAB — the "why" behind the suggestion, shown on its tile, and also
     *  [rankSuggestions]' sort key (more of either, the more useful the pick). Always non-empty
     *  together (both required to qualify at all — see the "both required" rule below). */
    val weaknessesResisted: List<String> = emptyList(),
    val gapsHit: List<String> = emptyList()
)

/** Which of [sharedWeaknesses] [types] resists (<1.0) and which of [coverageGaps] it hits (>1.0)
 *  with its own typing (STAB only) — null unless *both* are non-empty, the "both required" rule
 *  used by [rankSuggestions]. */
private data class Qualification(val weaknessesResisted: List<String>, val gapsHit: List<String>)

private fun qualification(
    types: List<String>,
    sharedWeaknesses: List<String>,
    coverageGaps: List<String>,
    typeDetailsByType: Map<String, TypeDetailDto>
): Qualification? {
    val details = types.mapNotNull { typeDetailsByType[it] }
    val defensive = computeDefensiveMultipliers(details)
    val weaknessesResisted = sharedWeaknesses.filter { (defensive[it] ?: 1.0) < 1.0 }
    if (weaknessesResisted.isEmpty()) return null
    val offensiveByType = details.associate { it.name to computeOffensiveMultipliers(it) }
    val bestOffense = bestOffensiveMultipliers(types, offensiveByType)
    val gapsHit = coverageGaps.filter { (bestOffense[it] ?: 0.0) > 1.0 }
    if (gapsHit.isEmpty()) return null
    return Qualification(weaknessesResisted, gapsHit)
}

/**
 * Drops candidates whose competitive tier is above [maxTier] — see [SmogonTierLabels.isAtOrBelowCeiling]
 * for the "this tier or below" rule and issue #11. [maxTier] null (no limit set) is a no-op.
 * A candidate absent from [tierByShowdownKey] (untiered/unclassified on Showdown) is kept rather
 * than excluded — "unknown" isn't evidence it's too strong for the chosen ceiling.
 */
fun filterByTierCeiling(
    candidates: List<SuggestionCandidate>,
    maxTier: String?,
    tierByShowdownKey: Map<String, String>
): List<SuggestionCandidate> {
    if (maxTier == null) return candidates
    return candidates.filter { candidate ->
        val tier = tierByShowdownKey[SmogonTierDataSource.showdownKey(candidate.name)] ?: return@filter true
        SmogonTierLabels.isAtOrBelowCeiling(tier, maxTier)
    }
}

/**
 * Candidates that would help a team stuck with [sharedWeaknesses] and [coverageGaps] at once — a
 * candidate qualifies only if it resists (<1.0) at least one shared weakness *and* hits (>1.0) at
 * least one coverage gap with its own typing, both required. Sorted by total impact — the number of
 * shared weaknesses resisted plus coverage gaps hit — descending, so the pick that fixes the most
 * at once leads; [SuggestionCandidate.statTotal] ascending breaks ties, so among equally useful
 * picks the least overpowering one leads. Capped at [limit].
 *
 * Offense is STAB only (a candidate's own types, not its movepool).
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
    limit: Int = 6
): List<TeamSuggestion> {
    if (sharedWeaknesses.isEmpty() || coverageGaps.isEmpty()) return emptyList()
    return candidates.asSequence()
        .filter { it.name !in excludeNames }
        .mapNotNull { candidate ->
            val q = qualification(candidate.types, sharedWeaknesses, coverageGaps, typeDetailsByType) ?: return@mapNotNull null
            TeamSuggestion(candidate.name, candidate.statTotal, candidate.types, q.weaknessesResisted, q.gapsHit)
        }
        .sortedWith(
            compareByDescending<TeamSuggestion> { it.weaknessesResisted.size + it.gapsHit.size }
                .thenBy { it.statTotal }
        )
        .take(limit)
        .toList()
}
