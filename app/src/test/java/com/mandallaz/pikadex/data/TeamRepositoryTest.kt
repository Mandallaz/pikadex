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
}
