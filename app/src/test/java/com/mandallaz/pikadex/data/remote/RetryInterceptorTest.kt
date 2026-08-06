package com.mandallaz.pikadex.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryInterceptorTest {

    @Test
    fun `runs the full duration and returns false when never cancelled`() {
        val start = System.currentTimeMillis()
        val wasCanceled = sleepInterruptibly(totalMs = 100, sliceMs = 20) { false }
        val elapsed = System.currentTimeMillis() - start

        assertFalse(wasCanceled)
        assertTrue("expected to actually sleep ~100ms, took ${elapsed}ms", elapsed >= 100)
    }

    @Test
    fun `stops early and returns true once cancellation is observed`() {
        var checks = 0
        val start = System.currentTimeMillis()
        // Slices of 20ms, cancelled on the 3rd check — a single long Thread.sleep(1000) would
        // block for the full second regardless; this must return well before that.
        val wasCanceled = sleepInterruptibly(totalMs = 1000, sliceMs = 20) {
            checks++
            checks >= 3
        }
        val elapsed = System.currentTimeMillis() - start

        assertTrue(wasCanceled)
        assertTrue("expected to return quickly, took ${elapsed}ms", elapsed < 500)
    }

    @Test
    fun `an already-cancelled check returns immediately without sleeping`() {
        val start = System.currentTimeMillis()
        val wasCanceled = sleepInterruptibly(totalMs = 1000, sliceMs = 20) { true }
        val elapsed = System.currentTimeMillis() - start

        assertTrue(wasCanceled)
        assertTrue("expected near-zero delay, took ${elapsed}ms", elapsed < 100)
    }
}
