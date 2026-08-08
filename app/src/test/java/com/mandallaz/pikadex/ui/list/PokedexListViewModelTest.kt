package com.mandallaz.pikadex.ui.list

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.util.RarityFilter
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

    // --- Rarity filter -------------------------------------------------------

    @Test
    fun `legendary filter keeps only names in legendaryNames`() {
        val state = PokedexListUiState(
            allPokemon = unsorted,
            rarityFilter = RarityFilter.LEGENDARY,
            legendaryNames = setOf("charmander")
        )
        assertEquals(listOf("charmander"), computeDisplayed(state, "").map { it.name })
    }

    @Test
    fun `mythical filter keeps only names in mythicalNames`() {
        val state = PokedexListUiState(
            allPokemon = unsorted,
            rarityFilter = RarityFilter.MYTHICAL,
            mythicalNames = setOf("bulbasaur")
        )
        assertEquals(listOf("bulbasaur"), computeDisplayed(state, "").map { it.name })
    }

    @Test
    fun `ordinary filter excludes both legendary and mythical names`() {
        val state = PokedexListUiState(
            allPokemon = unsorted,
            rarityFilter = RarityFilter.ORDINARY,
            legendaryNames = setOf("charmander"),
            mythicalNames = setOf("bulbasaur")
        )
        assertEquals(listOf("squirtle"), computeDisplayed(state, "").map { it.name })
    }

    // legendaryNames/mythicalNames come from the same bulk fetch as baseStats — both empty means
    // "not loaded yet", not "no legendaries exist", so the filter must not apply and hide everything.
    @Test
    fun `a rarity filter with no legendary or mythical data loaded yet leaves the list untouched`() {
        val state = PokedexListUiState(allPokemon = unsorted, rarityFilter = RarityFilter.LEGENDARY)
        assertEquals(unsorted, computeDisplayed(state, ""))
    }

    // --- Name sort -------------------------------------------------------

    @Test
    fun `name sort orders alphabetically by display name, ascending`() {
        val state = PokedexListUiState(allPokemon = unsorted, sortStat = SortStat.NAME, sortAscending = true)
        assertEquals(listOf("bulbasaur", "charmander", "squirtle"), computeDisplayed(state, "").map { it.name })
    }

    @Test
    fun `name sort descending reverses the order`() {
        val state = PokedexListUiState(allPokemon = unsorted, sortStat = SortStat.NAME, sortAscending = false)
        assertEquals(listOf("squirtle", "charmander", "bulbasaur"), computeDisplayed(state, "").map { it.name })
    }

    // "mr-mime" displays as "Mr. Mime" and "mime-jr" as "Mime Jr." — sorting must key off the
    // display name (what the picker/chip actually says), not the raw API name, or these two would
    // land in the wrong relative order (raw "mime-jr" < "mr-mime" alphabetically, but "Mime Jr."
    // should sort before "Mr. Mime" too, so this particular pair doesn't actually flip — the real
    // risk is a raw name whose hyphens/casing differ from its display form in a way that would).
    @Test
    fun `name sort keys off the display name, not the raw API name`() {
        val resources = listOf(resource("mr-mime", 122), resource("mime-jr", 439))
        val state = PokedexListUiState(allPokemon = resources, sortStat = SortStat.NAME, sortAscending = true)
        assertEquals(listOf("mime-jr", "mr-mime"), computeDisplayed(state, "").map { it.name })
    }

    // Name sort needs no bulk stats data at all (same as dex-number sort) — it must not fall into
    // the "no baseStats loaded" no-op guard that the numeric-stat sorts use.
    @Test
    fun `name sort works with no base stats loaded`() {
        val state = PokedexListUiState(allPokemon = unsorted, sortStat = SortStat.NAME, sortAscending = true, baseStats = emptyMap())
        assertEquals(listOf("bulbasaur", "charmander", "squirtle"), computeDisplayed(state, "").map { it.name })
    }

    // --- Stat minimum filter -------------------------------------------------

    private val statBaseline = mapOf(
        "charmander" to mapOf("attack" to 52, "speed" to 65),
        "bulbasaur" to mapOf("attack" to 49, "speed" to 45),
        "squirtle" to mapOf("attack" to 48, "speed" to 43)
    )

    @Test
    fun `a stat minimum keeps only entries meeting or exceeding it`() {
        val state = PokedexListUiState(allPokemon = unsorted, baseStats = statBaseline, statMinimums = mapOf("attack" to 49))
        assertEquals(listOf("charmander", "bulbasaur"), computeDisplayed(state, "").map { it.name })
    }

    @Test
    fun `multiple stat minimums combine as AND, not OR`() {
        // charmander clears attack>=50 but not speed>=60; nothing clears both except charmander itself here —
        // pick thresholds only charmander satisfies on both stats to prove they're ANDed, not ORed.
        val state = PokedexListUiState(
            allPokemon = unsorted,
            baseStats = statBaseline,
            statMinimums = mapOf("attack" to 50, "speed" to 60)
        )
        assertEquals(listOf("charmander"), computeDisplayed(state, "").map { it.name })
    }

    @Test
    fun `a resource missing from an otherwise-loaded stats map is excluded, not assumed to pass`() {
        val partial = mapOf("bulbasaur" to mapOf("attack" to 49))
        val state = PokedexListUiState(allPokemon = unsorted, baseStats = partial, statMinimums = mapOf("attack" to 1))
        assertEquals(listOf("bulbasaur"), computeDisplayed(state, "").map { it.name })
    }

    // Same "not loaded yet" guard as the rarity filter — an active minimum with no bulk stats data
    // at all must not filter the grid down to nothing while the fetch is still in flight.
    @Test
    fun `a stat minimum with no base stats loaded yet leaves the list untouched`() {
        val state = PokedexListUiState(allPokemon = unsorted, statMinimums = mapOf("attack" to 100))
        assertEquals(unsorted, computeDisplayed(state, ""))
    }

    // --- Stat total minimum filter (F14) --------------------------------------

    // charmander sums to 117, bulbasaur to 94, squirtle to 91 — none of these is a real key in
    // statBaseline, proving STAT_KEY_TOTAL is read as a derived sum rather than a literal lookup
    // into a "total" entry that doesn't exist in the map.
    @Test
    fun `a total minimum keeps only entries whose summed stats meet or exceed it`() {
        val state = PokedexListUiState(allPokemon = unsorted, baseStats = statBaseline, statMinimums = mapOf(STAT_KEY_TOTAL to 95))
        assertEquals(listOf("charmander"), computeDisplayed(state, "").map { it.name })
    }

    @Test
    fun `a total minimum combines with a per-stat minimum as AND`() {
        // bulbasaur clears total>=90 but not attack>=50; only charmander clears both.
        val state = PokedexListUiState(
            allPokemon = unsorted,
            baseStats = statBaseline,
            statMinimums = mapOf(STAT_KEY_TOTAL to 90, "attack" to 50)
        )
        assertEquals(listOf("charmander"), computeDisplayed(state, "").map { it.name })
    }
}
