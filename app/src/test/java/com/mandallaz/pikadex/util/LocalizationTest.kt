package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private data class Entry(val language: String, val text: String)

class LocalizationTest {

    private val entries = listOf(
        Entry("en", "Seed Pokémon"),
        Entry("fr", "Pokémon Graine"),
        Entry("de", "Samen-Pokémon")
    )

    @Test
    fun `picks the entry matching the requested language`() {
        assertEquals("Pokémon Graine", entries.localizedOrEnglish("fr") { it.language }?.text)
    }

    @Test
    fun `falls back to English when the requested language has no entry`() {
        assertEquals("Seed Pokémon", entries.localizedOrEnglish("ja") { it.language }?.text)
    }

    @Test
    fun `requesting English directly returns the English entry`() {
        assertEquals("Seed Pokémon", entries.localizedOrEnglish("en") { it.language }?.text)
    }

    @Test
    fun `an empty or null list returns null rather than throwing`() {
        assertNull(emptyList<Entry>().localizedOrEnglish("fr") { it.language })
        assertNull((null as List<Entry>?).localizedOrEnglish("fr") { it.language })
    }

    @Test
    fun `no entry at all (not even English) returns null`() {
        val noEnglish = listOf(Entry("de", "Samen-Pokémon"))
        assertNull(noEnglish.localizedOrEnglish("fr") { it.language })
    }

    // --- localizedDisplayName (B9) -----------------------------------------------------

    private val bulbasaurNames = mapOf(
        "en" to "Bulbasaur",
        "fr" to "Bulbizarre",
        "de" to "Bisasam"
    )
    private val speciesNames = mapOf("bulbasaur" to bulbasaurNames)

    // The bug itself: before B9, every screen showed the raw "bulbasaur" (formatted, not
    // translated) regardless of the picked language. This is the regression guard for it.
    @Test
    fun `resolves the localized species name for a non-English language`() {
        assertEquals("Bulbizarre", "bulbasaur".localizedDisplayName(speciesNames, "fr"))
    }

    @Test
    fun `falls back to English when the requested language has no species-name entry`() {
        assertEquals("Bulbasaur", "bulbasaur".localizedDisplayName(speciesNames, "ja"))
    }

    // English deliberately keeps toDisplayName()'s own special-case formatting (e.g.
    // "nidoran-f" -> "Nidoran♀") rather than PokeAPI's own "en" entry, even when one is present —
    // zero behavior change for the default language.
    @Test
    fun `English ignores the species-names map entirely, even when present`() {
        val namesWithDifferentEnglish = mapOf("bulbasaur" to mapOf("en" to "Something Else"))
        assertEquals("Bulbasaur", "bulbasaur".localizedDisplayName(namesWithDifferentEnglish, "en"))
    }

    @Test
    fun `a species missing from the map falls back to the formatted raw name`() {
        assertEquals("Charizard", "charizard".localizedDisplayName(speciesNames, "fr"))
    }

    @Test
    fun `an empty map falls back to the formatted raw name for any language`() {
        assertEquals("Nidoran♀", "nidoran-f".localizedDisplayName(emptyMap(), "fr"))
    }

    // --- RarityFilter Localization (B46) ------------------------------------------------

    @Test
    fun `RarityFilter entries store string resource IDs rather than hardcoded string labels`() {
        // Asserting that the enum uses valid string resource IDs rather than any hardcoded string label.
        // It should have no label property of type String, and all entries should point to valid, distinct, non-zero resource IDs.
        val entries = RarityFilter.entries
        assertEquals(3, entries.size)

        val legendaryResId = RarityFilter.LEGENDARY.labelResId
        val mythicalResId = RarityFilter.MYTHICAL.labelResId
        val ordinaryResId = RarityFilter.ORDINARY.labelResId

        // Assert they are valid non-zero resource IDs
        assert(legendaryResId != 0)
        assert(mythicalResId != 0)
        assert(ordinaryResId != 0)

        // Assert they are distinct resource IDs
        assert(legendaryResId != mythicalResId)
        assert(legendaryResId != ordinaryResId)
        assert(mythicalResId != ordinaryResId)
    }
}
