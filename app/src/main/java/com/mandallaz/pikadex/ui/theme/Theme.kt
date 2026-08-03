package com.mandallaz.pikadex.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PokedexRed = Color(0xFFE3350D)
private val PokedexRedDark = Color(0xFFB92B0C)
private val PokedexBlue = Color(0xFF2A75BB)
private val PokedexYellow = Color(0xFFFFCB05)

private val LightColors = lightColorScheme(
    primary = PokedexRed,
    onPrimary = Color.White,
    secondary = PokedexBlue,
    tertiary = PokedexYellow,
    background = Color(0xFFF7F7F8),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = PokedexRedDark,
    onPrimary = Color.White,
    secondary = PokedexBlue,
    tertiary = PokedexYellow,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

@Composable
fun PokeDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Defaults off: on API 31+ this derives the whole scheme from the user's wallpaper, which used
    // to silently replace PikaDex's own red/blue/yellow brand palette above with whatever colors
    // happen to be dynamic-themed that day — a Pokédex that doesn't reliably look like a Pokédex.
    // Left as a parameter (not deleted) in case a future settings toggle wants to offer it as an
    // opt-in.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
