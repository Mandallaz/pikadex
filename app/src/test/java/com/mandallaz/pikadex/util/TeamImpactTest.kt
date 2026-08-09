package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** issue #2's "preview impact on my team" — plain set differences between the before/after
 *  lists on every axis, so no case here needs a real matrix. */
class TeamImpactTest {

    // A default of "no change" on every axis a given test isn't exercising, so each test only
    // spells out the parameters it actually cares about.
    private fun impact(
        beforeSharedWeaknesses: List<String> = emptyList(),
        afterSharedWeaknesses: List<String> = emptyList(),
        beforeCoverageGaps: List<String> = emptyList(),
        afterCoverageGaps: List<String> = emptyList(),
        beforeImmunities: List<String> = emptyList(),
        afterImmunities: List<String> = emptyList(),
        beforeQuadWeaknesses: List<String> = emptyList(),
        afterQuadWeaknesses: List<String> = emptyList(),
        beforeResistances: List<String> = emptyList(),
        afterResistances: List<String> = emptyList()
    ) = computeTeamImpact(
        beforeSharedWeaknesses, afterSharedWeaknesses,
        beforeCoverageGaps, afterCoverageGaps,
        beforeImmunities, afterImmunities,
        beforeQuadWeaknesses, afterQuadWeaknesses,
        beforeResistances, afterResistances
    )

    @Test
    fun `a weakness present before but not after is fixed`() {
        val result = impact(beforeSharedWeaknesses = listOf("water", "rock"), afterSharedWeaknesses = listOf("rock"))
        assertEquals(listOf("water"), result.weaknessesFixed)
        assertEquals(emptyList<String>(), result.weaknessesIntroduced)
    }

    @Test
    fun `a weakness present after but not before is introduced`() {
        val result = impact(beforeSharedWeaknesses = listOf("rock"), afterSharedWeaknesses = listOf("rock", "ice"))
        assertEquals(emptyList<String>(), result.weaknessesFixed)
        assertEquals(listOf("ice"), result.weaknessesIntroduced)
    }

    @Test
    fun `a gap present before but not after is closed`() {
        val result = impact(beforeCoverageGaps = listOf("dragon"), afterCoverageGaps = emptyList())
        assertEquals(listOf("dragon"), result.gapsClosed)
        assertEquals(emptyList<String>(), result.gapsOpened)
    }

    @Test
    fun `a gap present after but not before is opened`() {
        val result = impact(beforeCoverageGaps = emptyList(), afterCoverageGaps = listOf("steel"))
        assertEquals(emptyList<String>(), result.gapsClosed)
        assertEquals(listOf("steel"), result.gapsOpened)
    }

    // Toedscool (Ground/Grass) added to an all-Fire preset team — the case that motivated these two
    // axes: no member of the team was immune to Electric before, and none was ×4 weak to Ice before,
    // but the majority-based sharedWeaknesses rule alone never surfaces either since a single new
    // member is nowhere near "half the team".
    @Test
    fun `an immunity only one member carries is still reported as gained`() {
        val result = impact(beforeImmunities = emptyList(), afterImmunities = listOf("electric"))
        assertEquals(listOf("electric"), result.immunitiesGained)
    }

    @Test
    fun `a quad weakness only one member carries is still reported as gained`() {
        val result = impact(beforeQuadWeaknesses = emptyList(), afterQuadWeaknesses = listOf("ice"))
        assertEquals(listOf("ice"), result.quadWeaknessesGained)
    }

    // Kingdra (Water/Dragon) added to an all-Fire preset team — its own ½x resistance to Water is
    // a real defensive gain the majority rule can't see from one new member alone either.
    @Test
    fun `a resistance only one member carries is still reported as gained`() {
        val result = impact(beforeResistances = emptyList(), afterResistances = listOf("water"))
        assertEquals(listOf("water"), result.resistancesGained)
    }

    @Test
    fun `an immunity, quad weakness or resistance already present before is not reported as newly gained`() {
        val result = impact(
            beforeImmunities = listOf("electric"), afterImmunities = listOf("electric"),
            beforeQuadWeaknesses = listOf("ice"), afterQuadWeaknesses = listOf("ice"),
            beforeResistances = listOf("water"), afterResistances = listOf("water")
        )
        assertEquals(emptyList<String>(), result.immunitiesGained)
        assertEquals(emptyList<String>(), result.quadWeaknessesGained)
        assertEquals(emptyList<String>(), result.resistancesGained)
    }

    @Test
    fun `an unchanged before and after reports nothing on any of the seven lists`() {
        val result = impact(
            beforeSharedWeaknesses = listOf("water"), afterSharedWeaknesses = listOf("water"),
            beforeCoverageGaps = listOf("dragon"), afterCoverageGaps = listOf("dragon"),
            beforeImmunities = listOf("electric"), afterImmunities = listOf("electric"),
            beforeQuadWeaknesses = listOf("ice"), afterQuadWeaknesses = listOf("ice"),
            beforeResistances = listOf("water"), afterResistances = listOf("water")
        )
        assertEquals(emptyList<String>(), result.weaknessesFixed)
        assertEquals(emptyList<String>(), result.weaknessesIntroduced)
        assertEquals(emptyList<String>(), result.gapsClosed)
        assertEquals(emptyList<String>(), result.gapsOpened)
        assertEquals(emptyList<String>(), result.immunitiesGained)
        assertEquals(emptyList<String>(), result.quadWeaknessesGained)
        assertEquals(emptyList<String>(), result.resistancesGained)
    }
}
