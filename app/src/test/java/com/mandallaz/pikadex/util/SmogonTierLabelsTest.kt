package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // issue #71 (B21) — labelFor used to return a hardcoded English String; it's now a @Composable
    // resolving a @StringRes id via stringResource(), which a plain JVM unit test can't call (no
    // Android Resources without Robolectric/instrumentation). These check labelResFor's id mapping
    // instead — the same invariant, one layer earlier.
    @Test
    fun `labelResFor is null for an unknown tier, same fallback-to-raw-code contract as before`() {
        assertNull(SmogonTierLabels.labelResFor("Weird"))
    }

    @Test
    fun `labelResFor resolves a known code to its resource id`() {
        assertEquals(R.string.smogon_tier_label_uu, SmogonTierLabels.labelResFor("UU"))
    }

    @Test
    fun `every tier code sortedTiers can return has a label`() {
        val allCodes = listOf(
            "AG", "Uber", "OU", "UUBL", "UU", "RUBL", "RU", "NUBL", "NU",
            "PUBL", "PU", "ZUBL", "ZU", "NFE", "LC", "CAP", "CAP NFE", "CAP LC"
        )
        assertEquals(allCodes.toSet(), SmogonTierLabels.sortedTiers(allCodes).toSet())
        allCodes.forEach { code ->
            assertTrue("missing label for $code", SmogonTierLabels.LABELS.containsKey(code))
        }
    }
}
