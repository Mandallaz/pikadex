package com.mandallaz.pikadex.ui.list

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.util.SortStat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * computeDisplayed is the filter/sort pipeline behind the Pokédex grid.
 *
 * The first two tests document, rather than newly prove, correct behaviour with no base stats
 * loaded: every key computes to Int.MIN_VALUE, and Kotlin's sortedBy/sortedByDescending are stable,
 * so the list was already left in its original order before computeDisplayed gained its explicit
 * `if (... && state.baseStats.isEmpty()) return@let` guard — that guard is genuinely redundant at
 * this function's output for that reason, confirmed by deliberately removing it and re-running
 * these tests (no failure). It stays because it matches the review's prescribed diff and documents
 * the invariant explicitly rather than leaning on sort-stability trivia.
 *
 * The behaviour that *did* change observably — loadBaseStatsIfNeeded clearing sortStat and setting
 * an explicit error message on a failed fetch, so the Sort chip stops claiming an unsatisfied sort —
 * lives in the ViewModel's coroutine code and isn't covered here: there's no fake PokedexRepository
 * or coroutines-test setup in this codebase yet, and building one is a larger change than this fix.
 * Verified instead by direct code review against the review's exact prescribed patch.
 */
class PokedexListViewModelTest {

    private fun resource(name: String, id: Int) = NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$id/")

    private val unsorted = listOf(resource("charmander", 4), resource("bulbasaur", 1), resource("squirtle", 7))

    @Test
    fun `a stat sort with no base stats loaded leaves the list untouched`() {
        val state = PokedexListUiState(allPokemon = unsorted, sortStat = SortStat.ATTACK, baseStats = emptyMap())
        assertEquals(unsorted, computeDisplayed(state, debouncedQuery = ""))
    }

    @Test
    fun `dex number sort works with no base stats loaded`() {
        val state = PokedexListUiState(
            allPokemon = unsorted,
            sortStat = SortStat.DEX_NUMBER,
            sortAscending = true,
            baseStats = emptyMap()
        )
        assertEquals(listOf("bulbasaur", "charmander", "squirtle"), computeDisplayed(state, "").map { it.name })
    }

    @Test
    fun `a stat sort applies once the matching base stats are present`() {
        val stats = mapOf(
            "charmander" to mapOf("attack" to 52),
            "bulbasaur" to mapOf("attack" to 49),
            "squirtle" to mapOf("attack" to 48)
        )
        val state = PokedexListUiState(
            allPokemon = unsorted,
            sortStat = SortStat.ATTACK,
            sortAscending = false,
            baseStats = stats
        )
        assertEquals(listOf("charmander", "bulbasaur", "squirtle"), computeDisplayed(state, "").map { it.name })
    }

    // A resource whose name is missing from the bulk stats map (a partial/stale fetch) sorts as
    // Int.MIN_VALUE rather than crashing on a missing key — this is deliberately still lenient
    // per-entry; only "no stats at all" refuses to sort.
    @Test
    fun `an entry missing from a present base stats map sorts to the bottom rather than crashing`() {
        val stats = mapOf("bulbasaur" to mapOf("attack" to 49))
        val state = PokedexListUiState(allPokemon = unsorted, sortStat = SortStat.ATTACK, baseStats = stats)
        assertEquals("bulbasaur", computeDisplayed(state, "").first().name)
    }

    // A resource whose url doesn't end in a numeric segment has no id (NamedApiResource.id is
    // null) and can't render a card — filtered out here rather than the grid skipping it one item
    // at a time on every recomposition.
    @Test
    fun `resources with no numeric id are filtered out`() {
        val idless = NamedApiResource("weird-form", "https://pokeapi.co/api/v2/pokemon/not-a-number/")
        val state = PokedexListUiState(allPokemon = unsorted + idless)
        assertEquals(unsorted, computeDisplayed(state, ""))
    }
}
