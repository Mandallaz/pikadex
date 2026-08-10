package com.mandallaz.pikadex.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** [runPrefetchBatch] is the concurrency/failure-counting engine shared by every [PrefetchTier] —
 *  the one part of the prefetch system with real unit test coverage (the tier-specific wiring in
 *  [PrefetchManager] is network-backed and manual-verification only). */
class PrefetchBatchTest {

    @Test
    fun `every unit runs and progress reaches the total`() = runBlocking {
        val units = (1..10).map { suspend { } }
        val progress = mutableListOf<Int>()
        val failed = runPrefetchBatch(units, concurrency = 3) { done -> progress.add(done) }
        assertEquals(0, failed)
        assertEquals(10, progress.size)
        assertEquals(10, progress.max())
    }

    @Test
    fun `a failing unit is counted but does not stop the rest from running`() = runBlocking {
        val ran = AtomicInteger(0)
        val units = (1..5).map { index ->
            suspend {
                ran.incrementAndGet()
                if (index == 3) throw RuntimeException("boom")
            }
        }
        val failed = runPrefetchBatch(units, concurrency = 2) { }
        assertEquals(1, failed)
        assertEquals(5, ran.get())
    }

    @Test
    fun `every failing unit is counted`() = runBlocking {
        val units = (1..4).map { suspend { throw RuntimeException("boom") } }
        val failed = runPrefetchBatch(units, concurrency = 4) { }
        assertEquals(4, failed)
    }

    @Test
    fun `progress fires once per unit, not once per wave`() = runBlocking {
        val units = (1..7).map { suspend { } }
        val calls = AtomicInteger(0)
        runPrefetchBatch(units, concurrency = 3) { calls.incrementAndGet() }
        assertEquals(7, calls.get())
    }

    @Test
    fun `an empty unit list runs without progress calls and no failures`() = runBlocking {
        var calls = 0
        val failed = runPrefetchBatch(emptyList(), concurrency = 6) { calls++ }
        assertEquals(0, failed)
        assertEquals(0, calls)
    }

    // Regression test for F55: with wave-based chunking (units.chunked(concurrency)), a unit
    // queued right after a full wave had to wait for every unit in that wave to finish — including
    // stragglers — before it could even start, even if another slot in the wave freed up almost
    // immediately. The semaphore-based version hands a freed permit straight to the next queued
    // unit instead.
    @Test
    fun `a slow unit does not block a later unit from starting once a permit frees up`() = runBlocking {
        val slowUnitStarted = CompletableDeferred<Unit>()
        val releaseSlowUnit = CompletableDeferred<Unit>()
        val thirdUnitStarted = CompletableDeferred<Unit>()

        val units = listOf<suspend () -> Unit>(
            { slowUnitStarted.complete(Unit); releaseSlowUnit.await() },
            { /* fast unit — frees its permit almost immediately */ },
            { thirdUnitStarted.complete(Unit) }
        )

        val job = launch { runPrefetchBatch(units, concurrency = 2) { } }

        slowUnitStarted.await()
        // Would time out under wave-based chunking, since the third unit couldn't start until
        // both units of the first wave (including the still-running slow one) had finished.
        withTimeout(1000) { thirdUnitStarted.await() }
        releaseSlowUnit.complete(Unit)
        job.join()
    }

    // Real cancellation of the surrounding job (e.g. Settings' own Cancel button) must propagate
    // out of runPrefetchBatch rather than being caught by the generic Exception handler and
    // counted as a failure — the explicit `catch (e: CancellationException) { throw e }` rethrow
    // is what this proves: without it, the coroutine's own cancellation is an Exception subtype
    // too and would otherwise fall into the "count it and move on" branch.
    @Test
    fun `a cancelled job never completes with a failure count`() = runBlocking {
        val failedResult = AtomicInteger(-1)
        val job = launch {
            val units = listOf<suspend () -> Unit>({ delay(1000) })
            val failed = runPrefetchBatch(units, concurrency = 1) { }
            failedResult.set(failed)
        }
        delay(50)
        job.cancelAndJoin()
        assertEquals(-1, failedResult.get())
    }
}
