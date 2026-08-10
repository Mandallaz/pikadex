package com.mandallaz.pikadex

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B12 — everything under `assets/` ships unconditionally in the APK, so a data drop with no
 * runtime reader (like the ~28 MB `radicalred/` dataset once committed here) silently bloats
 * every install. Guards against that class of regression rather than any one specific dataset.
 */
class AssetsFootprintTest {

    private val assetsDir = File("src/main/assets")

    @Test
    fun `no unreferenced top-level asset directory ships in the APK`() {
        val sourceRoots = listOf(File("src/main/java"), File("src/main/res"))
        val topLevelDirs = assetsDir.listFiles { f -> f.isDirectory }.orEmpty()
        topLevelDirs.forEach { dir ->
            val referenced = sourceRoots.any { root ->
                root.walkTopDown().any { it.isFile && it.extension in setOf("kt", "xml") && it.readText().contains(dir.name) }
            }
            assertTrue("assets/${dir.name} is not referenced by any source file — remove it or wire it up", referenced)
        }
    }

    @Test
    fun `bundled assets stay under 5 MB total`() {
        val totalBytes = assetsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertFalse(
            "assets/ totals ${totalBytes / (1024 * 1024)} MB — every user downloads this on install",
            totalBytes > 5 * 1024 * 1024,
        )
    }
}
