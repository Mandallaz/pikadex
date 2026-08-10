package com.mandallaz.pikadex

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.mandallaz.pikadex.data.DisplaySettings
import com.mandallaz.pikadex.data.LanguageSettings
import com.mandallaz.pikadex.navigation.PokedexNavHost
import com.mandallaz.pikadex.ui.theme.PokeDexTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val amoledBlack by DisplaySettings.amoledEnabled.collectAsState()
            // Turning AMOLED black on is itself a request for dark mode, not just a modifier of it
            // — the user shouldn't also have to separately flip the system theme.
            val darkTheme = isSystemInDarkTheme() || amoledBlack
            PokeDexTheme(darkTheme = darkTheme, amoledBlack = amoledBlack) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LocalizedContent {
                        PokedexNavHost()
                    }
                }
            }
        }
    }
}

/** F35's UI-chrome axis: overrides `stringResource()`'s locale to [LanguageSettings.currentLanguage]
 *  instead of the device's system locale, by wrapping [content] in a `Context` whose configuration
 *  carries that locale — `stringResource()` reads `LocalContext.current.resources`, so every
 *  composable under this provider picks it up automatically, including on a language change
 *  (recomposes since [LanguageSettings.currentLanguage] is collected as state). Deliberately always
 *  active, even for the "en" default: pinning the app's language to an explicit choice regardless of
 *  device locale is the point of F35's picker, not just an override for non-English choices. */
@Composable
private fun LocalizedContent(content: @Composable () -> Unit) {
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
