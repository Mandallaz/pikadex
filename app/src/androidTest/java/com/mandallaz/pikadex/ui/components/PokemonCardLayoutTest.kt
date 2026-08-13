package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * [PokemonCard]'s `heightIn(min = ...)` exists so a 1-line-name card (e.g. "Pikachu") ends up the
 * same height as a 2-line-name neighbor (e.g. "Zamazenta Crowned") in the same grid row, rather
 * than shorter and top-aligned against it (issue #49). Regression coverage for that invariant
 * specifically — not for the min value itself, which is free to change as long as this holds.
 */
class PokemonCardLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun oneLineAndTwoLineNameCardsHaveEqualHeight() {
        // Both cards in one setContent call, side by side like an actual grid row — a single
        // AndroidComposeTestRule can't call setContent more than once per test.
        composeTestRule.setContent {
            Row {
                PokemonCard(
                    id = 25,
                    displayName = "Pikachu",
                    baseSpeciesId = null,
                    isFavorite = false,
                    isInTeam = false,
                    isTeamFull = false,
                    onClick = {},
                    onToggleTeam = {},
                    onToggleFavorite = {},
                    modifier = Modifier.width(120.dp).testTag("oneLine")
                )
                PokemonCard(
                    id = 10024,
                    displayName = "Zamazenta Crowned",
                    baseSpeciesId = null,
                    isFavorite = false,
                    isInTeam = false,
                    isTeamFull = false,
                    onClick = {},
                    onToggleTeam = {},
                    onToggleFavorite = {},
                    modifier = Modifier.width(120.dp).testTag("twoLine")
                )
            }
        }

        val oneLineHeight = composeTestRule.onNodeWithTag("oneLine").fetchSemanticsNode().size.height
        val twoLineHeight = composeTestRule.onNodeWithTag("twoLine").fetchSemanticsNode().size.height

        assert(oneLineHeight == twoLineHeight) {
            "expected equal card heights, got one-line=$oneLineHeight two-line=$twoLineHeight"
        }
    }

    // F82/F88 — the dex list card shows each Pokémon's type(s) as icon-only TypeBadge pills
    // (compact; the full icon+name pill is reserved for the detail screen and other call sites)
    // — the localized name still lives on the icon's contentDescription per F88.
    @Test
    fun cardShowsATypeBadgeIconForEachType() {
        composeTestRule.setContent {
            PokemonCard(
                id = 6,
                displayName = "Charizard",
                baseSpeciesId = null,
                types = listOf("fire", "flying"),
                isFavorite = false,
                isInTeam = false,
                isTeamFull = false,
                onClick = {},
                onToggleTeam = {},
                onToggleFavorite = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Fire").assertExists()
        composeTestRule.onNodeWithContentDescription("Flying").assertExists()
        composeTestRule.onNodeWithText("FIRE").assertDoesNotExist()
    }
}
