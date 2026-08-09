package com.mandallaz.pikadex.data

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * [CryCache.writeAtomically] is a plain java.io.File operation — no Context needed, same reasoning
 * as [JsonDiskCacheTest]'s own doc on testing file-backed caches without one.
 */
class CryCacheTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = createTempDirectory(prefix = "cry-cache-test").toFile()
    }

    @After
    fun tearDown() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    @Test
    fun `a write leaves exactly the target file behind, no tmp leftover`() {
        val target = File(dir, "25.ogg")
        assertTrue(CryCache.writeAtomically(target, byteArrayOf(1, 2, 3)))
        assertEquals(listOf("25.ogg"), dir.listFiles()!!.map { it.name })
    }

    @Test
    fun `a written value round-trips through the target file's bytes`() {
        val target = File(dir, "25.ogg")
        CryCache.writeAtomically(target, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }

    // Regression: writing the target file directly left a reader able to observe a truncated file
    // if the process died mid-write — a same-directory rename is atomic, so a reader never sees a
    // partial file.
    @Test
    fun `a write that can't rename its tmp file over the target leaves the previous file untouched`() {
        val target = File(dir, "25.ogg")
        CryCache.writeAtomically(target, byteArrayOf(1, 2, 3))

        dir.setWritable(false)
        val result = CryCache.writeAtomically(target, byteArrayOf(9, 9, 9))
        dir.setWritable(true)

        assertFalse(result)
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertFalse(File(dir, "25.ogg.tmp").exists())
    }
}
