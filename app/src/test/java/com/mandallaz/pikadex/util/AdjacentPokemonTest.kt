package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AdjacentPokemonTest {

    private val list = listOf("bulbasaur", "ivysaur", "venusaur")

    @Test
    fun `empty list returns null both directions`() {
        assertEquals(null to null, adjacentNames(emptyList(), "bulbasaur"))
    }

    @Test
    fun `single entry list has no previous or next`() {
        assertEquals(null to null, adjacentNames(listOf("bulbasaur"), "bulbasaur"))
    }

    @Test
    fun `first entry has no previous`() {
        assertEquals(null to "ivysaur", adjacentNames(list, "bulbasaur"))
    }

    @Test
    fun `middle entry has both`() {
        assertEquals("bulbasaur" to "venusaur", adjacentNames(list, "ivysaur"))
    }

    @Test
    fun `last entry has no next`() {
        assertEquals("ivysaur" to null, adjacentNames(list, "venusaur"))
    }

    @Test
    fun `name absent from the list returns null both directions`() {
        assertEquals(null to null, adjacentNames(list, "mew"))
    }

    private val master = listOf("bulbasaur", "charmander", "squirtle", "pikachu")

    @Test
    fun `namesForAdjacency uses the filtered list when the current name is in it`() {
        val filtered = listOf("charmander", "vulpix", "growlithe")
        assertEquals(filtered, namesForAdjacency(filtered, master, "charmander"))
    }

    @Test
    fun `namesForAdjacency falls back to master list when the current name isn't filtered in`() {
        // e.g. the type filter was Fire but this Pokémon was reached via an evolution chain tap,
        // Compare, or a team member chip rather than the filtered grid itself.
        val filtered = listOf("charmander", "vulpix", "growlithe")
        assertEquals(master, namesForAdjacency(filtered, master, "pikachu"))
    }

    @Test
    fun `namesForAdjacency falls back to master list when nothing has been displayed yet`() {
        assertEquals(master, namesForAdjacency(emptyList(), master, "bulbasaur"))
    }
}
