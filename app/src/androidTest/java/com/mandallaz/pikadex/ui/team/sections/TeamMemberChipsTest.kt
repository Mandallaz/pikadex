package com.mandallaz.pikadex.ui.team.sections

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** F77 — tapping a team member's sprite opens its detail page, the same way a Team Suggestions
 *  tile's sprite already did (issue #17). */
@RunWith(AndroidJUnit4::class)
class TeamMemberChipsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingTheSpriteInvokesOnSpriteClickNotOnRemove() {
        var spriteClicked = false
        var removeClicked = false
        composeTestRule.setContent {
            TeamMemberChip(
                member = NamedApiResource(name = "pikachu", url = "https://pokeapi.co/api/v2/pokemon/25/"),
                speciesNames = emptyMap(),
                language = "en",
                onRemove = { removeClicked = true },
                onSpriteClick = { spriteClicked = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("pikachu").performClick()

        assertTrue(spriteClicked)
        assertTrue(!removeClicked)
    }

    // Regression guard: shrinking the remove button's touch target to clear the sprite (above)
    // must not make the remove button itself unreachable.
    @Test
    fun tappingTheRemoveButtonInvokesOnRemoveNotOnSpriteClick() {
        var spriteClicked = false
        var removeClicked = false
        composeTestRule.setContent {
            TeamMemberChip(
                member = NamedApiResource(name = "pikachu", url = "https://pokeapi.co/api/v2/pokemon/25/"),
                speciesNames = emptyMap(),
                language = "en",
                onRemove = { removeClicked = true },
                onSpriteClick = { spriteClicked = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Remove Pikachu from team").performClick()

        assertTrue(removeClicked)
        assertTrue(!spriteClicked)
    }
}
