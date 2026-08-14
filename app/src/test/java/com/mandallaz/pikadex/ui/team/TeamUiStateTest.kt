package com.mandallaz.pikadex.ui.team

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.util.TypeIds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B36/F99 — [TeamUiState.hasUnfixableSingleAxisIssue] powers the explanatory message TeamScreen shows
 * in place of the Suggestions card when [TeamViewModel.loadSuggestions]'s dual-fix gate (needs
 * both a shared weakness and a coverage gap) can never be satisfied, so the card doesn't just
 * vanish with no explanation. The derived values are no longer getters — they're computed once by
 * [TeamUiState.withDerivedFields] at publish time and stored — so these tests exercise that
 * derivation directly on hand-built states, no ViewModel/coroutines needed.
 */
class TeamUiStateTest {

    private val squirtle = NamedApiResource("squirtle", "https://pokeapi.co/api/v2/pokemon/7/")

    private fun stateWith(
        weakToFire: Boolean,
        noCoverageGaps: Boolean
    ): TeamUiState {
        val matrix = if (weakToFire) mapOf("fire" to mapOf("squirtle" to 2.0)) else emptyMap()
        val offensiveMatrix = if (noCoverageGaps) {
            TypeIds.standardTypeNames.associateWith { mapOf("squirtle" to 2.0) }
        } else {
            emptyMap()
        }
        return TeamUiState(
            members = listOf(squirtle),
            matrixComputedFor = setOf("squirtle"),
            matrix = matrix,
            offensiveMatrix = offensiveMatrix
        ).withDerivedFields()
    }

    @Test
    fun `hasUnfixableSingleAxisIssue is true when only a shared weakness exists`() {
        val state = stateWith(weakToFire = true, noCoverageGaps = true)
        assertTrue(state.sharedWeaknesses.isNotEmpty())
        assertTrue(state.coverageGaps.isEmpty())
        assertTrue(state.hasUnfixableSingleAxisIssue)
    }

    @Test
    fun `hasUnfixableSingleAxisIssue is true when only a coverage gap exists`() {
        val state = stateWith(weakToFire = false, noCoverageGaps = false)
        assertTrue(state.sharedWeaknesses.isEmpty())
        assertTrue(state.coverageGaps.isNotEmpty())
        assertTrue(state.hasUnfixableSingleAxisIssue)
    }

    @Test
    fun `hasUnfixableSingleAxisIssue is false when both a weakness and a gap exist`() {
        val state = stateWith(weakToFire = true, noCoverageGaps = false)
        assertTrue(state.sharedWeaknesses.isNotEmpty())
        assertTrue(state.coverageGaps.isNotEmpty())
        assertFalse(state.hasUnfixableSingleAxisIssue)
    }

    @Test
    fun `hasUnfixableSingleAxisIssue is false when neither a weakness nor a gap exists`() {
        val state = stateWith(weakToFire = false, noCoverageGaps = true)
        assertTrue(state.sharedWeaknesses.isEmpty())
        assertTrue(state.coverageGaps.isEmpty())
        assertFalse(state.hasUnfixableSingleAxisIssue)
    }
}
