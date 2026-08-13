package com.mandallaz.pikadex.ui.components

import com.mandallaz.pikadex.util.TypeIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** F88 — every type gets its own icon, distinct colored pill/icon per type on top of B39's
 *  localized name. */
class TypeBadgeTest {

    @Test
    fun `every standard type has an icon`() {
        TypeIds.standardTypeNames.forEach { type ->
            assertNotNull("missing icon for $type", typeIcon(type))
        }
    }

    @Test
    fun `stellar and unknown also have icons despite no dedicated name translation`() {
        assertNotNull(typeIcon("stellar"))
        assertNotNull(typeIcon("unknown"))
    }

    @Test
    fun `lookup is case-insensitive`() {
        assertEquals(typeIcon("fire"), typeIcon("FIRE"))
    }

    @Test
    fun `no two types share the same icon`() {
        val icons = (TypeIds.standardTypeNames + listOf("stellar", "unknown")).map { typeIcon(it) }
        assertEquals("expected every type to have a distinct icon", icons.size, icons.toSet().size)
    }
}
