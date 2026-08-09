package com.mandallaz.pikadex.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test

/** [SMOGON_TIER_EXPLANATION] is the content behind the "?" help icon in Settings (issue #30) — a
 *  pure data list kept separate from the composable so its content is testable without a Compose
 *  runtime. */
class SmogonTierExplanationDialogTest {

    @Test
    fun `covers every primary tier code`() {
        val allBody = SMOGON_TIER_EXPLANATION.joinToString(" ") { it.body }
        listOf("AG", "Uber", "OU", "UU", "RU", "NU", "PU", "ZU").forEach { tierCode ->
            assertTrue("expected $tierCode to be mentioned", allBody.contains(tierCode))
        }
    }

    @Test
    fun `no section is blank`() {
        SMOGON_TIER_EXPLANATION.forEach { section ->
            assertTrue(section.body.isNotBlank())
            section.heading?.let { assertTrue(it.isNotBlank()) }
        }
    }
}
