package com.mandallaz.pikadex.data

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.mandallaz.pikadex.data.repository.PokedexRepository
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.TypeIds
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One optional download tier — see BACKLOG.md F13. [label] doubles as the "phase" shown in
 *  [PrefetchState.Running]. */
enum class PrefetchTier(val label: String) {
    ESSENTIALS("Essentials"),
    SPRITES("Sprites"),
    FULL_DETAIL("Full detail")
}

sealed interface PrefetchState {
    data object Idle : PrefetchState
    data class Running(val done: Int, val total: Int, val phase: String) : PrefetchState
    data class Finished(val failed: Int) : PrefetchState
    data class Failed(val message: String) : PrefetchState
}

private const val PREFETCH_CONCURRENCY = 6

/** Politeness delay between waves of [PREFETCH_CONCURRENCY] concurrent requests — this is a bulk
 *  download of ~1300 entries' worth of data against public APIs, not a single user-triggered fetch. */
private const val PREFETCH_WAVE_DELAY_MILLIS = 200L

/**
 * Runs the tiers in [PrefetchTier], sequentially, on its own long-lived [CoroutineScope] rather
 * than a `viewModelScope` — a multi-minute download must keep running after the user navigates
 * away from Settings, not get cancelled the moment `SettingsViewModel` is cleared. The one
 * deliberate exception to this app's usual "the ViewModel's scope owns the work" pattern.
 */
object PrefetchManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<PrefetchState>(PrefetchState.Idle)
    val state: StateFlow<PrefetchState> = _state.asStateFlow()

    /** Cancels whatever's running (if anything) and starts fetching [tiers] in order. A fresh call
     *  supersedes an in-flight one rather than queuing behind it — same "latest request wins"
     *  behavior as every other tracked job in this codebase. */
    fun start(context: Context, repository: PokedexRepository, tiers: List<PrefetchTier>) {
        job?.cancel()
        if (tiers.isEmpty()) return
        val appContext = context.applicationContext
        job = scope.launch {
            try {
                var totalFailed = 0
                tiers.forEach { tier ->
                    _state.update { PrefetchState.Running(done = 0, total = 0, phase = tier.label) }
                    val units = buildUnits(tier, appContext, repository)
                    _state.update { PrefetchState.Running(done = 0, total = units.size, phase = tier.label) }
                    totalFailed += runPrefetchBatch(units, PREFETCH_CONCURRENCY) { done ->
                        _state.update { PrefetchState.Running(done = done, total = units.size, phase = tier.label) }
                    }
                }
                _state.update { PrefetchState.Finished(totalFailed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { PrefetchState.Failed("Network error during prefetch. Check your connection.") }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { PrefetchState.Idle }
    }

    private suspend fun buildUnits(
        tier: PrefetchTier,
        context: Context,
        repository: PokedexRepository
    ): List<suspend () -> Unit> = when (tier) {
        PrefetchTier.ESSENTIALS -> buildList {
            add { repository.getAllBasics() }
            add { repository.getAllMoveInfo() }
            TypeIds.standardTypeNames.forEach { type -> add { repository.getTypeDetail(type) } }
            Smogon.ALL_GENERATIONS.forEach { gen -> add { repository.getSmogonTiers(gen.code) } }
        }
        PrefetchTier.SPRITES -> {
            val imageLoader = context.imageLoader
            repository.getMasterList().mapNotNull { it.id }.flatMap { id ->
                listOf(Sprites.officialArtworkUrl(id), Sprites.defaultSpriteUrl(id))
            }.map { url ->
                suspend {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        // Sprites are downloaded once and never displayed as part of this run, so
                        // there's nothing to hold in the (size-limited) memory cache for — only the
                        // disk cache is what makes them available offline later.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    imageLoader.execute(request)
                    Unit
                }
            }
        }
        PrefetchTier.FULL_DETAIL -> repository.getMasterList().map { resource ->
            suspend { repository.getPokemonDetailBundle(resource.name); Unit }
        }
    }
}

/**
 * Runs [units] in waves of [concurrency], with a [PREFETCH_WAVE_DELAY_MILLIS] pause between waves.
 * Each unit's failure is caught and counted rather than aborting the whole run — a 404 on one
 * obscure form, or one dropped connection, shouldn't cost the other ~1300 units their result.
 * [onProgress] fires once per completed unit (success or failure alike) with the running total.
 *
 * Internal rather than private: this is the one part of F13 with real unit test coverage — the
 * tier-specific wiring in [PrefetchManager.buildUnits] is manual-verification only, same as
 * [com.mandallaz.pikadex.ui.team.TeamViewModel]'s own F11 wiring.
 */
internal suspend fun runPrefetchBatch(
    units: List<suspend () -> Unit>,
    concurrency: Int,
    onProgress: (done: Int) -> Unit
): Int {
    val failed = AtomicInteger(0)
    val done = AtomicInteger(0)
    val waves = units.chunked(concurrency)
    waves.forEachIndexed { index, wave ->
        coroutineScope {
            wave.forEach { unit ->
                launch {
                    try {
                        unit()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed.incrementAndGet()
                    }
                    onProgress(done.incrementAndGet())
                }
            }
        }
        if (index < waves.lastIndex) delay(PREFETCH_WAVE_DELAY_MILLIS)
    }
    return failed.get()
}
