package com.mandallaz.pikadex.util

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mandallaz.pikadex.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B22 — [Context.openExternalLink] crashed whenever called on a `Context` without an Activity
 * token, which is exactly what `LocalContext.current` resolves to app-wide once
 * [com.mandallaz.pikadex.ui.LocalizedContext] (F35/B8) wraps it via `createConfigurationContext`.
 * `ApplicationProvider`'s context is the same kind of non-Activity `Context`, so it reproduces the
 * bug without needing an actual Activity: before the fix, this throws
 * `AndroidRuntimeException: Calling startActivity() from outside of an Activity context requires
 * the FLAG_ACTIVITY_NEW_TASK flag`; after the fix, the added flag lets it launch (or, on a runner
 * with no browser installed, `openExternalLink`'s own catch swallows the resulting
 * `ActivityNotFoundException` — either way, no exception reaches this test).
 *
 * B27 — a real `Activity` receiver must also work, now that `FLAG_ACTIVITY_NEW_TASK` is only added
 * for non-Activity receivers (see this file's own doc on that decision). Whether the flag's
 * absence actually keeps the Custom Tab in PikaDex's own task (the regression B27 fixes) isn't
 * something either test here can observe — `openExternalLink` launches the tab and doesn't expose
 * the built `Intent` — so that property was verified manually on-device instead (opened a Smogon
 * link, confirmed the back button returns to the detail screen rather than the launcher).
 */
@RunWith(AndroidJUnit4::class)
class ExternalLinksTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openingALinkFromANonActivityContextDoesNotCrash() {
        val applicationContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        applicationContext.openExternalLink("https://www.smogon.com/dex/sv/pokemon/pikachu/")
    }

    @Test
    fun openingALinkFromARealActivityDoesNotCrash() {
        composeTestRule.activity.openExternalLink("https://www.smogon.com/dex/sv/pokemon/pikachu/")
    }
}
