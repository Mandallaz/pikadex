package com.mandallaz.pikadex

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real device rotation, on the real app, for the two screens that explicitly branch on compact
 * height (see [com.mandallaz.pikadex.ui.list.PokedexListScreen]'s `COMPACT_HEADER_MAX_HEIGHT` and
 * [com.mandallaz.pikadex.util.isCompactMatrixLayout] on the Team screen) — landscape is exactly
 * where those branches trigger on a phone-sized emulator. Hits the real network (PokeAPI), same as
 * every other manual-verification-only piece of this app; there's no fake repository wired in for
 * instrumented tests yet.
 */
@RunWith(AndroidJUnit4::class)
class RotationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pokedexListSurvivesRotationToLandscapeAndBack() {
        composeTestRule.onNodeWithText("Pokédex").assertExists()

        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Pokédex").assertExists()

        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Pokédex").assertExists()
    }

    @Test
    fun teamScreenSurvivesRotationToLandscapeAndBack() {
        composeTestRule.onNodeWithText("Team").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Team").assertExists()

        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Team").assertExists()
    }
}
