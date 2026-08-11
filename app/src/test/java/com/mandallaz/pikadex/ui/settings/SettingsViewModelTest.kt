package com.mandallaz.pikadex.ui.settings

import com.mandallaz.pikadex.data.PrefetchTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F72 — no test coverage previously existed for [SettingsViewModel] at all; this is specifically
 * the highest-value gap the full-project review flagged: [SettingsViewModel.isMeteredNetworkBlocked]
 * is the thing standing between a user and an unwanted 300MB cellular download, and the
 * tier-selection mapping in [SettingsViewModel.startPrefetch] was likewise untested.
 *
 * Deliberately tests the pure [SettingsViewModel.isBlocked]/[SettingsViewModel.selectedTiers]
 * companion functions rather than constructing a real [SettingsViewModel] — that's an
 * `AndroidViewModel` whose `init` unconditionally calls `measureStorage()`, which reaches real
 * `Dispatchers.IO` background work with no lightweight JVM double (see [SettingsViewModel.isBlocked]'s
 * own doc for the full story of why that's a real, previously-hit flakiness trap, not just
 * theoretical caution).
 */
class SettingsViewModelTest {

    // --- isBlocked ---------------------------------------------------------------------

    @Test
    fun `not blocked when wifi-only is off, even on a metered network`() {
        assertFalse(SettingsViewModel.isBlocked(wifiOnlyEnabled = false, metered = true))
    }

    @Test
    fun `not blocked when wifi-only is on but the network is unmetered`() {
        assertFalse(SettingsViewModel.isBlocked(wifiOnlyEnabled = true, metered = false))
    }

    @Test
    fun `blocked only when wifi-only is on and the network is metered`() {
        assertTrue(SettingsViewModel.isBlocked(wifiOnlyEnabled = true, metered = true))
    }

    @Test
    fun `not blocked when wifi-only is off and the network is unmetered`() {
        assertFalse(SettingsViewModel.isBlocked(wifiOnlyEnabled = false, metered = false))
    }

    // --- selectedTiers -------------------------------------------------------------------

    @Test
    fun `selectedTiers includes only the enabled tiers, in a fixed order`() {
        val state = SettingsUiState(
            essentialsEnabled = true,
            spritesEnabled = false,
            spritesExtraEnabled = true,
            fullDetailEnabled = false,
            criesEnabled = true
        )
        assertEquals(
            listOf(PrefetchTier.ESSENTIALS, PrefetchTier.SPRITES_EXTRA, PrefetchTier.CRIES),
            SettingsViewModel.selectedTiers(state)
        )
    }

    @Test
    fun `selectedTiers is empty when every tier is off`() {
        val state = SettingsUiState(
            essentialsEnabled = false,
            spritesEnabled = false,
            spritesExtraEnabled = false,
            fullDetailEnabled = false,
            criesEnabled = false
        )
        assertEquals(emptyList<PrefetchTier>(), SettingsViewModel.selectedTiers(state))
    }

    @Test
    fun `selectedTiers includes every tier when all are on`() {
        val state = SettingsUiState(
            essentialsEnabled = true,
            spritesEnabled = true,
            spritesExtraEnabled = true,
            fullDetailEnabled = true,
            criesEnabled = true
        )
        assertEquals(PrefetchTier.entries.toList(), SettingsViewModel.selectedTiers(state))
    }
}
