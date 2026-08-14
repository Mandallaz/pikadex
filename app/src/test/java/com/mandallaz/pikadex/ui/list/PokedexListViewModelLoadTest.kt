package com.mandallaz.pikadex.ui.list

import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.LocalizedNames
import com.mandallaz.pikadex.data.clearForTest
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
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
 * F49 — load/cancel/error coverage for [PokedexListViewModel]'s coroutine-backed loading and
 * filter jobs, which [PokedexListViewModelTest] (the existing `computeDisplayed` pure-function
 * suite) deliberately doesn't reach — see that file's own doc.
 */
class PokedexListViewModelLoadTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePokedexRepository
    private lateinit var viewModel: PokedexListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // B35 — reset before, not just after: guards against a *different* test class touching
        // this JVM-wide singleton and forgetting its own cleanup — this class was itself the one
        // missed by the original B35 fix, which is exactly the failure mode this guards against.
        LocalizedNames.clearForTest()
        repository = FakePokedexRepository()
        // Constructed here, before each test sets its own repository fields — loadInitialData()
        // fires from init{} but its coroutine body doesn't actually run until the test advances
        // the (Standard, not immediate) test dispatcher, so fields set afterward are still picked
        // up in time.
        viewModel = PokedexListViewModel(repository)
    }

    @After
    fun tearDown() {
        viewModel.clearForTest()
        // B35 — LocalizedNames is a JVM-wide singleton this ViewModel's init{} warms via
        // loadSpeciesNamesIfNeeded; every test class that touches it must reset it or the next
        // test class sharing this worker inherits stale/wrong data (this class was the one
        // missed by the original B35 fix, causing it to regress).
        LocalizedNames.clearForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load populates allPokemon and typeOptions`() = runTest(dispatcher) {
        val bulbasaur = NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/")
        repository.masterList = listOf(bulbasaur)
        repository.types = listOf(NamedApiResource("grass", "https://pokeapi.co/api/v2/type/12/"))

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(bulbasaur), state.allPokemon)
        assertEquals(1, state.typeOptions.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun `initial load failure sets an error message and clears the spinner`() = runTest(dispatcher) {
        repository.failWith = RuntimeException("boom")

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
    }

    // B13 — errorMessage used to be a raw English String built inside the ViewModel, which never
    // went through stringResource() and so stayed English regardless of the app's picked language.
    // Asserting the exact resource id (not just non-null) is what actually catches a regression
    // back to a hardcoded literal, since a plain String would no longer compile against this field.
    @Test
    fun `initial load failure carries the localizable error resource, not a hardcoded string`() = runTest(dispatcher) {
        repository.failWith = RuntimeException("boom")

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.list_error_load_pokedex, viewModel.uiState.value.errorMessage?.resId)
    }

    // The bug this guards against: a cold start with no connection used to be a dead end with no
    // way back short of force-killing the app — retryInitialLoad exists specifically to fix that.
    @Test
    fun `retryInitialLoad recovers after a failed initial load`() = runTest(dispatcher) {
        repository.failWith = RuntimeException("boom")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        repository.failWith = null
        repository.masterList = listOf(NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"))
        viewModel.retryInitialLoad()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertEquals(1, state.allPokemon.size)
    }

    // B34 — loadInitialData was the one coroutine body in this file that didn't rethrow
    // CancellationException before its generic catch, so a cancellation (e.g. this ViewModel
    // being cleared mid-load) used to be caught as a normal Exception and turned into a bogus
    // "couldn't load the Pokédex" error. clearForTest() cancels viewModelScope the same way a real
    // ViewModelStore does when the screen goes away.
    @Test
    fun `clearing the ViewModel mid-load does not surface a load error`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.clearForTest()
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    // F82 — typesByName (which feeds the dex list's type badges) used to only load once the user
    // opened the filter sheet or sort dialog (loadBaseStatsIfNeeded's only two callers); it must
    // now also be populated eagerly from init, with no user action, so cards show their type
    // badges on first entry to the screen.
    @Test
    fun `initial load populates typesByName without any filter or sort action`() = runTest(dispatcher) {
        // A fresh ViewModel, not the shared one from setUp(): the fake repository has no real
        // suspension point (no gate set), so viewModelScope's Dispatchers.Main.immediate runs
        // init{}'s coroutines synchronously at construction time rather than deferring them to
        // advanceUntilIdle() below — allBasics must already be set before construction for this
        // eager path to see it, unlike the master-list/type-options load (which genuinely
        // suspends via async{}.await() and so does pick up state set after construction).
        repository.allBasics = mapOf(
            "bulbasaur" to PokeApiGraphQLDataSource.PokemonBasics(
                stats = emptyMap(),
                types = listOf("grass", "poison"),
                isLegendary = false,
                isMythical = false
            )
        )
        val freshViewModel = PokedexListViewModel(repository)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("grass", "poison"), freshViewModel.uiState.value.typesByName["bulbasaur"])
    }

    @Test
    fun `selecting a type filter applies the fetched intersection`() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle() // let the initial load finish first
        repository.pokemonNamesForType = setOf("charmander", "charizard")

        viewModel.onTypeToggled("fire")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf("charmander", "charizard"), state.typeFilterNames)
        assertFalse(state.isFilterLoading)
    }

    @Test
    fun `a failed type filter fetch surfaces an error and clears the spinner`() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle()
        repository.failWith = RuntimeException("boom")

        viewModel.onTypeToggled("fire")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isFilterLoading)
        assertNotNull(state.errorMessage)
    }

    // Regression coverage for the "rapid second tap" comment on onTypeToggled: a type selection
    // changed again before the first request resolves must cancel it, not let its stale result
    // land after the second (current) one's.
    @Test
    fun `switching the type selection mid-flight cancels the stale fetch`() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        repository.pokemonNamesForType = setOf("STALE_RESULT_MUST_NOT_APPLY")

        viewModel.onTypeToggled("fire")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFilterLoading)

        repository.gate = null
        repository.pokemonNamesForType = setOf("charmander", "charizard")
        viewModel.onTypeToggled("flying") // selectedTypes becomes {fire, flying}; cancels + restarts the job
        dispatcher.scheduler.advanceUntilIdle()
        gate.complete(Unit) // release the stale, already-cancelled job late — must have no effect
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf("charmander", "charizard"), state.typeFilterNames)
        assertFalse(state.isFilterLoading)
    }

    // issue #70 (B20) — applyTierFilter built formatFilterNames from allPokemon without the
    // "empty means not loaded yet" guard that the rarity/counter/stat-minimum filters already have
    // in computeDisplayed, so an empty master list (still loading, or failed silently) resolved the
    // tier filter to an empty set and emptied the whole grid behind a confidently-checked chip.
    @Test
    fun `selecting a tier filter before the master list has loaded leaves the filter unapplied`() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle() // initial load completes with allPokemon still empty
        assertTrue(viewModel.uiState.value.allPokemon.isEmpty())
        repository.smogonTiers = mapOf("charizard" to "OU")

        viewModel.onFormatTierSelected("OU")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.formatFilterNames)
        assertFalse(state.isFilterLoading)
    }

    // Deselecting the last active type while its own request is still in flight has nowhere else
    // to clear isFilterLoading from (the cancelled job's own `finally`-equivalent never runs) —
    // this must happen at the cancel site itself, or the spinner is stuck forever.
    @Test
    fun `deselecting the last type while its fetch is in flight clears the spinner`() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        viewModel.onTypeToggled("fire")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFilterLoading)

        viewModel.onTypeToggled("fire") // deselect — cancels the pending job, no new one starts
        dispatcher.scheduler.advanceUntilIdle()
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isFilterLoading)
        assertNull(state.typeFilterNames)
    }

    // issue #133 — scope displayedPokemon combine to only the list-affecting state fields
    // so unrelated state changes do not trigger a full computeDisplayed recompute.
    @Test
    fun `updating unrelated state fields does not trigger computeDisplayed recompute`() = runTest(dispatcher) {
        val bulbasaur = NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/")
        repository.masterList = listOf(bulbasaur)
        repository.types = listOf(NamedApiResource("grass", "https://pokeapi.co/api/v2/type/12/"))

        dispatcher.scheduler.advanceUntilIdle()

        // Wait for background computeDisplayed on Dispatchers.Default to complete the initial load
        while (viewModel.displayedPokemon.value.isEmpty()) {
            Thread.sleep(10)
        }

        // Capture initial computeDisplayedCount
        val initialCount = viewModel.computeDisplayedCount

        // Cause a failure in loadMoveOptionsIfNeeded to trigger a state update for errorMessage,
        // which is unrelated to the displayedPokemon calculation.
        repository.failWith = RuntimeException("boom")
        viewModel.loadMoveOptionsIfNeeded()
        dispatcher.scheduler.advanceUntilIdle()

        // Verify errorMessage was actually populated in the state
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Dismiss the error, triggering another state update to errorMessage
        viewModel.dismissError()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)

        // Assert that computeDisplayedCount did not increase after the initial load count
        assertEquals(initialCount, viewModel.computeDisplayedCount)
    }
}
