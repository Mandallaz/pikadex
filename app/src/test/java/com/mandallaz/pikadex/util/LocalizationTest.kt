package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.R
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

    // --- resolvedTypeNames (B45) -------------------------------------------------------

    @Test
    fun `resolvedTypeNames resolves weaknesses and gaps to localized string resource IDs`() {
        val weaknesses = listOf("fire", "water")
        val resolved = weaknesses.resolvedTypeNames()

        // Before the fix, they would have been formatted to "Fire" and "Water" Strings.
        // After the fix, they are resolved to R.string.type_fire and R.string.type_water resource IDs (Ints).
        assertEquals(listOf(R.string.type_fire, R.string.type_water), resolved)
    }

    @Test
    fun `resolvedTypeNames resolves suggestion tile resistances and hits to localized resource IDs or falls back`() {
        val suggestionTypes = listOf("fairy", "unknown-type")
        val resolved = suggestionTypes.resolvedTypeNames()

        // Before the fix, they would have been formatted to "Fairy" and "Unknown Type" Strings.
        // After the fix, standard types are resolved to resource IDs (Ints), and others fall back to displayName.
        assertEquals(listOf(R.string.type_fairy, "Unknown Type"), resolved)
    }
}
