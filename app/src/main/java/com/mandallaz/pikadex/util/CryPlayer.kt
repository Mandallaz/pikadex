package com.mandallaz.pikadex.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mandallaz.pikadex.util.UrlValidator

/** Thin wrapper around [MediaPlayer] for F34's cry playback — the one place in the app that plays
 *  audio, so there's no existing player abstraction to reuse. Owned by `PokedexDetailViewModel`,
 *  released from its `onCleared()` so playback doesn't outlive the screen or leak across a swipe
 *  to the next Pokémon (same per-screen-resource concern issue #7 already raised elsewhere). */
class CryPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    // Lazy, not eager: a plain JVM unit test constructing a ViewModel that owns a CryPlayer never
    // calls play(), but an eager Handler(Looper.getMainLooper()) here would still run at
    // construction time and crash immediately — Looper isn't mocked outside Robolectric/instrumentation.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Same eager-construction concern as mainHandler above — android.media.AudioAttributes.Builder
    // isn't mocked in a plain JVM unit test either.
    private val audioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    /** Plays [source] (a local file path or a URL — [MediaPlayer.setDataSource] accepts either).
     *  On failure, retries once with [fallbackSource] if given (e.g. the legacy cry when the latest
     *  one 404s, a real coverage gap rather than a bug) — a silent fallback beats surfacing an
     *  error for what's a minor flourish, not core functionality.
     *
     *  issue #65 (B19) — the error listener used to call `play(fallbackSource)` directly, i.e.
     *  `release()` on the very [MediaPlayer] instance currently executing that callback, from
     *  inside its own callback. Posting the retry to the next main-thread loop iteration avoids
     *  that unsupported re-entrant call pattern. */
    fun play(context: Context, source: String, fallbackSource: String? = null) {
        val isSourceRemote = UrlValidator.isRemoteUrl(source)
        val isSourceValid = !isSourceRemote || UrlValidator.isValid(source)

        val isFallbackRemote = fallbackSource?.let { UrlValidator.isRemoteUrl(it) } ?: false
        val isFallbackValid = fallbackSource == null || !isFallbackRemote || UrlValidator.isValid(fallbackSource)

        val cleanSource = if (isSourceValid) source else {
            if (isFallbackValid && fallbackSource != null) fallbackSource else null
        }
        val cleanFallback = if (isSourceValid) {
            if (isFallbackValid) fallbackSource else null
        } else {
            null
        }

        if (cleanSource == null) {
            _isPlaying.value = false
            return
        }

        release()
        requestAudioFocus(context)
        _isPlaying.value = true
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setOnPreparedListener { it.start() }
            // B31 — abandons focus here too, not just from release(): without this, focus stayed
            // held (ducking any other app's audio, e.g. music) for as long as this CryPlayer
            // instance lived — i.e. while the user kept browsing the detail screen — instead of
            // being released right after the ~1s cry actually finished.
            setOnCompletionListener {
                _isPlaying.value = false
                abandonAudioFocus()
            }
            setOnErrorListener { _, _, _ ->
                if (cleanFallback != null) {
                    // Not abandoned here — play(cleanFallback) immediately re-enters and calls
                    // release() (which abandons focus) then requestAudioFocus() again itself.
                    mainHandler.post { play(context, cleanFallback) }
                } else {
                    _isPlaying.value = false
                    abandonAudioFocus()
                }
                true
            }
            try {
                setDataSource(cleanSource)
                prepareAsync()
            } catch (e: Exception) {
                _isPlaying.value = false
            }
        }
    }

    private fun requestAudioFocus(context: Context) {
        val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
        audioManager = null
        audioFocusRequest = null
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
        abandonAudioFocus()
    }
}
