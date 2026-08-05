package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Move lists pick, per move, the entry from the highest-ranked version group, so this ordering is
 * what decides which game's data the user actually sees.
 */
class VersionGroupsTest {

    @Test
    fun `ranks run oldest to newest`() {
        assertTrue(VersionGroups.rank("red-blue") < VersionGroups.rank("gold-silver"))
        assertTrue(VersionGroups.rank("gold-silver") < VersionGroups.rank("sword-shield"))
        assertTrue(VersionGroups.rank("sword-shield") < VersionGroups.rank("scarlet-violet"))
    }

    // A game newer than this mapping ranks as the most recent possible: the alternative is ranking
    // it lowest, which would silently hide a pokemon's newest moveset behind an older game's.
    @Test
    fun `a version group newer than the mapping still outranks every known one`() {
        assertTrue(VersionGroups.rank("some-future-game") > VersionGroups.rank("scarlet-violet"))
    }

    @Test
    fun `ranking is deterministic for the same name`() {
        assertEquals(VersionGroups.rank("x-y"), VersionGroups.rank("x-y"))
    }

    // Colosseum and XD are Gen 3 spin-offs. They used to sit after HeartGold/SoulSilver in the
    // order list, so a move learned in XD outranked the same move's Gen 4 entry — the wrong
    // game's moveset (and levels) rendered for any Gen 1-3 species that appears in both.
    @Test
    fun `gen 3 spin-offs rank below every gen 4 game`() {
        listOf("diamond-pearl", "platinum", "heartgold-soulsilver").forEach { gen4 ->
            assertTrue(VersionGroups.rank("colosseum") < VersionGroups.rank(gen4))
            assertTrue(VersionGroups.rank("xd") < VersionGroups.rank(gen4))
        }
        assertTrue(VersionGroups.rank("firered-leafgreen") < VersionGroups.rank("colosseum"))
    }
}
