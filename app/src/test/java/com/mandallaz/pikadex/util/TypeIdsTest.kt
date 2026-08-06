package com.mandallaz.pikadex.util

import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeIdsTest {

    @Test
    fun `standardTypeNames is computed once, not rebuilt on every read`() {
        // A `get()` accessor would rebuild the list (and its backing keySet view) on every access;
        // a plain val returns the exact same instance every time.
        assertSame(TypeIds.standardTypeNames, TypeIds.standardTypeNames)
    }

    @Test
    fun `standardTypeNames follows the type-chart reading order, not PokeAPI id order`() {
        assertEquals(
            listOf(
                "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison",
                "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"
            ),
            TypeIds.standardTypeNames
        )
    }
}
