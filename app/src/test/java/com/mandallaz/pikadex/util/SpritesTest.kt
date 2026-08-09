package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Sprites.baseSpeciesName] is what lets a form with no images of its own borrow its species'.
 * The forms it exists for (Koraidon's and Miraidon's traversal modes) have multi-segment names, so
 * "strip one segment" is not enough — that was the shape of the original bug.
 */
class SpritesTest {

    private val known = setOf("koraidon", "charizard", "charizard-mega-x", "tauros", "mr-mime")

    @Test
    fun `strips as many form segments as it takes to reach a known species`() {
        assertEquals("koraidon", Sprites.baseSpeciesName("koraidon-limited-build") { it in known })
        assertEquals("koraidon", Sprites.baseSpeciesName("koraidon-sprinting-build") { it in known })
    }

    @Test
    fun `prefers the longest known prefix rather than the bare species`() {
        // charizard-mega-x has its own artwork, so a "-gmax"-style suffix on it should fall back to
        // the mega, not all the way down to plain charizard.
        assertEquals(
            "charizard-mega-x",
            Sprites.baseSpeciesName("charizard-mega-x-something") { it in known }
        )
    }

    @Test
    fun `returns null for a name that is already a base species`() {
        assertNull(Sprites.baseSpeciesName("koraidon") { it in known })
    }

    @Test
    fun `returns null when no prefix is recognisable`() {
        assertNull(Sprites.baseSpeciesName("unknownmon-alpha-form") { it in known })
    }

    // Species whose own name contains hyphens ("mr-mime", "ho-oh") must not be mistaken for forms
    // and stripped down to a nonexistent "mr".
    @Test
    fun `hyphenated species name is not mistaken for a form`() {
        assertNull(Sprites.baseSpeciesName("mr-mime") { it in known })
        assertEquals("mr-mime", Sprites.baseSpeciesName("mr-mime-galar") { it in known })
    }

    @Test
    fun `image urls are built from the numeric id`() {
        assertTrue(Sprites.officialArtworkUrl(25).endsWith("/official-artwork/25.png"))
        assertTrue(Sprites.defaultSpriteUrl(25).endsWith("/pokemon/25.png"))
        assertTrue(Sprites.shinySpriteUrl(25).endsWith("/shiny/25.png"))
        assertTrue(Sprites.shinyOfficialArtworkUrl(25).endsWith("/official-artwork/shiny/25.png"))
    }

    @Test
    fun `showdown gif urls are built from the numeric id`() {
        assertTrue(Sprites.showdownGifUrl(25).endsWith("/showdown/25.gif"))
        assertTrue(Sprites.shinyShowdownGifUrl(25).endsWith("/showdown/shiny/25.gif"))
    }
}
