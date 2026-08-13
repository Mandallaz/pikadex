package com.mandallaz.pikadex.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B26 — the Wi-Fi-only guard used to be checked once, at the moment the user tapped "Prefetch
 * now", so walking out of Wi-Fi range mid-run silently kept going over cellular. Now with WorkManager,
 * PrefetchWorker does mid-run checks inside doWork and runPrefetchBatch.
 */
class PrefetchManagerWifiGuardTest {

    private val source = File("src/main/java/com/mandallaz/pikadex/data/PrefetchWorker.kt").readText()

    @Test
    fun `the tier loop checks the metered guard at each tier boundary`() {
        val doWorkFn = source.substringAfter("override suspend fun doWork()").substringBefore("private fun abortIfWentMetered")
        assertTrue(
            "doWork()'s tiers.forEach loop must call abortIfWentMetered before starting each tier",
            doWorkFn.contains("abortIfWentMetered(appContext)")
        )
    }

    @Test
    fun `the per-unit progress callback also checks the metered guard`() {
        val doWorkFn = source.substringAfter("override suspend fun doWork()").substringBefore("private fun abortIfWentMetered")
        val onProgress = doWorkFn.substringAfter("runPrefetchBatch(units, PREFETCH_CONCURRENCY) { done ->")
        assertTrue(
            "the onProgress callback passed to runPrefetchBatch must also call abortIfWentMetered",
            onProgress.contains("abortIfWentMetered")
        )
    }

    @Test
    fun `abortIfWentMetered triggers metered exception on metered network`() {
        val doWorkFn = source.substringAfter("override suspend fun doWork()").substringBefore("private fun abortIfWentMetered")
        assertTrue(
            "expected MeteredNetworkException being thrown on metered network check",
            doWorkFn.contains("throw MeteredNetworkException()")
        )
    }
}
