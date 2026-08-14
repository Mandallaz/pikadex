package com.mandallaz.pikadex.util

import org.junit.Ignore
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/**
 * Generator test used to build the compact reference database of sprite signatures.
 * It is marked with @Ignore so it doesn't run during normal build/CI pipelines, but can
 * be run manually when the signature database needs to be created or updated.
 */
class SpriteSignatureGeneratorTest {

    class BufferedImagePixelSource(private val image: java.awt.image.BufferedImage) : PixelSource {
        override val width: Int get() = image.width
        override val height: Int get() = image.height
        override fun getPixel(x: Int, y: Int): Int = image.getRGB(x, y)
    }

    @Test
    @Ignore("Run manually to generate the sprite signatures binary asset.")
    fun generateSignatures() {
        val destFile = File("src/main/assets/sprite_signatures.bin")
        destFile.parentFile.mkdirs()

        println("Starting signature generation for Pokémon 1 to 1025...")

        val maxId = 1025
        val signatures = Array<IntArray?>(maxId + 1) { null }

        // Fetch sprites concurrently to speed up the process
        val threads = mutableListOf<Thread>()
        val chunkSize = 50
        for (start in 1..maxId step chunkSize) {
            val end = (start + chunkSize - 1).coerceAtMost(maxId)
            val t = thread {
                for (id in start..end) {
                    val urlStr = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"
                    try {
                        val url = URL(urlStr)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        if (conn.responseCode == 200) {
                            conn.inputStream.use { stream ->
                                val img = ImageIO.read(stream)
                                if (img != null) {
                                    val source = BufferedImagePixelSource(img)
                                    val downsampled = SpriteMatcher.downsample(source)
                                    signatures[id] = downsampled
                                    if (id % 100 == 0 || id == maxId) {
                                        println("Processed sprite $id/$maxId")
                                    }
                                }
                            }
                        } else {
                            System.err.println("Failed to fetch sprite $id: HTTP ${conn.responseCode}")
                        }
                    } catch (e: Exception) {
                        System.err.println("Error fetching sprite $id: ${e.message}")
                    }
                }
            }
            threads.add(t)
        }

        threads.forEach { it.join() }

        println("Writing signatures to ${destFile.absolutePath}...")
        GZIPOutputStream(FileOutputStream(destFile)).use { gzip ->
            BufferedOutputStream(gzip).use { bos ->
                val buffer = ByteArray(258)
                for (id in 1..maxId) {
                    val sig = signatures[id] ?: continue
                    buffer[0] = ((id ushr 8) and 0xFF).toByte()
                    buffer[1] = (id and 0xFF).toByte()
                    for (i in 0 until 64) {
                        val p = sig[i]
                        val offset = 2 + i * 4
                        buffer[offset] = ((p ushr 24) and 0xFF).toByte()     // A
                        buffer[offset + 1] = ((p ushr 16) and 0xFF).toByte() // R
                        buffer[offset + 2] = ((p ushr 8) and 0xFF).toByte()  // G
                        buffer[offset + 3] = (p and 0xFF).toByte()           // B
                    }
                    bos.write(buffer)
                }
            }
        }

        println("Generation complete! Output size: ${destFile.length()} bytes")
    }
}
