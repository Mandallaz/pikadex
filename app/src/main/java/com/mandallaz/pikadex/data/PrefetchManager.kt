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
import com.mandallaz.pikadex.util.UrlValidator
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
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf

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

internal const val UNIQUE_WORK_NAME = "pikadex_prefetch_work"

/** Politeness pause after each unit finishes, before its slot is handed to the next queued one —
 *  this is a bulk download of ~1300 entries' worth of data against public APIs, not a single
 *  user-triggered fetch. Paid per-unit rather than per-wave so it slows the steady-state request
 *  rate without reintroducing a wait for the slowest unit in a batch. */
private const val PREFETCH_UNIT_DELAY_MILLIS = 35L

/**
 * Runs the prefetch flow via WorkManager, so it survives process death.
 */
object PrefetchManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null
    private lateinit var appContext: Context

    private val _state = MutableStateFlow<PrefetchState>(PrefetchState.Idle)
    val state: StateFlow<PrefetchState> = _state.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        observeJob?.cancel()
        observeJob = scope.launch {
            try {
                WorkManager.getInstance(appContext)
                    .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
                    .collect { list ->
                        val workInfo = list.firstOrNull()
                        if (workInfo == null) {
                            _state.value = PrefetchState.Idle
                        } else {
                            val mappedState = when (workInfo.state) {
                                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                                    PrefetchState.Running(done = 0, total = 0, phaseRes = PrefetchTier.ESSENTIALS.labelRes)
                                }
                                WorkInfo.State.RUNNING -> {
                                    val progress = workInfo.progress
                                    val done = progress.getInt("done", 0)
                                    val total = progress.getInt("total", 0)
                                    val phaseRes = progress.getInt("phaseRes", PrefetchTier.ESSENTIALS.labelRes)
                                    PrefetchState.Running(done = done, total = total, phaseRes = phaseRes)
                                }
                                WorkInfo.State.SUCCEEDED -> {
                                    val failed = workInfo.outputData.getInt("failed", 0)
                                    PrefetchState.Finished(failed = failed)
                                }
                                WorkInfo.State.FAILED -> {
                                    val messageRes = workInfo.outputData.getInt("messageRes", R.string.settings_prefetch_error_network)
                                    PrefetchState.Failed(messageRes = messageRes)
                                }
                                WorkInfo.State.CANCELLED -> {
                                    PrefetchState.Idle
                                }
                            }
                            _state.value = mappedState
                        }
                    }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun start(context: Context, repository: PokedexRepositoryApi, tiers: List<PrefetchTier>) {
        if (tiers.isEmpty()) {
            cancel()
            return
        }
        val workRequest = buildWorkRequest(tiers)
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    internal fun buildWorkRequest(tiers: List<PrefetchTier>): OneTimeWorkRequest {
        val data = workDataOf("tiers" to tiers.map { it.name }.toTypedArray())
        val builder = OneTimeWorkRequestBuilder<PrefetchWorker>()
            .setInputData(data)
        if (PrefetchSettings.wifiOnlyEnabled.value) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            builder.setConstraints(constraints)
        }
        return builder.build()
    }

    fun cancel() {
        val ctx = if (::appContext.isInitialized) appContext else null
        if (ctx != null) {
            WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
        _state.value = PrefetchState.Idle
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

    internal suspend fun buildUnits(
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
            val ids = repository.masterIdByName().values
            imagePrefetchUnits(context, ids.flatMap { id ->
                listOf(Sprites.officialArtworkUrl(id), Sprites.defaultSpriteUrl(id))
            })
        }
        PrefetchTier.SPRITES_EXTRA -> {
            val ids = repository.masterIdByName().values
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
        PrefetchTier.CRIES -> repository.masterIdByName().values.map { id ->
            suspend { CryCache.download(context, id, Cries.latestCryUrl(id)); Unit }
        }
    }

    /** One download unit per [urls], via Coil's disk cache — shared by [PrefetchTier.SPRITES] and
     *  [PrefetchTier.SPRITES_EXTRA]. */
    private fun imagePrefetchUnits(context: Context, urls: List<String>): List<suspend () -> Unit> {
        val imageLoader = context.imageLoader
        return urls.filter { UrlValidator.isValid(it) }.map { url ->
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
    onProgress: suspend (done: Int) -> Unit
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
