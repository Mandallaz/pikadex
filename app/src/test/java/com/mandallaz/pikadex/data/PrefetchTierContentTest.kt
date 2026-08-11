package com.mandallaz.pikadex.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B32/B33 — [PrefetchManager.buildUnits]' tier-specific wiring needs a real Android `Context`
 * (`context.imageLoader`), so it's manual-verification-only per this file's own existing doc
 * comment; these checks guard the source text instead, same technique as the build-config checks
 * elsewhere in this suite (e.g. `CleartextTrafficLockdownTest`).
 */
class PrefetchTierContentTest {

    private val source = File("src/main/java/com/mandallaz/pikadex/data/PrefetchManager.kt").readText()

    @Test
    fun `SPRITES_EXTRA no longer prefetches the unused shiny mini-sprite`() {
        assertFalse(
            "shinySpriteUrl is never displayed by any composable — see B32",
            source.contains("Sprites.shinySpriteUrl(id)")
        )
    }

    @Test
    fun `ESSENTIALS prefetches type badge icons`() {
        assertTrue(
            "TypeBadge renders typeIconUrl on every screen but no tier fetched it offline — see B33",
            source.contains("Sprites.typeIconUrl(")
        )
    }
}
