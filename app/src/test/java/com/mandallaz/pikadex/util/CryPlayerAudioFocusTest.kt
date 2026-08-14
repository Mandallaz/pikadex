package com.mandallaz.pikadex.util

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B31 — `play()` requested audio focus but only ever abandoned it from `release()`, which only
 * ran on the next `play()` call or `PokedexDetailViewModel.onCleared()` — not when a cry actually
 * finished playing (`setOnCompletionListener`), so background music (or any other app) stayed
 * ducked for as long as the detail screen stayed open. [CryPlayer] needs a real `MediaPlayer`/
 * `AudioManager`/`Looper` (see its own doc on why `mainHandler`/`audioAttributes` are lazy — "not
 * mocked outside Robolectric/instrumentation"), and this app has no existing androidTest
 * infrastructure for audio playback either, so this guards the source-level fix; the actual
 * ducking behavior was verified manually on-device (played music, tapped a cry, confirmed the
 * music un-ducked right after the cry finished instead of staying ducked while browsing).
 */
class CryPlayerAudioFocusTest {

    private val source = File("src/main/java/com/mandallaz/pikadex/util/CryPlayer.kt").readText()

    @Test
    fun `the completion listener abandons audio focus`() {
        val completionListener = source.substringAfter("setOnCompletionListener {").substringBefore("}\n")
        assertTrue(
            "setOnCompletionListener's body should call abandonAudioFocus()",
            completionListener.contains("abandonAudioFocus()")
        )
    }

    @Test
    fun `the terminal error path (no fallback) abandons audio focus`() {
        val errorListener = source.substringAfter("setOnErrorListener { _, _, _ ->").substringBefore("\n            }")
        val elseBranch = errorListener.substringAfter("} else {").substringBefore("}")
        assertTrue(
            "the no-fallback branch of setOnErrorListener should call abandonAudioFocus()",
            elseBranch.contains("abandonAudioFocus()")
        )
    }

    @Test
    fun `the fallback retry path does not abandon focus directly (play re-enters and handles it)`() {
        val errorListener = source.substringAfter("setOnErrorListener { _, _, _ ->").substringBefore("\n            }")
        val ifBranch = errorListener.substringBefore("} else {")
        assertTrue(
            "the fallback branch should re-enter play(), not call abandonAudioFocus() itself",
            ifBranch.contains("play(context, fallbackSource)") && !ifBranch.contains("abandonAudioFocus()")
        )
    }

    @Test
    fun `the synchronous catch block fallback retry path posts fallback`() {
        val catchBlock = source.substringAfter("catch (e: Exception) {").substringBefore("}\n        }")
        assertTrue(
            "the synchronous catch block should handle fallback retry correctly when fallbackSource is present",
            catchBlock.contains("fallbackSource != null") && catchBlock.contains("play(context, fallbackSource)")
        )
    }

    @Test
    fun `the synchronous catch block no-fallback path abandons audio focus and updates state`() {
        val catchBlock = source.substringAfter("catch (e: Exception) {").substringBefore("}\n        }")
        val elseBranch = catchBlock.substringAfter("} else {").substringBefore("}")
        assertTrue(
            "the synchronous catch block no-fallback branch should call abandonAudioFocus()",
            elseBranch.contains("abandonAudioFocus()")
        )
        assertTrue(
            "the synchronous catch block no-fallback branch should set isPlaying to false",
            elseBranch.contains("_isPlaying.value = false")
        )
    }
}
