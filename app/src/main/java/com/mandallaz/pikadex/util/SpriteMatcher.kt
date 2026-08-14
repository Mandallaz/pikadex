package com.mandallaz.pikadex.util

import android.content.Context
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Platform-agnostic pixel reader interface. This allows testing the sprite recognition
 * logic in plain JVM unit tests where android.graphics.Bitmap is not available.
 */
interface PixelSource {
    val width: Int
    val height: Int
    fun getPixel(x: Int, y: Int): Int // Returns ARGB format (32-bit int)
}

/**
 * On-device ML Kit-style sprite-recognition primitive. Matches a cropped sprite image
 * (as PixelSource) against the precomputed 8x8 signatures of all Pokémon species.
 */
object SpriteMatcher {

    data class MatchResult(
        val pokemonId: Int,
        val score: Double // Distance metric: lower is better (0.0 is perfect match)
    )

    data class SpriteSignature(
        val id: Int,
        val pixels: IntArray // 64 pixels of ARGB
    )

    class BackgroundInfo(
        val hasBackground: Boolean,
        val r: Int,
        val g: Int,
        val b: Int
    )

    private var signatures: List<SpriteSignature>? = null

    /**
     * Loads the signatures database from a gzipped binary input stream.
     */
    fun loadSignatures(inputStream: InputStream) {
        val gzip = GZIPInputStream(inputStream)
        val list = mutableListOf<SpriteSignature>()
        val buffer = ByteArray(258)
        while (true) {
            var read = 0
            while (read < 258) {
                val r = gzip.read(buffer, read, 258 - read)
                if (r == -1) break
                read += r
            }
            if (read < 258) break
            val id = ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
            val pixels = IntArray(64)
            for (i in 0 until 64) {
                val offset = 2 + i * 4
                val a = buffer[offset].toInt() and 0xFF
                val r = buffer[offset + 1].toInt() and 0xFF
                val g = buffer[offset + 2].toInt() and 0xFF
                val b = buffer[offset + 3].toInt() and 0xFF
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            list.add(SpriteSignature(id, pixels))
        }
        signatures = list
    }

    /**
     * Lazily initializes and loads the sprite signatures from the app assets.
     */
    fun initIfNeeded(context: Context) {
        if (signatures != null) return
        try {
            context.assets.open("sprite_signatures.bin").use { stream ->
                loadSignatures(stream)
            }
        } catch (e: Exception) {
            // Fallback or log if asset not found
            e.printStackTrace()
        }
    }

    /**
     * Clears loaded signatures (useful for testing).
     */
    fun clearSignatures() {
        signatures = null
    }

    /**
     * Returns the loaded signatures or empty list.
     */
    fun getSignatures(): List<SpriteSignature> {
        return signatures.orEmpty()
    }

    /**
     * Downsamples any arbitrary PixelSource to an 8x8 color grid using area-averaging
     * to preserve shape, color, and transparency features accurately.
     */
    fun downsample(source: PixelSource): IntArray {
        val targetWidth = 8
        val targetHeight = 8
        val pixels = IntArray(64)
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val startX = (x * source.width) / targetWidth
                val endX = (((x + 1) * source.width) / targetWidth).coerceAtMost(source.width)
                val startY = (y * source.height) / targetHeight
                val endY = (((y + 1) * source.height) / targetHeight).coerceAtMost(source.height)

                var sumA = 0
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0

                for (sy in startY until endY) {
                    for (sx in startX until endX) {
                        val p = source.getPixel(sx, sy)
                        val a = (p ushr 24) and 0xFF
                        val r = (p ushr 16) and 0xFF
                        val g = (p ushr 8) and 0xFF
                        val b = p and 0xFF

                        sumA += a
                        sumR += r
                        sumG += g
                        sumB += b
                        count++
                    }
                }

                if (count > 0) {
                    val avgA = sumA / count
                    val avgR = sumR / count
                    val avgG = sumG / count
                    val avgB = sumB / count
                    pixels[y * targetWidth + x] = (avgA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                } else {
                    pixels[y * targetWidth + x] = 0
                }
            }
        }
        return pixels
    }

