package com.mandallaz.pikadex

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B24 — Sprites (50-150MB) plus Sprites-extra ("roughly doubles" that) can approach 300MB, so
 * Coil's image disk cache must be sized to hold both tiers at once — otherwise Coil's own LRU
 * eviction silently undoes part of a still-running prefetch. Guards the cache size rather than
 * instantiating a real `ImageLoader`, which needs an Android `Context` unavailable to a plain
 * JVM unit test.
 */
class PikaDexApplicationTest {

    @Test
    fun `image disk cache is sized to hold both Sprites tiers at once`() {
        val source = File("src/main/java/com/mandallaz/pikadex/PikaDexApplication.kt").readText()
        val match = Regex("""maxSizeBytes\((\d+)L\s*\*\s*1024\s*\*\s*1024\)""").find(source)
        val maxSizeMb = requireNotNull(match) { "couldn't find diskCache maxSizeBytes in PikaDexApplication.kt" }
            .groupValues[1].toInt()
        assertTrue(
            "image disk cache is ${maxSizeMb}MB, too small to hold Sprites + Sprites-extra combined (~300MB)",
            maxSizeMb >= 300
        )
    }
}
