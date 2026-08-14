package com.mandallaz.pikadex.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * Cancels [this] ViewModel's `viewModelScope`, same as what `ViewModelStore` does when a real
 * screen goes away. Without this, a ViewModel built directly (as every ViewModel test in this repo
 * does, bypassing the ViewModelStore entirely) leaves its `init{}`-launched collectors — e.g.
 * [com.mandallaz.pikadex.ui.team.TeamViewModel]'s subscription to
 * [com.mandallaz.pikadex.data.TeamRepository.team] — permanently subscribed to their singleton
 * `object`s for the rest of the JVM test run, so a later, unrelated test mutating that singleton
 * would still wake up this long-dead ViewModel's coroutines. `clear$lifecycle_viewmodel` is
 * `internal` in Kotlin (name-mangled, not `private`) specifically so tests in other modules can
 * still reach it via reflection the way this does — there's no other public API to stop a
 * ViewModel outside of an actual `ViewModelStore`.
 *
 * B51 — cancelling isn't enough on its own: a `StateFlow` built with `flowOn(Dispatchers.Default)`
 * (e.g. [com.mandallaz.pikadex.ui.list.PokedexListViewModel]'s `displayedPokemon`) keeps running on
 * a real background thread until it actually observes the cancellation, uncoupled from any test
 * dispatcher's virtual clock (same root cause as B50, but B50's `cancelAndJoin()` fix was local to
 * one ViewModel's own jobs). A plain blocking `job.join()` doesn't work either though: most
 * ViewModels' pending work is scheduled on the caller's `StandardTestDispatcher` (via
 * `Dispatchers.setMain`), not a real thread — nothing pumps that dispatcher's queue while blocked
 * inside `runBlocking`, so the join would hang forever on a job that's virtual-time-scheduled, not
 * actually stuck. Passing the caller's [scheduler] lets this poll-and-pump both kinds of pending
 * work at once: `advanceUntilIdle()` drains virtual-time work each iteration, and the real
 * `Thread.sleep` between iterations gives real background threads (the B51 case) genuine wall-clock
 * time to finish. Bounded by a timeout so a genuinely stuck collector (e.g. one swallowing
 * `CancellationException`) fails the test instead of hanging CI.
 */
fun ViewModel.clearForTest(scheduler: TestCoroutineScheduler? = null) {
    val job = viewModelScope.coroutineContext[Job]
    val method = ViewModel::class.java.getMethod("clear\$lifecycle_viewmodel")
    method.invoke(this)
    if (job == null) return
    val deadlineMillis = System.currentTimeMillis() + 5_000
    while (job.isActive) {
        scheduler?.advanceUntilIdle()
        if (!job.isActive) break
        if (System.currentTimeMillis() >= deadlineMillis) {
            throw AssertionError(
                "${this::class.simpleName}'s viewModelScope job did not reach a terminal state " +
                    "within 5s of cancellation — a collector is likely swallowing " +
                    "CancellationException instead of letting it propagate.",
            )
        }
        Thread.sleep(10)
    }
}
