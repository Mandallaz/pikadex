package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** BACKLOG.md F15's "preview impact on my team" — plain set differences between the before/after
 *  shared-weakness and coverage-gap lists, so no case here needs a real matrix. */
class TeamImpactTest {

    @Test
    fun `a weakness present before but not after is fixed`() {
        val impact = computeTeamImpact(
            beforeSharedWeaknesses = listOf("water", "rock"),
            afterSharedWeaknesses = listOf("rock"),
            beforeCoverageGaps = emptyList(),
            afterCoverageGaps = emptyList()
        )
        assertEquals(listOf("water"), impact.weaknessesFixed)
        assertEquals(emptyList<String>(), impact.weaknessesIntroduced)
    }

    @Test
    fun `a weakness present after but not before is introduced`() {
        val impact = computeTeamImpact(
            beforeSharedWeaknesses = listOf("rock"),
            afterSharedWeaknesses = listOf("rock", "ice"),
            beforeCoverageGaps = emptyList(),
            afterCoverageGaps = emptyList()
        )
        assertEquals(emptyList<String>(), impact.weaknessesFixed)
        assertEquals(listOf("ice"), impact.weaknessesIntroduced)
    }

    @Test
    fun `a gap present before but not after is closed`() {
        val impact = computeTeamImpact(
            beforeSharedWeaknesses = emptyList(),
            afterSharedWeaknesses = emptyList(),
            beforeCoverageGaps = listOf("dragon"),
            afterCoverageGaps = emptyList()
        )
        assertEquals(listOf("dragon"), impact.gapsClosed)
        assertEquals(emptyList<String>(), impact.gapsOpened)
    }

    @Test
    fun `a gap present after but not before is opened`() {
        val impact = computeTeamImpact(
            beforeSharedWeaknesses = emptyList(),
            afterSharedWeaknesses = emptyList(),
            beforeCoverageGaps = emptyList(),
            afterCoverageGaps = listOf("steel")
        )
        assertEquals(emptyList<String>(), impact.gapsClosed)
        assertEquals(listOf("steel"), impact.gapsOpened)
    }

    @Test
    fun `an unchanged before and after reports nothing on any of the four lists`() {
        val impact = computeTeamImpact(
            beforeSharedWeaknesses = listOf("water"),
            afterSharedWeaknesses = listOf("water"),
            beforeCoverageGaps = listOf("dragon"),
            afterCoverageGaps = listOf("dragon")
        )
        assertEquals(emptyList<String>(), impact.weaknessesFixed)
        assertEquals(emptyList<String>(), impact.weaknessesIntroduced)
        assertEquals(emptyList<String>(), impact.gapsClosed)
        assertEquals(emptyList<String>(), impact.gapsOpened)
    }
}
