package com.mandallaz.pikadex.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 */
@RunWith(AndroidJUnit4::class)
class ExternalLinksTest {

    @Test
    fun openingALinkFromANonActivityContextDoesNotCrash() {
        val applicationContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        applicationContext.openExternalLink("https://www.smogon.com/dex/sv/pokemon/pikachu/")
    }
}
