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
}
