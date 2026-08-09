package com.mandallaz.pikadex.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Covers [selectColorScheme] (BACKLOG.md F19) — the pure decision of which color scheme to use,
 *  kept separate from [PokeDexTheme] so it doesn't need a Compose runtime to test. */
class ThemeTest {

    @Test
    fun `light theme ignores the amoled flag`() {
        val withoutAmoled = selectColorScheme(darkTheme = false, amoledBlack = false)
        val withAmoled = selectColorScheme(darkTheme = false, amoledBlack = true)

        assertEquals(withoutAmoled.background, withAmoled.background)
        assertNotEquals(Color.Black, withoutAmoled.background)
    }

    @Test
    fun `dark theme without amoled keeps Material dark grey`() {
        val scheme = selectColorScheme(darkTheme = true, amoledBlack = false)

        assertNotEquals(Color.Black, scheme.background)
        assertNotEquals(Color.Black, scheme.surface)
    }

    @Test
    fun `dark theme with amoled turns background and surface pure black`() {
        val scheme = selectColorScheme(darkTheme = true, amoledBlack = true)

        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
    }

    @Test
    fun `amoled dark scheme keeps the same brand accent colors as regular dark`() {
        val regular = selectColorScheme(darkTheme = true, amoledBlack = false)
        val amoled = selectColorScheme(darkTheme = true, amoledBlack = true)

        assertEquals(regular.primary, amoled.primary)
        assertEquals(regular.secondary, amoled.secondary)
        assertEquals(regular.tertiary, amoled.tertiary)
    }
}
