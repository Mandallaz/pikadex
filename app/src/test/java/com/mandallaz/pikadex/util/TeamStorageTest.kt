package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamStorageTest {

    private fun resource(name: String, id: Int) = NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$id/")

    @Test
    fun `team ids round-trip through encode and decode`() {
        val ids = listOf(1, 2, 5)
        assertEquals(ids, decodeTeamIds(encodeTeamIds(ids)))
    }

    @Test
    fun `decodeTeamIds returns empty for null or blank input`() {
        assertEquals(emptyList<Int>(), decodeTeamIds(null))
        assertEquals(emptyList<Int>(), decodeTeamIds(""))
    }

    @Test
    fun `decodeTeamIds drops a malformed entry rather than crashing`() {
        assertEquals(listOf(1, 3), decodeTeamIds("1,notanumber,3"))
    }

    @Test
    fun `members round-trip through encode and decode`() {
        val members = listOf(resource("pikachu", 25), resource("bulbasaur", 1))
        assertEquals(members, decodeMembers(encodeMembers(members)))
    }

    @Test
    fun `decodeMembers returns empty for null or blank input`() {
        assertEquals(emptyList<NamedApiResource>(), decodeMembers(null))
        assertEquals(emptyList<NamedApiResource>(), decodeMembers(""))
        assertEquals(emptyList<NamedApiResource>(), decodeMembers("   "))
    }

    @Test
    fun `decodeMembers drops an entry with no id field rather than crashing`() {
        assertEquals(listOf(resource("bulbasaur", 1)), decodeMembers("pikachu,bulbasaur|1"))
    }
}
