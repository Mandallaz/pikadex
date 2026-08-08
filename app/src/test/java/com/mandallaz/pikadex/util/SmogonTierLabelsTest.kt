package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmogonTierLabelsTest {

    @Test
    fun `the ceiling tier itself is at or below the ceiling`() {
        assertTrue(SmogonTierLabels.isAtOrBelowCeiling("UU", ceiling = "UU"))
    }

    @Test
    fun `a weaker tier is at or below the ceiling`() {
        assertTrue(SmogonTierLabels.isAtOrBelowCeiling("PU", ceiling = "UU"))
    }

    @Test
    fun `a stronger tier is not at or below the ceiling`() {
        assertFalse(SmogonTierLabels.isAtOrBelowCeiling("OU", ceiling = "UU"))
        assertFalse(SmogonTierLabels.isAtOrBelowCeiling("Uber", ceiling = "UU"))
    }

    @Test
    fun `an unrecognized tier is treated as weaker than every known tier`() {
        assertTrue(SmogonTierLabels.isAtOrBelowCeiling("SomeNewTier", ceiling = "PU"))
    }

    @Test
    fun `an unrecognized ceiling allows everything`() {
        assertTrue(SmogonTierLabels.isAtOrBelowCeiling("Uber", ceiling = "NotARealTier"))
    }

    @Test
    fun `labelFor falls back to the raw code for an unknown tier`() {
        assertEquals("Weird", SmogonTierLabels.labelFor("Weird"))
    }

    @Test
    fun `labelFor expands a known code`() {
        assertEquals("UnderUsed (UU)", SmogonTierLabels.labelFor("UU"))
    }
}
