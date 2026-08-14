package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mandallaz.pikadex.data.LanguageSettings
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** F114 — a badge narrower than the full English type name falls back to the short form instead
 *  of clipping, but only in English; other locales keep their full translated name regardless of
 *  fit (out of this feature's scope). */
@RunWith(AndroidJUnit4::class)
class TypeBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // These tests mutate the LanguageSettings singleton directly (no Context, so it's not
    // persisted — see PrefsStore.set()'s early-return when uninitialized). Reset it so a language
    // change here can't leak into whatever test runs next in this process, same reasoning as this
    // session's B50/B51/B52/B53 ViewModel-leak fixes for JVM unit tests.
    @After
    fun tearDown() {
        LanguageSettings.setLanguage("en")
    }

    @Test
    fun englishNameShortensToFitANarrowBadge() {
        composeTestRule.setContent {
            TypeBadge(typeName = "psychic", modifier = Modifier.width(60.dp))
        }

        composeTestRule.onNodeWithText("PSY").assertExists()
    }

    @Test
    fun englishNameStaysFullWhenTheBadgeHasRoom() {
        composeTestRule.setContent {
            TypeBadge(typeName = "psychic", modifier = Modifier.width(200.dp))
        }

        composeTestRule.onNodeWithText("PSYCHIC").assertExists()
    }

    @Test
    fun nonEnglishNameNeverShortensEvenWhenNarrow() {
        LanguageSettings.setLanguage("fr")

        composeTestRule.setContent {
            TypeBadge(typeName = "psychic", modifier = Modifier.width(60.dp))
        }

        // French has no short-form table (out of scope) — the full translated name is expected
        // to still be requested, even though it will visually clip/ellipsize in this narrow badge.
        composeTestRule.onNodeWithText("PSY").assertDoesNotExist()
    }
}
