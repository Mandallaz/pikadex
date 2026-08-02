package com.mandallaz.pikadex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mandallaz.pikadex.navigation.PokedexNavHost
import com.mandallaz.pikadex.ui.theme.PokeDexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PokeDexTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PokedexNavHost()
                }
            }
        }
    }
}
