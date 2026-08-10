package com.mandallaz.pikadex.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.mandallaz.pikadex.data.LanguageSettings
import java.util.Locale

/**
 * F35/B8 — overrides `stringResource()`'s locale to [LanguageSettings.currentLanguage] instead of
 * the device's system locale, by wrapping [content] in a `Context` whose configuration carries
 * that locale.
 *
 * Applied once around the whole app in `MainActivity`, but **also needed again inside every
 * `Dialog`/`Popup`** (see [com.mandallaz.pikadex.ui.settings.SmogonTierExplanationDialog],
 * [com.mandallaz.pikadex.ui.components.OptionsDialog]): Compose's `Dialog` composable creates its
 * content in a new Android `Window`, and its underlying `DialogWrapper` re-resolves `LocalContext`
 * from that window's own (un-overridden) context rather than inheriting the ambient
 * `CompositionLocalProvider` from outside the dialog — confirmed by B8's actual bug, where every
 * other translated string on the Settings screen followed the language picker correctly but this
 * dialog's content stayed in English. Wrapping the dialog's own content here re-asserts the
 * override at the point Compose actually needs it.
 */
@Composable
fun LocalizedContext(content: @Composable () -> Unit) {
    val language by LanguageSettings.currentLanguage.collectAsState()
    val baseContext = LocalContext.current
    // LocalConfiguration.current, not baseContext.resources.configuration directly — reading
    // Configuration off LocalContext doesn't recompose on a real device configuration change
    // (e.g. rotation), only LocalConfiguration.current does.
    val baseConfiguration = LocalConfiguration.current
    val localizedContext = remember(baseContext, baseConfiguration, language) {
        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(baseConfiguration)
        configuration.setLocale(locale)
        baseContext.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(LocalContext provides localizedContext) {
        content()
    }
}
