package com.mandallaz.pikadex.data

import android.content.Context
import androidx.annotation.StringRes
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.repository.PokedexRepositoryApi
import com.mandallaz.pikadex.util.Cries
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.TypeIds
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One optional download tier. [labelRes] doubles as the "phase" shown in
 *  [PrefetchState.Running] — reuses the same resource as this tier's Settings toggle title. */
enum class PrefetchTier(@param:StringRes val labelRes: Int) {
    ESSENTIALS(R.string.settings_tier_essentials_title),
    SPRITES(R.string.settings_tier_sprites_title),
    // Opt-in, separate from SPRITES (issue #31): shiny + animated Showdown GIFs roughly double
    // SPRITES' own download volume, so it isn't worth defaulting on for users who never touch
    // those toggles on the detail screen.
    SPRITES_EXTRA(R.string.settings_tier_sprites_extra_title),
    FULL_DETAIL(R.string.settings_tier_full_detail_title),
    CRIES(R.string.settings_tier_cries_title)
}

sealed interface PrefetchState {
    data object Idle : PrefetchState
    data class Running(val done: Int, val total: Int, @param:StringRes val phaseRes: Int) : PrefetchState
    data class Finished(val failed: Int) : PrefetchState
    data class Failed(@param:StringRes val messageRes: Int) : PrefetchState
}

private const val PREFETCH_CONCURRENCY = 6

/** Politeness pause after each unit finishes, before its slot is handed to the next queued one —
 *  this is a bulk download of ~1300 entries' worth of data against public APIs, not a single
 *  user-triggered fetch. Paid per-unit rather than per-wave so it slows the steady-state request
 *  rate without reintroducing a wait for the slowest unit in a batch. */
private const val PREFETCH_UNIT_DELAY_MILLIS = 35L

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
     *  behavior as every other tracked job in this codebase.
     *
     *  The previous job is joined (not just cancelled) before this one starts its own work: a plain
     *  `cancel()` lets the old coroutine's in-flight `_state.update { Running(...) }` land after this
     *  job has already published its own state, which briefly shows the old tier's stale progress. */
    fun start(context: Context, repository: PokedexRepositoryApi, tiers: List<PrefetchTier>) {
        val previousJob = job
        if (tiers.isEmpty()) {
            previousJob?.cancel()
            return
        }
        val appContext = context.applicationContext
        job = scope.launch {
            previousJob?.cancelAndJoin()
            try {
                var totalFailed = 0
                tiers.forEach { tier ->
                    _state.update { PrefetchState.Running(done = 0, total = 0, phaseRes = tier.labelRes) }
                    val units = buildUnits(tier, appContext, repository)
                    _state.update { PrefetchState.Running(done = 0, total = units.size, phaseRes = tier.labelRes) }
                    totalFailed += runPrefetchBatch(units, PREFETCH_CONCURRENCY) { done ->
                        _state.update { PrefetchState.Running(done = done, total = units.size, phaseRes = tier.labelRes) }
                    }
                }
                _state.update { PrefetchState.Finished(totalFailed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { PrefetchState.Failed(R.string.settings_prefetch_error_network) }
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
        repository: PokedexRepositoryApi
    ): List<suspend () -> Unit> = when (tier) {
        PrefetchTier.ESSENTIALS -> buildList {
            add { repository.getAllBasics() }
            add { repository.getAllMoveInfo() }
            TypeIds.standardTypeNames.forEach { type -> add { repository.getTypeDetail(type) } }
            Smogon.ALL_GENERATIONS.forEach { gen -> add { repository.getSmogonTiers(gen.code) } }
        }
        PrefetchTier.SPRITES -> {
            val ids = repository.getMasterList().mapNotNull { it.id }
            imagePrefetchUnits(context, ids.flatMap { id ->
                listOf(Sprites.officialArtworkUrl(id), Sprites.defaultSpriteUrl(id))
            })
        }
        PrefetchTier.SPRITES_EXTRA -> {
            val ids = repository.getMasterList().mapNotNull { it.id }
            imagePrefetchUnits(context, ids.flatMap { id ->
                listOf(
                    Sprites.shinySpriteUrl(id),
                    Sprites.shinyOfficialArtworkUrl(id),
                    Sprites.showdownGifUrl(id),
                    Sprites.shinyShowdownGifUrl(id)
                )
            })
        }
        PrefetchTier.FULL_DETAIL -> repository.getMasterList().map { resource ->
            suspend { repository.getPokemonDetailBundle(resource.name); Unit }
        }
        // Cry URLs are built by convention (Cries.latestCryUrl), same as SPRITES above — this
        // avoids ~1300 individual REST calls just to read PokemonDto.cries.latest for each entry.
        PrefetchTier.CRIES -> repository.getMasterList().mapNotNull { it.id }.map { id ->
            suspend { CryCache.download(context, id, Cries.latestCryUrl(id)); Unit }
        }
    }

    /** One download unit per [urls], via Coil's disk cache — shared by [PrefetchTier.SPRITES] and
     *  [PrefetchTier.SPRITES_EXTRA]. */
    private fun imagePrefetchUnits(context: Context, urls: List<String>): List<suspend () -> Unit> {
        val imageLoader = context.imageLoader
        return urls.map { url ->
            suspend {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    // Images are downloaded once and never displayed as part of this run, so
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
}

/**
 * Runs [units] through a [concurrency]-permit [Semaphore]: every unit is launched up front and
 * blocks on acquiring a permit, so a slot freed by one finishing unit is immediately handed to the
 * next queued one — unlike wave-based chunking, no unit ever waits on stragglers from an earlier
 * batch that happened to start alongside it. Each unit's failure is caught and counted rather than
 * aborting the whole run — a 404 on one obscure form, or one dropped connection, shouldn't cost the
 * other ~1300 units their result. [onProgress] fires once per completed unit (success or failure
 * alike) with the running total.
 *
 * Internal rather than private: this is the one part of the prefetch system with real unit test
 * coverage — the tier-specific wiring in [PrefetchManager.buildUnits] is manual-verification only,
 * same as [com.mandallaz.pikadex.ui.team.TeamViewModel]'s own suggestion-ranking wiring.
 */
internal suspend fun runPrefetchBatch(
    units: List<suspend () -> Unit>,
    concurrency: Int,
    onProgress: (done: Int) -> Unit
): Int {
    val failed = AtomicInteger(0)
    val done = AtomicInteger(0)
    val semaphore = Semaphore(concurrency)
    coroutineScope {
        units.forEach { unit ->
            launch {
                semaphore.withPermit {
                    try {
                        unit()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed.incrementAndGet()
                    }
                    onProgress(done.incrementAndGet())
                    delay(PREFETCH_UNIT_DELAY_MILLIS)
                }
            }
        }
    }
    return failed.get()
}
