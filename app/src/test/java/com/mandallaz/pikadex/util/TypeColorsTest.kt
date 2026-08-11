package com.mandallaz.pikadex.util

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
}
