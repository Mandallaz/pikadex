package com.mandallaz.pikadex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
