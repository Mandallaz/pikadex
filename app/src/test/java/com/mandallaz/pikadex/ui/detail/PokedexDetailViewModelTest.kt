package com.mandallaz.pikadex.ui.detail

import com.mandallaz.pikadex.util.Cries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * resolveCrySource is the F34 decision of what CryPlayer should be given — genuinely a plain
 * java.io.File check (a Context is only needed to find *where* the cache directory is, done by the
 * caller before this runs), so no Robolectric/Context needed here, same reasoning as
 * JsonDiskCacheTest's own doc on testing file-backed caches without Context.
 */
class PokedexDetailViewModelTest {

    private val tempDir = createTempDirectory(prefix = "cry-source-test").toFile()

    @Test
    fun `a genuinely cached (non-empty) file is used directly, with no fallback`() {
        val file = File(tempDir, "25.ogg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val source = resolveCrySource(file, id = 25)
        assertEquals(file.absolutePath, source.primary)
        assertNull(source.fallback)
    }

    // A File pointing at a path that doesn't exist (the common case — not yet prefetched) has
    // length() 0, same as an empty file some interrupted download could theoretically leave behind.
    @Test
    fun `a missing or empty file falls back to the network URL with a legacy fallback`() {
        val missing = File(tempDir, "does-not-exist.ogg")
        val source = resolveCrySource(missing, id = 25)
        assertEquals(Cries.latestCryUrl(25), source.primary)
        assertEquals(Cries.legacyCryUrl(25), source.fallback)
    }

    @Test
    fun `an empty (zero-byte) file is treated the same as a missing one`() {
        val empty = File(tempDir, "empty.ogg").apply { createNewFile() }
        val source = resolveCrySource(empty, id = 1)
        assertEquals(Cries.latestCryUrl(1), source.primary)
        assertEquals(Cries.legacyCryUrl(1), source.fallback)
    }
}
