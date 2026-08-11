package com.mandallaz.pikadex.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B25 — two races in [PrefetchManager.start]/[PrefetchManager.cancel]:
 *  1. The previous job used to be cancelled from *inside* the new coroutine's own body
 *     (`previousJob?.cancelAndJoin()`), so a `cancel()` call landing between `start()` returning
 *     and the new coroutine being dispatched could cancel the new job before it ever cancelled the
 *     old one, orphaning a still-running download with nothing left able to stop it.
 *  2. `cancel()` published `Idle` without joining the job, so a unit that finished right as
 *     `cancel()` ran could still call `onProgress` afterward, publishing a stale `Running(...)`
 *     over the `Idle` state.
 *
 * Both are timing races inside `PrefetchManager`'s own real (non-test) `Dispatchers.IO` scope, and
 * every tier's execution — including `ESSENTIALS` since B33 added type-icon prefetching to it —
 * needs a real Android `Context.imageLoader`, so `start()` can't be driven end-to-end in a plain
 * JVM test any more than `buildUnits`'s tier-specific wiring already was (see that file's own doc).
 * This guards the source-level fix — a monotonic run id checked before every `_state` write —
 * instead; the actual race-free behavior was also verified manually on-device (rapid-tap Cancel
 * during a real prefetch, confirming the screen settles on Idle and no background download
 * continues).
 */
class PrefetchManagerCancelRaceTest {

    private val source = File("src/main/java/com/mandallaz/pikadex/data/PrefetchManager.kt").readText()

    @Test
    fun `the previous job is cancelled synchronously in start, not from inside the new coroutine`() {
        val startFn = source.substringAfter("fun start(").substringBefore("\n    fun cancel()")
        val cancelCallIndex = startFn.indexOf("previousJob?.cancel()")
        val launchIndex = startFn.indexOf("scope.launch")
        assertTrue(
            "previousJob?.cancel() must run before scope.launch(...), not inside it",
            cancelCallIndex in 0 until launchIndex
        )
    }

    @Test
    fun `cancel bumps the run id before publishing Idle`() {
        val cancelFn = source.substringAfter("fun cancel() {").substringBefore("\n    }")
        val bumpIndex = cancelFn.indexOf("currentRunId.incrementAndGet()")
        val idleIndex = cancelFn.indexOf("PrefetchState.Idle")
        assertTrue("cancel() must increment currentRunId before publishing Idle", bumpIndex in 0 until idleIndex)
    }

    @Test
    fun `every state update inside the run loop is guarded by isCurrentRun`() {
        val startFn = source.substringAfter("fun start(").substringBefore("\n    fun cancel()")
        // The three _state.update calls that run once per tier/unit (Running x2, the onProgress
        // callback, Finished, Failed) must each be reachable only when isCurrentRun(runId) holds.
        val guardedUpdates = Regex("""isCurrentRun\(runId\)[^}]*?_state\.update""").findAll(startFn).count()
        assertTrue(
            "expected at least 3 isCurrentRun-guarded _state.update calls in start(), found $guardedUpdates",
            guardedUpdates >= 3
        )
    }
}
