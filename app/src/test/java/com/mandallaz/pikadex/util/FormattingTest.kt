package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** F108 — [formatBytes] was a private SettingsScreen helper; it's reusable for other storage
 *  displays, so it now lives here and gets direct coverage. */
class FormattingTest {

    @Test
    fun `bytes under a kilobyte are plain`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1 B", formatBytes(1))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `kilobyte range is rounded to whole KB`() {
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("1 KB", formatBytes(1500))
        assertEquals("2 KB", formatBytes(1536))
    }

    @Test
    fun `megabyte range keeps one decimal place`() {
        assertEquals("1.0 MB", formatBytes(1024L * 1024L))
        assertEquals("1.5 MB", formatBytes((1024L * 1024L) + (512L * 1024L)))
        assertEquals("2.6 MB", formatBytes((1024L * 1024L * 26) / 10))
    }
}