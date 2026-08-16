package com.mandallaz.pikadex.data

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import com.mandallaz.pikadex.data.repository.PokemonDetailBundle
import com.mandallaz.pikadex.data.repository.fakePokemonDto
import com.mandallaz.pikadex.data.repository.fakePokemonSpeciesDto
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PrefetchWorkerTest {

    private lateinit var context: Context
    private lateinit var fakeRepo: FakePokedexRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        fakeRepo = FakePokedexRepository().apply {
            masterList = listOf(NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"))
            detailBundle = PokemonDetailBundle(
                fakePokemonDto(),
                fakePokemonSpeciesDto(),
                null
            )
        }
        AppContainer.repository = fakeRepo
        PrefetchSettings.setWifiOnlyEnabled(false)
        PrefetchManager.meteredCheck = { false }
    }

    @After
    fun tearDown() {
        AppContainer.resetRepositoryForTest()
        PrefetchManager.meteredCheck = { ctx ->
            val connectivityManager = ctx.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager?.isActiveNetworkMetered ?: false
        }
    }

    @Test
    fun `doWork succeeds and returns outputData with failed count on normal completion`(): Unit = runBlocking {
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("FULL_DETAIL")))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(workDataOf("failed" to 0)), result)
    }

    @Test
    fun `doWork returns failure when inputData missing tiers key`(): Unit = runBlocking {
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf())
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns success when tiers list is empty`(): Unit = runBlocking {
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to emptyArray<String>()))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns failure with network error resource when repository throws exception`(): Unit = runBlocking {
        fakeRepo.failWith = Exception("Network connection failed")

        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("FULL_DETAIL")))
            .build()

        val result = worker.doWork()

        assertEquals(
            ListenableWorker.Result.failure(workDataOf("messageRes" to R.string.settings_prefetch_error_network)),
            result
        )
    }

    @Test
    fun `getForegroundInfo creates foregroundInfo with expected notification channel title text and action`(): Unit = runBlocking {
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("SPRITES")))
            .build()

        val foregroundInfo = worker.getForegroundInfo()

        assertEquals(PREFETCH_NOTIFICATION_ID, foregroundInfo.notificationId)
        val notification = foregroundInfo.notification
        assertEquals(PREFETCH_NOTIFICATION_CHANNEL_ID, notification.channelId)

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        assertEquals(context.getString(R.string.settings_offline_data_section), title)
        assertEquals("${context.getString(R.string.settings_tier_sprites_title)}…", text)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(PREFETCH_NOTIFICATION_CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals(context.getString(R.string.settings_offline_data_section), channel.name)

        val cancelAction = notification.actions?.firstOrNull()
        assertNotNull(cancelAction)
        assertEquals(context.getString(R.string.settings_cancel), cancelAction?.title?.toString())
    }

    @Test
    fun `createForegroundInfo formats progress text matching in-app display`(): Unit = runBlocking {
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context).build()

        val foregroundInfo = worker.createForegroundInfo(
            done = 1204,
            total = 2702,
            phaseRes = R.string.settings_tier_sprites_title
        )

        val notification = foregroundInfo.notification
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        assertEquals("Sprites — 1204/2702", text)
    }

    // B62 — the tests above only call getForegroundInfo()/createForegroundInfo() directly with
    // fixed arguments; none of them drove doWork() and checked that setForeground is actually
    // invoked with increasing progress as work proceeds, which is the entire point of promoting
    // this worker to a foreground service.
    private class RecordingForegroundUpdater : ForegroundUpdater {
        val progressValues = mutableListOf<Int>()

        override fun setForegroundAsync(context: Context, id: UUID, foregroundInfo: ForegroundInfo): ListenableFuture<Void> {
            val text = foregroundInfo.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            // createForegroundInfo formats progress as "Phase — done/total"; done is the part this
            // test cares about, total/phase text are covered by the tests above.
            text.substringAfterLast("— ").substringBefore("/").toIntOrNull()?.let { progressValues.add(it) }
            return Futures.immediateFuture(null)
        }
    }

    @Test
    fun `doWork calls setForeground with increasing progress as units complete`(): Unit = runBlocking {
        fakeRepo.masterList = listOf(
            NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"),
            NamedApiResource("ivysaur", "https://pokeapi.co/api/v2/pokemon/2/"),
            NamedApiResource("venusaur", "https://pokeapi.co/api/v2/pokemon/3/")
        )
        val foregroundUpdater = RecordingForegroundUpdater()
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("FULL_DETAIL")))
            .setForegroundUpdater(foregroundUpdater)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(workDataOf("failed" to 0)), result)
        assertTrue(
            "expected setForeground to be called at least once per completed unit, got ${foregroundUpdater.progressValues}",
            foregroundUpdater.progressValues.size >= 3
        )
        assertEquals(
            "progress must never regress once a unit completes",
            foregroundUpdater.progressValues.sorted(),
            foregroundUpdater.progressValues
        )
        assertEquals(3, foregroundUpdater.progressValues.last())
    }
}
