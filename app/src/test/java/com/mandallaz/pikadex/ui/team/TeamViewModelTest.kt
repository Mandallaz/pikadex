package com.mandallaz.pikadex.ui.team

import com.mandallaz.pikadex.data.LocalizedNames
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.clearForTest
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.dto.DamageRelations
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import com.mandallaz.pikadex.data.repository.fakeTypeDetailDto
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.clearForTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F49 — load/cancel/error coverage for [TeamViewModel.computeMatrix], which reacts to
 * [TeamRepository.team] changes rather than being called directly — every test here drives it via
 * [TeamRepository.replaceAll], the same entry point [TeamViewModel.loadPreset] uses.
 */
class TeamViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePokedexRepository
    private lateinit var viewModel: TeamViewModel

    private val squirtle = NamedApiResource("squirtle", "https://pokeapi.co/api/v2/pokemon/7/")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // B35 — reset before, not just after: guards against a *different* test class touching
        // this JVM-wide singleton and forgetting its own cleanup, which is exactly what let this
        // bug regress once already (see the issue) despite this class's own @After already doing it.
        LocalizedNames.clearForTest()
        repository = FakePokedexRepository()
        repository.pokemonTypes = listOf("water")
        repository.typeDetailByName = mapOf("water" to fakeTypeDetailDto("water"))
        repository.pokemonLevelUpMoveNames = emptyList()
        repository.allMoveInfo = emptyMap()
        viewModel = TeamViewModel(repository, dispatcher)
    }

    @After
    fun tearDown() {
        // Order matters: clearing the ViewModel first cancels its TeamRepository.team collector,
        // so the reset below doesn't try to wake a dead coroutine on an already-reset dispatcher
        // (see clearForTest's doc) or leak that collector into a later, unrelated test.
        viewModel.clearForTest()
        TeamRepository.replaceAll(emptyList())
        // B35 — LocalizedNames is a JVM-wide singleton whose cache, once warmed by this test's
        // own `species names load into state...` test, would otherwise stay stale for every other
        // test class sharing this worker.
        LocalizedNames.clearForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun `adding a team member computes the matrix`() = runTest(dispatcher) {
        TeamRepository.replaceAll(listOf(squirtle))
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(setOf("squirtle"), state.matrixComputedFor)
        assertFalse(state.isMatrixStale)
    }

    @Test
    fun `emptying the team clears the matrix instead of erroring`() = runTest(dispatcher) {
        TeamRepository.replaceAll(listOf(squirtle))
        dispatcher.scheduler.advanceUntilIdle()

        TeamRepository.replaceAll(emptyList())
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(emptyList<NamedApiResource>(), state.members)
        assertEquals(emptyMap<String, Map<String, Double>>(), state.matrix)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a failed matrix fetch clears the spinner and sets an error, keeping matrixComputedFor stale`() = runTest(dispatcher) {
        repository.failWith = RuntimeException("boom")

        TeamRepository.replaceAll(listOf(squirtle))
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        // matrixComputedFor deliberately doesn't include "squirtle" — the fetch never succeeded
        // for it, so isMatrixStale must keep reporting the matrix as not-yet-fresh for this team.
        assertTrue(state.isMatrixStale)
    }

    // retry() is the only way back after an offline matrix failure — without it, only the team
    // itself changing again would ever re-trigger a fetch.
    @Test
    fun `retry recovers after a failed matrix fetch`() = runTest(dispatcher) {
        repository.failWith = RuntimeException("boom")
        TeamRepository.replaceAll(listOf(squirtle))
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        repository.failWith = null
        viewModel.retry()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertFalse(state.isMatrixStale)
    }

    // Two team changes in quick succession must leave the matrix reflecting only the *second*
    // (current) team — the first fetch's stale result landing afterward would otherwise show a
    // matrix that doesn't match what's actually on the roster.
    @Test
    fun `a team change mid-fetch cancels the stale computation`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        TeamRepository.replaceAll(listOf(squirtle))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        repository.gate = null
        val charmander = NamedApiResource("charmander", "https://pokeapi.co/api/v2/pokemon/4/")
        TeamRepository.replaceAll(listOf(charmander))
        dispatcher.scheduler.advanceUntilIdle()
        gate.complete(Unit) // release the stale, already-cancelled fetch late
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(setOf("charmander"), state.matrixComputedFor)
    }

    // B9 — the Team screen (roster chips, suggestion tiles) fell back to the raw English name
    // forever, even once F35's language picker was set to French, since TeamViewModel never fetched
    // the bulk species-name map PokedexListViewModel already had (issue #52). The fake must be
    // primed and a fresh ViewModel constructed here (not the shared one from setUp()) — this
    // ViewModel's viewModelScope runs on Dispatchers.Main.immediate, so its init-time fetch executes
    // eagerly at construction rather than waiting for advanceUntilIdle().
    @Test
    fun `species names load into state for the Team screen to localize with`() = runTest(dispatcher) {
        repository.allSpeciesNames = mapOf("squirtle" to mapOf("fr" to "Carapuce"))
        val freshViewModel = TeamViewModel(repository, dispatcher)

        TeamRepository.replaceAll(listOf(squirtle))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("fr" to "Carapuce"), freshViewModel.uiState.value.speciesNames["squirtle"])
        freshViewModel.clearForTest()
    }

    @Test
    fun `loadSuggestions computes and updates suggestions state on success`() = runTest(dispatcher) {
        val pikachu = NamedApiResource("pikachu", "https://pokeapi.co/api/v2/pokemon/25/")
        val charmander = NamedApiResource("charmander", "https://pokeapi.co/api/v2/pokemon/4/")

        repository.masterList = listOf(squirtle, pikachu, charmander)
        val stats = mapOf(
            "hp" to 100,
            "attack" to 100,
            "defense" to 100,
            "special-attack" to 100,
            "special-defense" to 100,
            "speed" to 100
        )
        repository.allBasics = mapOf(
            "squirtle" to PokeApiGraphQLDataSource.PokemonBasics(stats, listOf("water"), false, false),
            "pikachu" to PokeApiGraphQLDataSource.PokemonBasics(stats, listOf("electric"), false, false),
            "charmander" to PokeApiGraphQLDataSource.PokemonBasics(stats, listOf("fire"), false, false)
        )
        val allTypeDetails = TypeIds.standardTypeNames.associateWith { name ->
            when (name) {
                "electric" -> TypeDetailDto(
                    id = 1,
                    name = "electric",
                    damageRelations = DamageRelations(
                        doubleDamageFrom = null,
                        doubleDamageTo = listOf(NamedApiResource("water", "")),
                        halfDamageFrom = null,
                        halfDamageTo = null,
                        noDamageFrom = null,
                        noDamageTo = null
                    ),
                    pokemon = null
                )
                "fire" -> TypeDetailDto(
                    id = 1,
                    name = "fire",
                    damageRelations = DamageRelations(
                        doubleDamageFrom = null,
                        doubleDamageTo = listOf(NamedApiResource("grass", "")),
                        halfDamageFrom = null,
                        halfDamageTo = null,
                        noDamageFrom = null,
                        noDamageTo = null
                    ),
                    pokemon = null
                )
                else -> fakeTypeDetailDto(name)
            }
        }
        repository.typeDetailByName = allTypeDetails + repository.typeDetailByName

        TeamRepository.replaceAll(listOf(squirtle))
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSuggestionsLoading)
        assertTrue(state.suggestions.isNotEmpty())
        assertEquals(mapOf("pikachu" to 25, "charmander" to 4), state.suggestionSpriteIds)
    }
}
