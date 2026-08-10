package com.mandallaz.pikadex.data

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamRepositoryTest {

    private fun resource(name: String, id: Int) = NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$id/")
    private fun idlessResource(name: String) = NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/not-a-number/")

    @Test
    fun `keeps members that have a numeric id`() {
        val team = listOf(resource("pikachu", 25), resource("bulbasaur", 1))
        assertEquals(team, persistableMembers(team))
    }

    @Test
    fun `drops members with no numeric id instead of encoding a fake one`() {
        val team = listOf(resource("pikachu", 25), idlessResource("weird-form"))
        assertEquals(listOf(resource("pikachu", 25)), persistableMembers(team))
    }

    @Test
    fun `an all id-less team persists as empty rather than all zeros`() {
        val team = listOf(idlessResource("a"), idlessResource("b"))
        assertEquals(emptyList<NamedApiResource>(), persistableMembers(team))
    }

    // issue #71 (B21) — confirmed live during B14's French testing: after creating the first team
    // on a fresh install, the Team screen's title bar read "My Team" in French mode, because the
    // hardcoded English literal was what got persisted at first-run migration. resolveStoredTeamName
    // is the fix's core: the persisted sentinel now maps to null, letting the UI resolve a localized
    // default instead — this test is the regression guard for that mapping.
    @Test
    fun `an unset team name resolves to null, not the old hardcoded English literal`() {
        assertEquals(null, resolveStoredTeamName(""))
        assertEquals(null, resolveStoredTeamName(null))
    }

    @Test
    fun `an existing custom team name (including one already stored as the old literal) passes through unchanged`() {
        assertEquals("My Team", resolveStoredTeamName("My Team"))
        assertEquals("Ash's Squad", resolveStoredTeamName("Ash's Squad"))
    }
}
