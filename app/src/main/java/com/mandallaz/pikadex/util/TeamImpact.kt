package com.mandallaz.pikadex.util

/** Delta between a team's shared weaknesses/coverage gaps before and after a hypothetical roster
 *  change (BACKLOG.md F15) — what a candidate Pokémon would change if added to or swapped into the
 *  team, shown as a text summary on the Pokédex detail screen.
 *
 *  [weaknessesFixed]/[weaknessesIntroduced] are majority-based (see [sharedWeaknesses]) — a real
 *  team-wide vulnerability, but one a single new member's own severe typing can't move unless it
 *  tips at least half the roster. [immunitiesGained]/[quadWeaknessesGained]/[resistancesGained] are
 *  the per-member counterpart, added after user feedback that adding e.g. Toedscool (Ground/Grass)
 *  to an all-Fire preset team didn't mention it bringing the team's first immunity to Electric or
 *  its own ×4 weakness to Ice, and that adding Kingdra (Water/Dragon) didn't mention its own
 *  resistance to Water — real, single-Pokémon-driven changes the majority rule is blind to. */
data class TeamImpactSummary(
    val weaknessesFixed: List<String>,
    val weaknessesIntroduced: List<String>,
    val gapsClosed: List<String>,
    val gapsOpened: List<String>,
    val immunitiesGained: List<String>,
    val quadWeaknessesGained: List<String>,
    val resistancesGained: List<String>
)

/** Plain set differences between the "before" and "after" lists on every axis. Only ever the
 *  gained/closed direction for the immunity, quad-weakness and resistance axes: the caller only
 *  ever *adds* a candidate (never removes one), and a team's collective min (immunity/resistance)
 *  and max (quad weakness) multiplier per type can only move toward "more covered" as members are
 *  added, never regress — so there's no symmetric "lost" case to compute here. */
fun computeTeamImpact(
    beforeSharedWeaknesses: List<String>,
    afterSharedWeaknesses: List<String>,
    beforeCoverageGaps: List<String>,
    afterCoverageGaps: List<String>,
    beforeImmunities: List<String>,
    afterImmunities: List<String>,
    beforeQuadWeaknesses: List<String>,
    afterQuadWeaknesses: List<String>,
    beforeResistances: List<String>,
    afterResistances: List<String>
): TeamImpactSummary = TeamImpactSummary(
    weaknessesFixed = beforeSharedWeaknesses - afterSharedWeaknesses.toSet(),
    weaknessesIntroduced = afterSharedWeaknesses - beforeSharedWeaknesses.toSet(),
    gapsClosed = beforeCoverageGaps - afterCoverageGaps.toSet(),
    gapsOpened = afterCoverageGaps - beforeCoverageGaps.toSet(),
    immunitiesGained = afterImmunities - beforeImmunities.toSet(),
    quadWeaknessesGained = afterQuadWeaknesses - beforeQuadWeaknesses.toSet(),
    resistancesGained = afterResistances - beforeResistances.toSet()
)
