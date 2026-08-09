package com.mandallaz.pikadex.util

/** Delta between a team's shared weaknesses/coverage gaps before and after a hypothetical roster
 *  change (BACKLOG.md F15) — what a candidate Pokémon would change if added to or swapped into the
 *  team, shown as a text summary on the Pokédex detail screen. */
data class TeamImpactSummary(
    val weaknessesFixed: List<String>,
    val weaknessesIntroduced: List<String>,
    val gapsClosed: List<String>,
    val gapsOpened: List<String>
)

/** Plain set differences between the "before" and "after" shared-weakness/coverage-gap lists. */
fun computeTeamImpact(
    beforeSharedWeaknesses: List<String>,
    afterSharedWeaknesses: List<String>,
    beforeCoverageGaps: List<String>,
    afterCoverageGaps: List<String>
): TeamImpactSummary = TeamImpactSummary(
    weaknessesFixed = beforeSharedWeaknesses - afterSharedWeaknesses.toSet(),
    weaknessesIntroduced = afterSharedWeaknesses - beforeSharedWeaknesses.toSet(),
    gapsClosed = beforeCoverageGaps - afterCoverageGaps.toSet(),
    gapsOpened = afterCoverageGaps - beforeCoverageGaps.toSet()
)
