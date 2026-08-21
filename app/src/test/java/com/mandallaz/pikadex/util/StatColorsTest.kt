package com.mandallaz.pikadex.util

import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [StatColors.forPercentile] had no coverage at all — Robolectric, not a plain JUnit test like
 * [TypeColorsTest], since it calls the real `android.graphics.Color.HSVToColor`/`colorToHSV`,
 * which aren't mocked outside Robolectric/instrumentation (same reasoning this codebase already
 * documents for [com.mandallaz.pikadex.util.CryPlayer]'s lazy fields).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatColorsTest {

    private fun hueOf(percentile: Double): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(StatColors.forPercentile(percentile).toArgb(), hsv)
        return hsv[0]
    }

    @Test
    fun `0th percentile is red`() {
        assertEquals(0f, hueOf(0.0), 0.5f)
    }

    @Test
    fun `50th percentile is yellow, the distinguishable midpoint`() {
        assertEquals(60f, hueOf(0.5), 0.5f)
    }

    @Test
    fun `100th percentile is green`() {
        assertEquals(120f, hueOf(1.0), 0.5f)
    }

    @Test
    fun `an out-of-range percentile is clamped rather than producing an invalid hue`() {
        assertEquals(hueOf(0.0), hueOf(-5.0), 0.01f)
        assertEquals(hueOf(1.0), hueOf(5.0), 0.01f)
    }
}
