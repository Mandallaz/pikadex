package com.mandallaz.pikadex.util

import androidx.compose.ui.graphics.Color
import com.mandallaz.pikadex.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** B39 — type names are localized via [typeNameRes] rather than [toDisplayName], which only ever
 *  formats the raw English PokeAPI slug. */
class TypeColorsTest {

    @Test
    fun `every standard type has a localized name resource`() {
        TypeIds.standardTypeNames.forEach { type ->
            assertNotNull("missing type name resource for $type", typeNameRes(type))
        }
    }

    @Test
    fun `fire resolves to its own string resource, not a generic fallback`() {
        assertEquals(R.string.type_fire, typeNameRes("fire"))
    }

    @Test
    fun `lookup is case-insensitive`() {
        assertEquals(R.string.type_fire, typeNameRes("FIRE"))
    }

    @Test
    fun `stellar has a dedicated translation despite not being one of the 18 standard types`() {
        assertEquals(R.string.type_stellar, typeNameRes("stellar"))
    }

    @Test
    fun `a type with no dedicated translation falls back to null, not a crash`() {
        assertNull(typeNameRes("unknown"))
        assertNull(typeNameRes("shadow"))
    }

    // B57 — typeShortNameEn backs TypeBadge's F114 narrow-width abbreviation fallback but had no
    // JVM unit test of its own, only an instrumented Compose test of the badge that uses it.
    @Test
    fun `every one of the 18 standard types has a short English name`() {
        TypeIds.standardTypeNames.forEach { type ->
            assertNotNull("missing short name for $type", typeShortNameEn(type))
        }
    }

    @Test
    fun `short name lookup is case-insensitive`() {
        assertEquals("Fight", typeShortNameEn("FIGHTING"))
    }

    @Test
    fun `abbreviations only exist where the full name is meaningfully longer`() {
        assertEquals("Elect", typeShortNameEn("electric"))
        assertEquals("Fight", typeShortNameEn("fighting"))
        assertEquals("Psy", typeShortNameEn("psychic"))
        // Already-short names keep their full spelling rather than an arbitrary truncation.
        assertEquals("Ice", typeShortNameEn("ice"))
        assertEquals("Bug", typeShortNameEn("bug"))
    }

    // stellar isn't one of the 18 standard types (see the dedicated-translation test above) and
    // TypeBadge falls back to the full localized name whenever this returns null — that fallback
    // is the one being exercised here, not a claim that stellar/unknown are unsupported.
    @Test
    fun `a type with no short name falls back to null, not a crash`() {
        assertNull(typeShortNameEn("stellar"))
        assertNull(typeShortNameEn("unknown"))
    }

    @Test
    fun `TypeColors of returns each standard type's own color`() {
        assertEquals(Color(0xFFF08030), TypeColors.of("fire"))
        assertEquals(Color(0xFF6890F0), TypeColors.of("water"))
    }

    @Test
    fun `TypeColors of is case-insensitive`() {
        assertEquals(TypeColors.of("fire"), TypeColors.of("FIRE"))
    }

    @Test
    fun `TypeColors of falls back to the unknown-type color rather than crashing`() {
        assertEquals(Color(0xFF68A090), TypeColors.of("some-future-type"))
    }
}
