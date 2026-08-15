package com.mandallaz.pikadex.ui.list

import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.LocalizedNames
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
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
        viewModel = PokedexListViewModel(repository, dispatcher)
    }

    @After
    fun tearDown() {
        viewModel.clearForTest(dispatcher.scheduler)
        // B51 — displayedPokemon's combine{}.flowOn(Dispatchers.Default) runs real work on a
        // background thread; without advancing the scheduler here, clearForTest()'s cancellation
        // can still be mid-cleanup when the next test class starts, surfacing as an uncaught
        // exception attributed to whatever runs next (same root cause/fix as B50).
        dispatcher.scheduler.advanceUntilIdle()
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

    // B54 — selecting a tier while allPokemon is still empty used to leave selectedFormatTier
    // set but formatFilterNames null, with no re-trigger once loadInitialData finished: the UI
    // showed an active "Tier: OU" chip over a completely unfiltered grid.
    @Test
    fun `selecting a format tier before allPokemon finishes loading applies once the load completes`() = runTest(dispatcher) {
        val bulbasaur = NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/")
        val charmander = NamedApiResource("charmander", "https://pokeapi.co/api/v2/pokemon/4/")
        repository.masterList = listOf(bulbasaur, charmander)
        repository.types = emptyList()
        repository.smogonTiers = mapOf("bulbasaur" to "OU", "charmander" to "UU")

        val masterListGate = CompletableDeferred<Unit>()
        repository.masterListGate = masterListGate

        viewModel.onFormatTierSelected("OU")
        dispatcher.scheduler.advanceUntilIdle()

        // allPokemon is still empty (master list load held open) — the tier stays selected but
        // nothing can be filtered yet.
        assertEquals("OU", viewModel.uiState.value.selectedFormatTier)
        assertNull(viewModel.uiState.value.formatFilterNames)
        assertTrue(viewModel.uiState.value.allPokemon.isEmpty())

        masterListGate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("OU", state.selectedFormatTier)
        assertEquals(listOf("bulbasaur"), state.formatFilterNames?.toList())
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

        viewModel.clearForTest(dispatcher.scheduler)
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
        val freshViewModel = PokedexListViewModel(repository, dispatcher)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("grass", "poison"), freshViewModel.uiState.value.typesByName["bulbasaur"])

        // B53 — this ViewModel isn't the one setUp()/tearDown() tracks, so its own displayedPokemon
        // collector (flowOn(Dispatchers.Default)) leaks past this test unless cleared here too.
        freshViewModel.clearForTest(dispatcher.scheduler)
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

    private class TestTrackingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var blocksDispatched = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            blocksDispatched++
            delegate.dispatch(context, block)
        }
    }

    @Test
    fun `fetchAndApplyBasics post processing executes on the defaultDispatcher`() = runTest(dispatcher) {
        val trackingDispatcher = TestTrackingDispatcher(dispatcher)
        repository.allBasics = mapOf(
            "bulbasaur" to PokeApiGraphQLDataSource.PokemonBasics(
                stats = emptyMap(),
                types = listOf("grass", "poison"),
                isLegendary = false,
                isMythical = false
            )
        )
        repository.masterList = listOf(NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"))

        val testViewModel = PokedexListViewModel(repository, trackingDispatcher)

        val initialDispatches = trackingDispatcher.blocksDispatched

        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "Expected fetchAndApplyBasics to dispatch to defaultDispatcher",
            trackingDispatcher.blocksDispatched > initialDispatches
        )
        assertEquals(listOf("grass", "poison"), testViewModel.uiState.value.typesByName["bulbasaur"])

        // B53 — same as freshViewModel above: this instance isn't tracked by tearDown(), so clear
        // it explicitly or its displayedPokemon collector leaks into later tests.
        testViewModel.clearForTest(dispatcher.scheduler)
    }

    // A sibling test for the same defaultDispatcher-injection pattern (PokedexListContext.update's
    // name mapping) was removed here: displayedPokemon's upstream combine runs via
    // .flowOn(Dispatchers.Default) — a real thread pool, not this test's virtual scheduler — so
    // advanceUntilIdle() can't deterministically wait for it before asserting. That's the same
    // real-thread/virtual-time race B42 (issue #111) already fixed elsewhere; it passed locally but
    // lost the race in CI. `fetchAndApplyBasics post processing executes on the defaultDispatcher`
    // above already proves the injected-dispatcher pattern deterministically, so this redundant,
    // flaky duplicate was dropped rather than risk reintroducing that class of bug.

    // issue #133's own regression test (`updating unrelated state fields does not trigger
    // computeDisplayed recompute`, and the computeDisplayedCount var it read) had the identical
    // problem: the counter was written from the real Dispatchers.Default thread displayedPokemon's
    // combine runs on, and read from the test thread with no synchronization — a timing/visibility
    // race, not a deterministic assertion. Replaced with a plain unit test of
    // PokedexListUiState.toListAffectingState() below, in PokedexListViewModelTest.kt, which proves
    // the same guarantee (unrelated field changes don't change the scoped state distinctUntilChanged
    // keys on) without touching the coroutine pipeline at all.

}
