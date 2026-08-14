package com.mandallaz.pikadex.ui.list

import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.ui.UiText
import com.mandallaz.pikadex.util.RarityFilter
import com.mandallaz.pikadex.util.SortStat
import com.mandallaz.pikadex.util.TOTAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    // --- Perfect Counter filter (F33) -----------------------------------

    @Test
    fun `counter filter keeps only Pokemon whose typing counters a triangle`() {
        val state = PokedexListUiState(
            allPokemon = unsorted,
            counterFilterActive = true,
            typesByName = mapOf(
                "charmander" to listOf("fire", "flying"), // counters Fire/Grass/Ground
                "bulbasaur" to listOf("grass", "poison"), // no triangle counter
                "squirtle" to listOf("water")
            )
        )
        assertEquals(listOf("charmander"), computeDisplayed(state, "").map { it.name })
    }

    // typesByName comes from the same bulk fetch as legendaryNames/mythicalNames/baseStats — empty
    // means "not loaded yet", not "nothing counters a triangle", so the filter must not apply.
    @Test
    fun `a counter filter with no type data loaded yet leaves the list untouched`() {
        val state = PokedexListUiState(allPokemon = unsorted, counterFilterActive = true)
        assertEquals(unsorted, computeDisplayed(state, ""))
    }

    // An entry missing from an otherwise-loaded typesByName map (partial/stale fetch) is excluded
    // rather than assumed to pass — same reasoning as the identical statMinimums guard below.
    @Test
    fun `an entry missing from a present typesByName map is excluded, not assumed to pass`() {
        val state = PokedexListUiState(
            allPokemon = unsorted,
            counterFilterActive = true,
            typesByName = mapOf("charmander" to listOf("fire", "flying"))
        )
        assertEquals(listOf("charmander"), computeDisplayed(state, "").map { it.name })
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
    // statBaseline, proving TOTAL is read as a derived sum rather than a literal lookup
    // into a "total" entry that doesn't exist in the map.
    @Test
    fun `a total minimum keeps only entries whose summed stats meet or exceed it`() {
        val state = PokedexListUiState(allPokemon = unsorted, baseStats = statBaseline, statMinimums = mapOf(TOTAL to 95))
        assertEquals(listOf("charmander"), computeDisplayed(state, "").map { it.name })
    }

    @Test
    fun `a total minimum combines with a per-stat minimum as AND`() {
        // bulbasaur clears total>=90 but not attack>=50; only charmander clears both.
        val state = PokedexListUiState(
            allPokemon = unsorted,
            baseStats = statBaseline,
            statMinimums = mapOf(TOTAL to 90, "attack" to 50)
        )
        assertEquals(listOf("charmander"), computeDisplayed(state, "").map { it.name })
    }

    // --- Search matches the localized name, not the raw English one (B10) -----

    private val mudbray = resource("mudbray", 749)
    private val mudbraySpeciesNames = mapOf(
        "mudbray" to mapOf("en" to "Mudbray", "fr" to "Tiboudet", "de" to "Pampuli")
    )

    @Test
    fun `search matches the localized name in the current language`() {
        val state = PokedexListUiState(allPokemon = listOf(mudbray), speciesNames = mudbraySpeciesNames)
        assertEquals(listOf("mudbray"), computeDisplayed(state, "tiboudet", language = "fr").map { it.name })
    }

    // The bug itself (first B10 report): searching "ray" in French mode found Mudbray only
    // because its *English* name contains "ray" — its French name "Tiboudet" doesn't.
    //
    // A first fix matched *both* the localized and the English name, which turned out to be the
    // same bug from a different angle (second B10 report): "ray" in German still found Mudbray,
    // whose German name "Pampuli" has nothing to do with "ray" either. Once a species has a
    // language-specific name, English must not be a second always-on match path — only what's
    // actually on screen should be searchable.
    @Test
    fun `search does not match the English name when a different localized name exists`() {
        val state = PokedexListUiState(allPokemon = listOf(mudbray), speciesNames = mudbraySpeciesNames)
        assertEquals(emptyList<NamedApiResource>(), computeDisplayed(state, "ray", language = "fr"))
        assertEquals(emptyList<NamedApiResource>(), computeDisplayed(state, "ray", language = "de"))
    }

    // A localized name absent from speciesNames (not loaded yet, or no translation for this
    // species) must not crash the search — falls back to the English-formatted name via
    // localizedDisplayName, and *that* fallback is the one case English is still searchable in a
    // non-English language.
    @Test
    fun `search falls back to the English name when no localized name is available for this species`() {
        val state = PokedexListUiState(allPokemon = listOf(mudbray), speciesNames = emptyMap())
        assertEquals(listOf("mudbray"), computeDisplayed(state, "mudbray", language = "fr").map { it.name })
        assertEquals(listOf("mudbray"), computeDisplayed(state, "ray", language = "fr").map { it.name })
    }

    // English itself must still match the raw API name exactly as before B10 (not routed through
    // toDisplayName()'s punctuation, which normalizedQuery doesn't strip) — e.g. "mrmime" must
    // still find "mr-mime" the same way it did pre-B10, since toDisplayName() would format it as
    // "Mr. Mime" and the "." isn't stripped by the query-side normalization.
    @Test
    fun `English search still matches the raw API name, not the formatted display name`() {
        val mrMime = resource("mr-mime", 122)
        val state = PokedexListUiState(allPokemon = listOf(mrMime), speciesNames = mudbraySpeciesNames)
        assertEquals(listOf("mr-mime"), computeDisplayed(state, "mrmime", language = "en").map { it.name })
    }

    // --- toListAffectingState (issue #133) -----------------------------------------------------
    //
    // displayedPokemon's combine() is scoped to toListAffectingState() so that fields unrelated to
    // the displayed list (isLoading, errorMessage, isFilterLoading, isStatsLoading, and the various
    // *Options lists, which are UI-facing only) don't trigger a full recompute via
    // distinctUntilChanged(). Proven here as a plain equality check on the pure function itself,
    // rather than by counting recomputes through the real coroutine pipeline — that approach (an
    // earlier version of this test, plus a computeDisplayedCount instrumentation var in
    // PokedexListViewModel) was flaky: displayedPokemon's upstream flowOn(Dispatchers.Default) runs
    // on a real thread, not the test's virtual scheduler, so nothing could deterministically wait
    // for it before asserting.

    @Test
    fun `toListAffectingState is unchanged when only unrelated fields differ`() {
        val base = PokedexListUiState(allPokemon = listOf(resource("bulbasaur", 1)))
        val withUnrelatedChanges = base.copy(
            isLoading = true,
            errorMessage = UiText(R.string.list_error_load_pokedex),
            searchQuery = "char",
            isFilterLoading = true,
            isStatsLoading = true
        )

        assertEquals(base.toListAffectingState(), withUnrelatedChanges.toListAffectingState())
    }

    @Test
    fun `toListAffectingState changes when a list-affecting field changes`() {
        val base = PokedexListUiState(allPokemon = listOf(resource("bulbasaur", 1)))
        val withDifferentFilter = base.copy(rarityFilter = RarityFilter.LEGENDARY)

        assertNotEquals(base.toListAffectingState(), withDifferentFilter.toListAffectingState())
    }
}
