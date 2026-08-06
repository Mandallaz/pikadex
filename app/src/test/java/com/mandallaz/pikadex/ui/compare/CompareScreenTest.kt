package com.mandallaz.pikadex.ui.compare

import org.junit.Assert.assertEquals
import org.junit.Test

class CompareScreenTest {

    @Test
    fun `a positive delta is prefixed with a plus sign`() {
        assertEquals("+15", deltaLabel(15))
    }

    @Test
    fun `a negative delta keeps its own minus sign, no double sign`() {
        assertEquals("-8", deltaLabel(-8))
    }

    @Test
    fun `a zero delta reads as a tie, not a bare 0`() {
        assertEquals("±0", deltaLabel(0))
    }
}
