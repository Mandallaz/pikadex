package com.mandallaz.pikadex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mandallaz.pikadex.data.DisplaySettings
import com.mandallaz.pikadex.navigation.PokedexNavHost
import com.mandallaz.pikadex.ui.LocalizedContext
import com.mandallaz.pikadex.ui.theme.PokeDexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val amoledBlack by DisplaySettings.amoledEnabled.collectAsState()
            // Turning AMOLED black on is itself a request for dark mode, not just a modifier of it
            // — the user shouldn't also have to separately flip the system theme.
            val darkTheme = isSystemInDarkTheme() || amoledBlack
            // B27 — provided here, above LocalizedContext, so external-link callers can read the
            // real Activity via LocalActivity.current instead of LocalContext.current, which
            // LocalizedContext overrides with a createConfigurationContext-derived Context that
            // carries no Activity token (see ExternalLinks.kt's own doc for the full story).
            CompositionLocalProvider(LocalActivity provides this) {
                PokeDexTheme(darkTheme = darkTheme, amoledBlack = amoledBlack) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        LocalizedContext {
                            PokedexNavHost()
                        }
                    }
                }
            }
        }
    }
}
