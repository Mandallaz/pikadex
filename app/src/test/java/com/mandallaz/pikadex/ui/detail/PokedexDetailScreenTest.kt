package com.mandallaz.pikadex.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class PokedexDetailScreenTest {

    @Test
    fun `no-eggs reads as Undiscovered, not the literal API name`() {
        assertEquals("Undiscovered", eggGroupDisplayName("no-eggs"))
    }

    @Test
    fun `an ordinary egg group name falls back to normal display formatting`() {
        assertEquals("Monster", eggGroupDisplayName("monster"))
        assertEquals("Human Like", eggGroupDisplayName("human-like"))
    }
}
