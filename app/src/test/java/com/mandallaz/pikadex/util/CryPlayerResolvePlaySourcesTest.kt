package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CryPlayerResolvePlaySourcesTest {

    private val validRemote = "https://pokeapi.co/media/cry.ogg"
    private val otherValidRemote = "https://raw.githubusercontent.com/cry.ogg"
    private val invalidRemote = "https://evil.example.com/cry.ogg"
    private val localPath = "/data/cache/cry.ogg"

    @Test
    fun `a valid remote source is kept as-is, a valid fallback untouched`() {
        val (source, fallback) = CryPlayer.resolvePlaySources(validRemote, otherValidRemote)
        assertEquals(validRemote, source)
        assertEquals(otherValidRemote, fallback)
    }

    @Test
    fun `a valid remote source is kept, but an invalid fallback is dropped`() {
        val (source, fallback) = CryPlayer.resolvePlaySources(validRemote, invalidRemote)
        assertEquals(validRemote, source)
        assertEquals(null, fallback)
    }

    @Test
    fun `a local file path source is never validated against UrlValidator`() {
        val (source, fallback) = CryPlayer.resolvePlaySources(localPath, null)
        assertEquals(localPath, source)
        assertEquals(null, fallback)
    }

    @Test
    fun `an invalid remote source with a valid fallback promotes the fallback to primary`() {
        val (source, fallback) = CryPlayer.resolvePlaySources(invalidRemote, otherValidRemote)
        assertEquals(otherValidRemote, source)
        assertEquals(null, fallback)
    }

    @Test
    fun `an invalid remote source with an invalid fallback resolves to nothing playable`() {
        val (source, fallback) = CryPlayer.resolvePlaySources(invalidRemote, "https://also-evil.example.com/cry.ogg")
        assertEquals(null, source)
        assertEquals(null, fallback)
    }

    @Test
    fun `an invalid remote source with no fallback resolves to nothing playable`() {
        val (source, fallback) = CryPlayer.resolvePlaySources(invalidRemote, null)
        assertEquals(null, source)
        assertEquals(null, fallback)
    }

    @Test
    fun `a valid source with no fallback leaves fallback null`() {
        val (source, fallback) = CryPlayer.resolvePlaySources(validRemote, null)
        assertEquals(validRemote, source)
        assertEquals(null, fallback)
    }
}
