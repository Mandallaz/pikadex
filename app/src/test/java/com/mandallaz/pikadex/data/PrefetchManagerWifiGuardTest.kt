package com.mandallaz.pikadex.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B26 — the Wi-Fi-only guard used to be checked once, at the moment the user tapped "Prefetch
 * now", so walking out of Wi-Fi range mid-run silently kept going over cellular. `start()` needs
 * a real `android.content.Context` (not just Coil's `imageLoader`, which only some tiers touch —
 * the `Context` parameter itself has no lightweight JVM test double), so this guards the
 * source-level fix, same technique and reasoning as [PrefetchManagerCancelRaceTest]. The
 * mid-run-abort behavior was also verified manually on-device (started a prefetch on Wi-Fi,
 * disabled Wi-Fi, confirmed the run stops and the screen shows the Wi-Fi-required error instead of
 * continuing to progress).
 */
class PrefetchManagerWifiGuardTest {

    private val source = File("src/main/java/com/mandallaz/pikadex/data/PrefetchManager.kt").readText()

    @Test
    fun `the tier loop checks the metered guard at each tier boundary`() {
        val startFn = source.substringAfter("fun start(").substringBefore("\n    /** B26")
        assertTrue(
            "start()'s tiers.forEach loop must call abortIfWentMetered before starting each tier",
            startFn.contains("abortIfWentMetered(appContext, runId)")
        )
    }

    @Test
    fun `the per-unit progress callback also checks the metered guard`() {
        val startFn = source.substringAfter("fun start(").substringBefore("\n    /** B26")
        val onProgress = startFn.substringAfter("runPrefetchBatch(units, PREFETCH_CONCURRENCY) { done ->")
        assertTrue(
            "the onProgress callback passed to runPrefetchBatch must also call abortIfWentMetered",
            onProgress.substringBefore("}\n                    }").contains("abortIfWentMetered")
        )
    }

    @Test
    fun `abortIfWentMetered publishes the wifi-required error before self-cancelling`() {
        val guardFn = source.substringAfter("private fun abortIfWentMetered(").substringBefore("\n    }")
        val failedIndex = guardFn.indexOf("settings_prefetch_error_wifi_required")
        val cancelIndex = guardFn.indexOf("job?.cancel()")
        assertTrue("expected both the Failed(wifi_required) update and job?.cancel()", failedIndex >= 0 && cancelIndex >= 0)
        assertTrue("the Failed(wifi_required) state must be published before self-cancelling", failedIndex < cancelIndex)
    }
}
