package com.mandallaz.pikadex.ui.detail

import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource.MoveInfo
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

    @Test
    fun `move stats label includes PP alongside power and accuracy`() {
        val info = MoveInfo(type = "fire", damageClass = "physical", power = 40, accuracy = 100, pp = 25)
        assertEquals("Physical · Power 40 · Accuracy 100% · PP 25", moveStatsLabel(info))
    }

    @Test
    fun `a null pp shows as a dash rather than an empty gap`() {
        val info = MoveInfo(type = "normal", damageClass = "status", power = null, accuracy = null, pp = null)
        assertEquals("Status · Power — · Accuracy — · PP —", moveStatsLabel(info))
    }
}
