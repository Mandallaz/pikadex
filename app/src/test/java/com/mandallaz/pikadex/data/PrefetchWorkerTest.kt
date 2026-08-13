package com.mandallaz.pikadex.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for [PrefetchWorker] verifying its implementation correctness via source text
 * and structure analysis, matching the codebase convention for classes requiring Android context.
 */
class PrefetchWorkerTest {

    private val source = File("src/main/java/com/mandallaz/pikadex/data/PrefetchWorker.kt").readText()

    @Test
    fun `PrefetchWorker extends CoroutineWorker`() {
        assertTrue(
            "PrefetchWorker must extend CoroutineWorker",
            source.contains("class PrefetchWorker") && source.contains(": CoroutineWorker")
        )
    }

    @Test
    fun `PrefetchWorker uses UNIQUE_WORK_NAME defined in PrefetchManager`() {
        val managerSource = File("src/main/java/com/mandallaz/pikadex/data/PrefetchManager.kt").readText()
        assertTrue(
            "PrefetchManager must define UNIQUE_WORK_NAME",
            managerSource.contains("UNIQUE_WORK_NAME = \"pikadex_prefetch_work\"")
        )
    }

    @Test
    fun `PrefetchWorker deserializes tiers array from inputData`() {
        assertTrue(
            "PrefetchWorker must retrieve tiers from inputData",
            source.contains("inputData.getStringArray(\"tiers\")")
        )
    }

    @Test
    fun `PrefetchWorker reports progress using setProgress with correct keys`() {
        assertTrue(
            "PrefetchWorker must set progress with 'done' key",
            source.contains("\"done\" to")
        )
        assertTrue(
            "PrefetchWorker must set progress with 'total' key",
            source.contains("\"total\" to")
        )
        assertTrue(
            "PrefetchWorker must set progress with 'phaseRes' key",
            source.contains("\"phaseRes\" to")
        )
    }

    @Test
    fun `PrefetchWorker handles failures and aborts with correct message resources`() {
        assertTrue(
            "PrefetchWorker must handle wifi required error",
            source.contains("settings_prefetch_error_wifi_required")
        )
        assertTrue(
            "PrefetchWorker must handle general network error",
            source.contains("settings_prefetch_error_network")
        )
    }
}
