package com.mandallaz.pikadex.util

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Thin wrapper around [MediaPlayer] for F34's cry playback — the one place in the app that plays
 *  audio, so there's no existing player abstraction to reuse. Owned by `PokedexDetailViewModel`,
 *  released from its `onCleared()` so playback doesn't outlive the screen or leak across a swipe
 *  to the next Pokémon (same per-screen-resource concern BACKLOG.md F16 already raised elsewhere). */
class CryPlayer {
    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Plays [source] (a local file path or a URL — [MediaPlayer.setDataSource] accepts either).
     *  On failure, retries once with [fallbackSource] if given (e.g. the legacy cry when the latest
     *  one 404s, a real coverage gap rather than a bug) — a silent fallback beats surfacing an
     *  error for what's a minor flourish, not core functionality. */
    fun play(source: String, fallbackSource: String? = null) {
        release()
        _isPlaying.value = true
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { it.start() }
            setOnCompletionListener { _isPlaying.value = false }
            setOnErrorListener { _, _, _ ->
                if (fallbackSource != null) play(fallbackSource) else _isPlaying.value = false
                true
            }
            try {
                setDataSource(source)
                prepareAsync()
            } catch (e: Exception) {
                _isPlaying.value = false
            }
        }
    }

    fun release() {
        mediaPlayer?.apply {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            try {
                reset()
            } catch (e: Exception) {
                // Already in an error state — nothing to reset.
            }
            release()
        }
        mediaPlayer = null
        _isPlaying.value = false
    }
}
