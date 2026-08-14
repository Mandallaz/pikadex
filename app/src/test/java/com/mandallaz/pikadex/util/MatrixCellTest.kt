package com.mandallaz.pikadex.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** F108 — [multiplierLabel]/[multiplierColors] were private to TeamScreen, so the matrix-cell
 *  formatting was only covered indirectly through layout tests. Moved to util/ to test directly. */
class MatrixCellTest {

    @Test
    fun `multiplier label covers the four strong-weak buckets`() {
        assertEquals("×4", multiplierLabel(4.0))
        assertEquals("×2", multiplierLabel(2.0))
        assertEquals("×½", multiplierLabel(0.5))
        assertEquals("×¼", multiplierLabel(0.25))
        assertEquals("×0", multiplierLabel(0.0))
    }

    @Test
    fun `multiplier label returns empty for neutral and unknown multipliers`() {
        assertEquals("", multiplierLabel(1.0))
        assertEquals("", multiplierLabel(3.0))
        assertEquals("", multiplierLabel(0.75))
    }

    @Test
    fun `neutral multiplier has no fill so text follows the theme`() {
        assertEquals(Color.Transparent to Color.Unspecified, multiplierColors(1.0))
    }

    @Test
    fun `defensive zero is the special immunity blue, offensive zero is the worst outcome red`() {
        assertEquals(Color(0xFFB3E5FC) to Color(0xFF01579B), multiplierColors(0.0))
        assertEquals(Color(0xFFFFCDD2) to Color(0xFFB71C1C), multiplierColors(0.0, isOffense = true))
    }

    @Test
    fun `over-one is bad to take and good to deal`() {
        assertEquals(Color(0xFFFFCDD2) to Color(0xFFB71C1C), multiplierColors(2.0))
        assertEquals(Color(0xFFC8E6C9) to Color(0xFF1B5E20), multiplierColors(2.0, isOffense = true))
        assertEquals(Color(0xFFFFCDD2) to Color(0xFFB71C1C), multiplierColors(4.0))
    }

    @Test
    fun `under-one is good to take and bad to deal`() {
        assertEquals(Color(0xFFC8E6C9) to Color(0xFF1B5E20), multiplierColors(0.5))
        assertEquals(Color(0xFFFFCDD2) to Color(0xFFB71C1C), multiplierColors(0.5, isOffense = true))
        assertEquals(Color(0xFFC8E6C9) to Color(0xFF1B5E20), multiplierColors(0.25))
    }
}