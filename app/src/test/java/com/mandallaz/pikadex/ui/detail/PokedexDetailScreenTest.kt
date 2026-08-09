package com.mandallaz.pikadex.ui.detail

import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource.MoveInfo
import com.mandallaz.pikadex.data.remote.dto.ShowdownSprites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // --- selectShowdownUrl (F38) -------------------------------------------------------

    private val bothVariants = ShowdownSprites(frontDefault = "default.gif", frontShiny = "shiny.gif")

    @Test
    fun `regular coloring picks the default animated sprite`() {
        assertEquals("default.gif", selectShowdownUrl(shiny = false, showdown = bothVariants))
    }

    @Test
    fun `shiny coloring picks the shiny animated sprite when it exists`() {
        assertEquals("shiny.gif", selectShowdownUrl(shiny = true, showdown = bothVariants))
    }

    // A real coverage gap (not every Pokemon has an animated shiny) falls back to the regular
    // animated sprite rather than showing nothing while the shiny toggle is on.
    @Test
    fun `shiny coloring falls back to the default animated sprite when no shiny variant exists`() {
        val defaultOnly = ShowdownSprites(frontDefault = "default.gif", frontShiny = null)
        assertEquals("default.gif", selectShowdownUrl(shiny = true, showdown = defaultOnly))
    }

    @Test
    fun `no Showdown sprite data at all returns null`() {
        assertNull(selectShowdownUrl(shiny = false, showdown = null))
    }
}
