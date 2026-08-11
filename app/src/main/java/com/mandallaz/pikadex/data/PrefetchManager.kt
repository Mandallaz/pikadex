package com.mandallaz.pikadex.data

import android.content.Context
import android.net.ConnectivityManager
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

    // B25 — every state update below is tagged with the run id active when its coroutine started
    // and checked against the current one before publishing. Two races this closes:
    //  1. Without it, a unit that finishes right as cancel() runs could still call onProgress
    //     after cancel() already published Idle, making the screen show a stale Running(...) with
    //     a Cancel button for a run that no longer exists.
    //  2. It also makes it safe for start() to cancel the previous job *synchronously*, in its own
    //     body rather than from inside the new coroutine (see start()'s own doc) — even if the two
    //     jobs' cancellation/dispatch interleave unexpectedly, only the run whose id still matches
    //     currentRunId is allowed to touch [_state].
    private val currentRunId = AtomicInteger(0)
    private fun isCurrentRun(runId: Int) = runId == currentRunId.get()

    private val _state = MutableStateFlow<PrefetchState>(PrefetchState.Idle)
    val state: StateFlow<PrefetchState> = _state.asStateFlow()

    /** Cancels whatever's running (if anything) and starts fetching [tiers] in order. A fresh call
     *  supersedes an in-flight one rather than queuing behind it — same "latest request wins"
     *  behavior as every other tracked job in this codebase.
     *
     *  The previous job is cancelled synchronously, here, before this function returns — not from
     *  inside the new coroutine's own body. If it were cancelled from in there instead, a `cancel()`
     *  call landing between `start()` returning and the new coroutine actually being dispatched
     *  would cancel the *new* job before it ever got a chance to cancel the old one, orphaning the
     *  old download with nothing left able to stop it. The new coroutine still joins the old one
     *  (waits for it to actually finish unwinding) before starting its own work, so the old
     *  coroutine's in-flight `_state.update { Running(...) }` can't land after this job has already
     *  published its own state. */
    fun start(context: Context, repository: PokedexRepositoryApi, tiers: List<PrefetchTier>) {
        val previousJob = job
        previousJob?.cancel()
        val runId = currentRunId.incrementAndGet()
        if (tiers.isEmpty()) {
            job = null
            return
        }
        val appContext = context.applicationContext
        job = scope.launch {
            previousJob?.join()
            try {
                var totalFailed = 0
                tiers.forEach { tier ->
                    if (!isCurrentRun(runId)) return@launch
                    if (abortIfWentMetered(appContext, runId)) return@launch
                    _state.update { PrefetchState.Running(done = 0, total = 0, phaseRes = tier.labelRes) }
                    val units = buildUnits(tier, appContext, repository)
                    if (!isCurrentRun(runId)) return@launch
                    _state.update { PrefetchState.Running(done = 0, total = units.size, phaseRes = tier.labelRes) }
                    totalFailed += runPrefetchBatch(units, PREFETCH_CONCURRENCY) { done ->
                        if (isCurrentRun(runId)) {
                            if (!abortIfWentMetered(appContext, runId)) {
                                _state.update { PrefetchState.Running(done = done, total = units.size, phaseRes = tier.labelRes) }
                            }
                        }
                    }
                }
                if (isCurrentRun(runId)) _state.update { PrefetchState.Finished(totalFailed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentRun(runId)) _state.update { PrefetchState.Failed(R.string.settings_prefetch_error_network) }
            }
        }
    }

    /** B26 — the Wi-Fi-only guard used to be checked once, at the moment the user tapped
     *  "Prefetch now"; walking out of Wi-Fi range mid-run silently kept going over cellular, which
     *  is exactly what [PrefetchSettings.wifiOnlyEnabled]'s own description promises won't happen.
     *  Called once per tier boundary and once per completed unit (from the `onProgress` callback
     *  below) — a reasonable polling granularity for a run that can take minutes, without adding a
     *  full `ConnectivityManager.NetworkCallback` registration/lifecycle. Publishes the same
     *  [PrefetchState.Failed] path [SettingsViewModel.startPrefetch]'s own pre-flight check uses,
     *  then self-cancels: the surrounding `catch (e: CancellationException) { throw e }` rethrows
     *  without touching `_state` again, so the message set here survives. */
    private fun abortIfWentMetered(context: Context, runId: Int): Boolean {
        if (!PrefetchSettings.wifiOnlyEnabled.value) return false
        if (!isActiveNetworkMetered(context)) return false
        if (!isCurrentRun(runId)) return false
        _state.update { PrefetchState.Failed(R.string.settings_prefetch_error_wifi_required) }
        job?.cancel()
        return true
    }

    fun cancel() {
        currentRunId.incrementAndGet()
        job?.cancel()
        _state.update { PrefetchState.Idle }
    }

    /** F63 — [SettingsViewModel.startPrefetch]'s guard reports through the same [PrefetchState]
     *  the actual download uses, so the screen shows this exactly like any other prefetch failure
     *  (with its own "Retry" affordance) rather than needing a separate error channel. */
    fun reportWifiRequired() {
        _state.update { PrefetchState.Failed(R.string.settings_prefetch_error_wifi_required) }
    }

    /** F63 — the Settings screen checks this before starting a download, so it can warn (or block,
     *  per [PrefetchSettings.wifiOnlyEnabled]) rather than silently spending mobile data on what
     *  can be a 50-300MB+ run. `isActiveNetworkMetered` covers both cellular and a metered hotspot
     *  — a plain "is this Wi-Fi" check would miss the latter. */
    fun isActiveNetworkMetered(context: Context): Boolean = meteredCheck(context)

    /** F72 — the real check, swappable so tests (e.g. [SettingsViewModel]'s own) can simulate a
     *  metered/unmetered network without a real [ConnectivityManager] — there's no lightweight JVM
     *  test double for one otherwise. */
    internal var meteredCheck: (Context) -> Boolean = { context ->
        val connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.isActiveNetworkMetered ?: false
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
            // B33 — TypeBadge renders these on every screen (detail header, matchup cards,
            // suggestion tiles, team chips) via Coil, but no tier ever fetched them, so a fully
            // offline device showed blank badges for any type not yet viewed. Bundled with
            // Essentials since it's the same "small, always-useful" spirit as the rest of this
            // tier, not a separate opt-in.
            val typeIconUrls = TypeIds.standardTypeNames.map { type -> Sprites.typeIconUrl(TypeIds.of(type)) }
            addAll(imagePrefetchUnits(context, typeIconUrls))
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
                    // B32 — shinySpriteUrl dropped: no composable ever requests it.
                    // PokemonArtwork's shiny chain is showdownUrl -> shinyOfficialArtworkUrl ->
                    // officialArtworkUrl -> defaultSpriteUrl, and PokemonSprite (evolution/team
                    // thumbnails) has no shiny mode at all — this URL was pure wasted bandwidth
                    // and wasted Coil cache space.
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
