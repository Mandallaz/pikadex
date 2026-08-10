package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Smogon.linksFor] decides which generations of Smogon's strategy dex a pokemon actually has a
 * page in. Every case below is one that shipped wrong at some point: the rules are a pile of
 * special cases about forms, and nothing in the type system stops the next edit from re-breaking
 * one while fixing another.
 */
class SmogonTest {

    private fun codesFor(
        name: String,
        speciesGeneration: String = "generation-i",
        formVersionGroup: String? = null
    ) = Smogon.linksFor(name, speciesGeneration, formVersionGroup).map { it.code }

    @Test
    fun `ordinary species links every generation from its debut to the present, newest first`() {
        assertEquals(
            listOf("sv", "ss", "sm", "xy", "bw", "dp", "rs", "gs", "rb"),
            codesFor("bulbasaur", speciesGeneration = "generation-i")
        )
    }

    @Test
    fun `a species that debuts later does not link to generations before it existed`() {
        assertEquals(listOf("sv", "ss"), codesFor("zacian", speciesGeneration = "generation-viii"))
    }

    @Test
    fun `url points at the smogon dex page for that generation and name`() {
        val link = Smogon.linksFor("bulbasaur", "generation-i").last()
        assertEquals("https://www.smogon.com/dex/rb/pokemon/bulbasaur/", link.url)
    }

    // The suffix used to be matched with endsWith, which "charizard-mega-x" fails — an X/Y-era form
    // was sent back to its species' generation and offered links from Red/Blue onwards.
    @Test
    fun `mega form with a trailing variant letter is still recognised as a mega`() {
        assertEquals(listOf("sm", "xy"), codesFor("charizard-mega-x", speciesGeneration = "generation-i"))
    }

    @Test
    fun `plain mega form starts at its own debut, not its species'`() {
        assertEquals(listOf("sm", "xy"), codesFor("venusaur-mega", speciesGeneration = "generation-i"))
    }

    // Links used to run to the present for every form, so Megas advertised Sword/Shield and
    // Scarlet/Violet pages for a mechanic those games removed.
    @Test
    fun `forms whose mechanic was removed stop at its last generation`() {
        assertTrue("sv" !in codesFor("venusaur-mega", speciesGeneration = "generation-i"))
        assertTrue("ss" !in codesFor("venusaur-mega", speciesGeneration = "generation-i"))
        assertEquals(listOf("ss"), codesFor("charizard-gmax", speciesGeneration = "generation-i"))
        assertEquals(listOf("sm"), codesFor("raticate-totem-alola", speciesGeneration = "generation-i"))
    }

    @Test
    fun `regional form starts at the generation that introduced the region`() {
        assertEquals(listOf("sv", "ss", "sm"), codesFor("raichu-alola", speciesGeneration = "generation-i"))
        assertEquals(listOf("sv", "ss"), codesFor("meowth-galar", speciesGeneration = "generation-i"))
        assertEquals(listOf("sv"), codesFor("tauros-paldea-blaze", speciesGeneration = "generation-i"))
    }

    @Test
    fun `the form's own version group wins over the guess made from its name`() {
        // Suffix alone would say Gen 6; the form's real version group says Gen 7.
        assertEquals(
            listOf("sm"),
            codesFor("necrozma-mega", speciesGeneration = "generation-vii", formVersionGroup = "ultra-sun-ultra-moon")
        )
    }

    // Roughly half of all "-mega" forms are Legends Z-A ones, whose version group Smogon has no dex
    // for at all. Guessing from the suffix would hand them Gen 6/7 links that 404.
    @Test
    fun `form from a version group smogon has no dex for gets no links at all`() {
        assertEquals(emptyList<String>(), codesFor("dragonite-mega", formVersionGroup = "mega-dimension"))
    }

    @Test
    fun `form whose debut is later than its mechanic's end gets no links rather than an inverted range`() {
        assertEquals(
            emptyList<String>(),
            codesFor("charizard-mega-x", formVersionGroup = "scarlet-violet")
        )
    }

    // PokeAPI occasionally returns a species generation this mapping doesn't know. Showing every
    // generation is the graceful outcome; throwing or showing none is not.
    @Test
    fun `unknown species generation falls back to listing everything`() {
        assertEquals(9, codesFor("some-future-mon", speciesGeneration = "generation-x").size)
    }

    // issue #71 (B21) — label used to be a bare String, checkable by content; it's now a @StringRes
    // id (resolved at render time via stringResource(), which needs a Composable/Android Resources
    // a plain JVM unit test doesn't have), so this checks the id matches the expected resource
    // instead of the rendered text.
    @Test
    fun `labels resolve to the human-readable resource, not a bare dex code`() {
        val labelResIds = Smogon.linksFor("bulbasaur", "generation-i").map { it.labelRes }
        assertEquals(R.string.smogon_gen_9_label, labelResIds.first())
        assertEquals(R.string.smogon_gen_1_label, labelResIds.last())
    }

    @Test
    fun `all generations list runs oldest to newest`() {
        assertEquals("rb", Smogon.ALL_GENERATIONS.first().code)
        assertEquals("sv", Smogon.ALL_GENERATIONS.last().code)
    }
}