    /**
     * Examines the corners of the 8x8 grid to detect if there is a solid-colored or transparent
     * background. If the corners are transparent or similar, we treat that color as the background.
     */
    fun detectBackground(pixels: IntArray): BackgroundInfo {
        val corners = intArrayOf(
            pixels[0],   // top-left
            pixels[7],   // top-right
            pixels[56],  // bottom-left
            pixels[63]   // bottom-right
        )

        // If at least 2 corners are highly transparent, assume transparent background
        val transparentCount = corners.count { ((it ushr 24) and 0xFF) < 50 }
        if (transparentCount >= 2) {
            return BackgroundInfo(hasBackground = false, 0, 0, 0)
        }

        // Compute average of the corners that are not fully transparent
        var sumR = 0
        var sumG = 0
        var sumB = 0
        var count = 0
        for (c in corners) {
            val a = (c ushr 24) and 0xFF
            if (a >= 50) {
                sumR += (c ushr 16) and 0xFF
                sumG += (c ushr 8) and 0xFF
                sumB += c and 0xFF
                count++
            }
        }

        if (count < 2) {
            return BackgroundInfo(hasBackground = false, 0, 0, 0)
        }

        val avgR = sumR / count
        val avgG = sumG / count
        val avgB = sumB / count

        // Check variance to see if they are consistent
        var maxDiff = 0
        for (c in corners) {
            val a = (c ushr 24) and 0xFF
            if (a >= 50) {
                val cr = (c ushr 16) and 0xFF
                val cg = (c ushr 8) and 0xFF
                val cb = c and 0xFF
                val diff = Math.abs(cr - avgR) + Math.abs(cg - avgG) + Math.abs(cb - avgB)
                if (diff > maxDiff) {
                    maxDiff = diff
                }
            }
        }

        // If variance is too high, it's not a solid background
        if (maxDiff > 60) {
            return BackgroundInfo(hasBackground = false, 0, 0, 0)
        }

        return BackgroundInfo(hasBackground = true, avgR, avgG, avgB)
    }

    /**
     * Determines if a pixel is a background pixel (transparent or matching the background color).
     */
    fun isBackgroundPixel(pixel: Int, bg: BackgroundInfo): Boolean {
        val a = (pixel ushr 24) and 0xFF
        if (a < 50) return true
        if (!bg.hasBackground) return false
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val diff = Math.abs(r - bg.r) + Math.abs(g - bg.g) + Math.abs(b - bg.b)
        return diff < 40 // threshold distance for solid color match
    }

    /**
     * Calculates the distance/difference score between input 8x8 pixels and reference 8x8 pixels.
     * Lower is better. Perfect match is 0.0.
     */
    fun calculateDistance(inputPixels: IntArray, refPixels: IntArray, bg: BackgroundInfo): Double {
        var totalDiff = 0.0
        var compareCount = 0

        for (i in 0 until 64) {
            val inputPixel = inputPixels[i]
            val refPixel = refPixels[i]

            val refAlpha = (refPixel ushr 24) and 0xFF
            val refIsBg = refAlpha < 50
            val inputIsBg = isBackgroundPixel(inputPixel, bg)

            if (refIsBg && inputIsBg) {
                // Both background: perfect match
                totalDiff += 0.0
            } else if (refIsBg != inputIsBg) {
                // Shape mismatch penalty (foreground vs background)
                totalDiff += 255.0 * 3.0 // maximum RGB difference penalty
                compareCount++
            } else {
                // Both are foreground: compare colors
                val r1 = (inputPixel ushr 16) and 0xFF
                val g1 = (inputPixel ushr 8) and 0xFF
                val b1 = inputPixel and 0xFF

                val r2 = (refPixel ushr 16) and 0xFF
                val g2 = (refPixel ushr 8) and 0xFF
                val b2 = refPixel and 0xFF

                val rDiff = Math.abs(r1 - r2)
                val gDiff = Math.abs(g1 - g2)
                val bDiff = Math.abs(b1 - b2)

                totalDiff += (rDiff + gDiff + bDiff).toDouble()
                compareCount++
            }
        }

        return if (compareCount > 0) {
            totalDiff / 64.0
        } else {
            totalDiff
        }
    }

    /**
     * Matches a PixelSource crop against loaded reference signatures.
     * Returns a sorted list of MatchResults, with the best match first.
     */
    fun matchSprite(source: PixelSource, context: Context? = null): List<MatchResult> {
        context?.let { initIfNeeded(it) }
        val sigs = signatures ?: return emptyList()

        val input8x8 = downsample(source)
        val bgInfo = detectBackground(input8x8)

        return sigs.map { sig ->
            val score = calculateDistance(input8x8, sig.pixels, bgInfo)
            MatchResult(sig.id, score)
        }.sortedBy { it.score }
    }
}
