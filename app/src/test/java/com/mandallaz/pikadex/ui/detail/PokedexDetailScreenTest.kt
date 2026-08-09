package com.mandallaz.pikadex.ui.detail

import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource.MoveInfo
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource.MoveStatChange
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

    // --- moveStatsLabel priority (F37) --------------------------------------------------

    @Test
    fun `zero priority (the vast majority of moves) is omitted from the label`() {
        val info = MoveInfo(type = "normal", damageClass = "physical", power = 40, accuracy = 100, pp = 35, priority = 0)
        assertEquals("Physical · Power 40 · Accuracy 100% · PP 35", moveStatsLabel(info))
    }

    @Test
    fun `positive priority is shown with an explicit plus sign`() {
        val info = MoveInfo(type = "normal", damageClass = "physical", power = 40, accuracy = 100, pp = 30, priority = 1)
        assertEquals("Physical · Power 40 · Accuracy 100% · PP 30 · Priority +1", moveStatsLabel(info))
    }

    @Test
    fun `negative priority keeps its own minus sign`() {
        val info = MoveInfo(type = "psychic", damageClass = "status", power = null, accuracy = null, pp = 5, priority = -7)
        assertEquals("Status · Power — · Accuracy — · PP 5 · Priority -7", moveStatsLabel(info))
    }

    // --- moveMetaLabel (F37) -------------------------------------------------------------

    private fun baseMoveInfo(
        priority: Int = 0,
        critRate: Int = 0,
        drain: Int = 0,
        healing: Int = 0,
        flinchChance: Int = 0,
        ailment: String = "none",
        ailmentChance: Int = 0,
        statChanges: List<MoveStatChange> = emptyList(),
        statChangeChance: Int = 0
    ) = MoveInfo(
        type = "normal", damageClass = "physical", power = 40, accuracy = 100, pp = 20,
        priority = priority, critRate = critRate, drain = drain, healing = healing,
        flinchChance = flinchChance, ailment = ailment, ailmentChance = ailmentChance,
        statChanges = statChanges, statChangeChance = statChangeChance
    )

    @Test
    fun `a move with no secondary effects renders no meta line at all`() {
        assertNull(moveMetaLabel(baseMoveInfo()))
    }

    @Test
    fun `an ailment with a chance shows the percentage`() {
        assertEquals("10% Paralysis", moveMetaLabel(baseMoveInfo(ailment = "paralysis", ailmentChance = 10)))
    }

    // ailment_chance 0 on a move that does have an ailment means unconditional, not "0% i.e. never".
    @Test
    fun `an ailment with zero chance reads as Always, not 0 percent`() {
        assertEquals("Always Paralysis", moveMetaLabel(baseMoveInfo(ailment = "paralysis", ailmentChance = 0)))
    }

    @Test
    fun `stat changes render with sign and display name, prefixed by their chance`() {
        val label = moveMetaLabel(
            baseMoveInfo(statChanges = listOf(MoveStatChange("special-defense", -1)), statChangeChance = 10)
        )
        assertEquals("10% chance: -1 Sp. Def", label)
    }

    @Test
    fun `a guaranteed stat change, like Swords Dance, has no chance prefix`() {
        val label = moveMetaLabel(baseMoveInfo(statChanges = listOf(MoveStatChange("attack", 2)), statChangeChance = 0))
        assertEquals("+2 Attack", label)
    }

    @Test
    fun `positive drain reads as drain, negative drain reads as recoil`() {
        assertEquals("Drains 50% dealt", moveMetaLabel(baseMoveInfo(drain = 50)))
        assertEquals("Recoil 25% dealt", moveMetaLabel(baseMoveInfo(drain = -25)))
    }

    @Test
    fun `healing, flinch chance and crit rate each render their own part`() {
        assertEquals("Heals 50% max HP", moveMetaLabel(baseMoveInfo(healing = 50)))
        assertEquals("30% Flinch", moveMetaLabel(baseMoveInfo(flinchChance = 30)))
        assertEquals("Crit rate +1", moveMetaLabel(baseMoveInfo(critRate = 1)))
    }

    @Test
    fun `multiple effects join into one line in a fixed order`() {
        val label = moveMetaLabel(
            baseMoveInfo(ailment = "burn", ailmentChance = 10, flinchChance = 30, critRate = 1)
        )
        assertEquals("10% Burn · Crit rate +1 · 30% Flinch", label)
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
