package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class SpriteMatcherTest {

    class MockPixelSource(
        override val width: Int,
        override val height: Int,
        private val generator: (x: Int, y: Int) -> Int
    ) : PixelSource {
        override fun getPixel(x: Int, y: Int): Int = generator(x, y)
    }

    @Test
    fun `downsample area-averaging preserves exact grid values`() {
        // Create a 16x16 mock image with solid white on the left half and black on the right half
        val source = MockPixelSource(16, 16) { x, _ ->
            if (x < 8) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }

        val downsampled = SpriteMatcher.downsample(source)
        assertEquals(64, downsampled.size)

        // The left 4 columns should be white, and the right 4 columns should be black
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val color = downsampled[y * 8 + x]
                if (x < 4) {
                    assertEquals("White pixel at ($x,$y)", 0xFFFFFFFF.toInt(), color)
                } else {
                    assertEquals("Black pixel at ($x,$y)", 0xFF000000.toInt(), color)
                }
            }
        }
    }

    @Test
    fun `detectBackground accurately identifies transparent and colored backgrounds`() {
        // Transparent background (all pixels transparent)
        val transparentGrid = IntArray(64) { 0x00000000 }
        val transparentBg = SpriteMatcher.detectBackground(transparentGrid)
        assertFalse(transparentBg.hasBackground)

        // Solid colored white background (fully opaque)
        val whiteGrid = IntArray(64) { 0xFFFFFFFF.toInt() }
        // Put some foreground shape in the middle
        for (y in 2..5) {
            for (x in 2..5) {
                whiteGrid[y * 8 + x] = 0xFFFF0000.toInt() // Red center
            }
        }
        val whiteBg = SpriteMatcher.detectBackground(whiteGrid)
        assertTrue(whiteBg.hasBackground)
        assertEquals(255, whiteBg.r)
        assertEquals(255, whiteBg.g)
        assertEquals(255, whiteBg.b)

        // Check if red center is not treated as background
        assertFalse(SpriteMatcher.isBackgroundPixel(0xFFFF0000.toInt(), whiteBg))
        // Check if white pixel is treated as background
        assertTrue(SpriteMatcher.isBackgroundPixel(0xFFFFFFFF.toInt(), whiteBg))
    }

    @Test
    fun `calculateDistance matches similar shapes and penalizes mismatches`() {
        // Transparent background
        val bgInfo = SpriteMatcher.BackgroundInfo(hasBackground = false, 0, 0, 0)

        // Reference: a simple red square in the middle of 8x8 grid, others transparent
        val refPixels = IntArray(64) { 0x00000000 }
        for (y in 2..5) {
            for (x in 2..5) {
                refPixels[y * 8 + x] = 0xFFFF0000.toInt()
            }
        }

        // Input 1: Perfect match
        val perfectInput = refPixels.clone()
        val perfectDist = SpriteMatcher.calculateDistance(perfectInput, refPixels, bgInfo)
        assertEquals(0.0, perfectDist, 1e-5)

        // Input 2: Same shape, slight color difference (red vs slightly lighter red)
        val colorDiffInput = refPixels.clone()
        for (y in 2..5) {
            for (x in 2..5) {
                colorDiffInput[y * 8 + x] = 0xFFEE0000.toInt() // R=238 instead of 255
            }
        }
        val colorDiffDist = SpriteMatcher.calculateDistance(colorDiffInput, refPixels, bgInfo)
        assertTrue("Slight color difference should have small score", colorDiffDist > 0.0)
        assertTrue("Slight color difference should be under 10.0", colorDiffDist < 10.0)

        // Input 3: Completely different shape (e.g. background where foreground should be, and vice versa)
        val mismatchInput = IntArray(64) { 0x00000000 }
        mismatchInput[0] = 0xFFFF0000.toInt() // only one foreground pixel in corner
        val mismatchDist = SpriteMatcher.calculateDistance(mismatchInput, refPixels, bgInfo)
        assertTrue("Completely different shape should have massive score", mismatchDist > 100.0)
    }

    @Test
    fun `signatures load and match successfully from local asset database`() {
        // Ensure the generated binary asset file exists
        val binFile = File("src/main/assets/sprite_signatures.bin")
        assertTrue("sprite_signatures.bin must exist in main assets", binFile.exists())

        // Load signatures
        SpriteMatcher.clearSignatures()
        FileInputStream(binFile).use { fis ->
            SpriteMatcher.loadSignatures(fis)
        }

        val signatures = SpriteMatcher.getSignatures()
        assertTrue("Should have loaded more than 1000 signatures", signatures.size >= 1000)

        // Pick one signature to act as our mock input, e.g. Pikachu (ID 25)
        val pikachuSig = signatures.firstOrNull { it.id == 25 }
        assertNotNull("Pikachu signature should exist in database", pikachuSig)

        // Create a MockPixelSource from Pikachu's signature pixels
        val source = MockPixelSource(8, 8) { x, y ->
            pikachuSig!!.pixels[y * 8 + x]
        }

        // Match against the database
        val results = SpriteMatcher.matchSprite(source)
        assertTrue("Should return matches", results.isNotEmpty())

        // The best match should be exactly Pikachu (ID 25) with a score of 0.0 (perfect match)
        val bestMatch = results[0]
        assertEquals(25, bestMatch.pokemonId)
        assertEquals(0.0, bestMatch.score, 1e-5)
    }
}
