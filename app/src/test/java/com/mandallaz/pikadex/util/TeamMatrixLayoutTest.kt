package com.mandallaz.pikadex.util

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the bug where the coverage matrix on [TeamScreen][com.mandallaz.pikadex.ui.team.TeamScreen]
 * became unreachable: the layout compared the *total* viewport height against a hardcoded guess
 * of the header's size, so once the suggestions card grew taller than that guess, the pinned
 * (non-scrolling-page) layout kept being chosen even though the real header left no room for the
 * matrix — and that layout has no page-scroll gesture able to recover it.
 */
class TeamMatrixLayoutTest {

    @Test
    fun `plenty of room after the real header stays non-compact`() {
        assertFalse(isCompactMatrixLayout(maxHeight = 750.dp, headerHeight = 250.dp, minRemainingHeight = 150.dp))
    }

    @Test
    fun `a tall viewport with a header that leaves too little room goes compact`() {
        // This is the exact regression: total height alone (750dp) used to read as "plenty of
        // room" and skip the page-scroll fallback, even though the actual header (650dp, e.g. a
        // grown suggestions card) leaves only 100dp for the matrix — below the 150dp floor.
        assertTrue(isCompactMatrixLayout(maxHeight = 750.dp, headerHeight = 650.dp, minRemainingHeight = 150.dp))
    }

    @Test
    fun `header taller than the viewport goes compact`() {
        assertTrue(isCompactMatrixLayout(maxHeight = 250.dp, headerHeight = 300.dp, minRemainingHeight = 150.dp))
    }

    @Test
    fun `remaining space exactly at the floor is enough`() {
        assertFalse(isCompactMatrixLayout(maxHeight = 400.dp, headerHeight = 250.dp, minRemainingHeight = 150.dp))
    }

    @Test
    fun `remaining space just above the floor stays non-compact`() {
        assertFalse(isCompactMatrixLayout(maxHeight = 401.dp, headerHeight = 250.dp, minRemainingHeight = 150.dp))
    }
}
