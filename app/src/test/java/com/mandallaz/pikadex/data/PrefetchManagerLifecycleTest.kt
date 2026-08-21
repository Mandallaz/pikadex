package com.mandallaz.pikadex.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [PrefetchManager.start]/[PrefetchManager.cancel]/[PrefetchManager.reportWifiRequired]/
 * [PrefetchManager.isActiveNetworkMetered] had no coverage at all — every existing Prefetch test
 * either goes through [PrefetchWorker] directly ([PrefetchManagerWifiGuardTest]) or checks pure
 * construction ([PrefetchManagerWorkRequestTest]/[PrefetchBatchTest]/[PrefetchTierContentTest]),
 * never these entry points a real Settings screen actually calls.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PrefetchManagerLifecycleTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        PrefetchManager.init(context)
        // WorkManagerTestInitHelper runs enqueued work synchronously — a real AppContainer.repository
        // would make real network calls the moment a test enqueues work with a non-empty tier list.
        AppContainer.repository = FakePokedexRepository()
    }

    @After
    fun tearDown() {
        AppContainer.resetRepositoryForTest()
    }

    @Test
    fun `start with no tiers cancels instead of enqueueing`() {
        PrefetchManager.start(context, AppContainer.repository, emptyList())

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(PrefetchState.Idle, PrefetchManager.state.value)
    }

    @Test
    fun `start with tiers enqueues the unique prefetch work`() {
        PrefetchManager.start(context, AppContainer.repository, listOf(PrefetchTier.ESSENTIALS))

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        assertTrue(infos.isNotEmpty())
        assertTrue(infos.none { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `cancel resets state to Idle and cancels the unique work`() {
        PrefetchManager.start(context, AppContainer.repository, listOf(PrefetchTier.ESSENTIALS))

        PrefetchManager.cancel()

        assertEquals(PrefetchState.Idle, PrefetchManager.state.value)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `reportWifiRequired sets a Failed state carrying the wifi-required message`() {
        PrefetchManager.reportWifiRequired()

        assertEquals(
            PrefetchState.Failed(R.string.settings_prefetch_error_wifi_required),
            PrefetchManager.state.value
        )
    }

    @Test
    fun `isActiveNetworkMetered delegates to the swappable meteredCheck`() {
        val originalCheck = PrefetchManager.meteredCheck
        try {
            PrefetchManager.meteredCheck = { true }
            assertTrue(PrefetchManager.isActiveNetworkMetered(context))

            PrefetchManager.meteredCheck = { false }
            assertEquals(false, PrefetchManager.isActiveNetworkMetered(context))
        } finally {
            PrefetchManager.meteredCheck = originalCheck
        }
    }
}
