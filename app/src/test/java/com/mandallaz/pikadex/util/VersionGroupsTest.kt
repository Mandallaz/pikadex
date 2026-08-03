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
}
