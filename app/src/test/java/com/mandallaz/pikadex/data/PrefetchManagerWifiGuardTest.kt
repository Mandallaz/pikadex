package com.mandallaz.pikadex.data

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import com.mandallaz.pikadex.data.repository.PokemonDetailBundle
import com.mandallaz.pikadex.data.repository.fakePokemonDto
import com.mandallaz.pikadex.data.repository.fakePokemonSpeciesDto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B26 / B44 — test Wi-Fi-only guard in [PrefetchWorker] by executing doWork against test state.
 */
@RunWith(AndroidJUnit4::class)
class PrefetchManagerWifiGuardTest {

    private lateinit var context: Context
    private lateinit var fakeRepo: FakePokedexRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeRepo = FakePokedexRepository().apply {
            masterList = listOf(NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"))
            detailBundle = PokemonDetailBundle(
                fakePokemonDto(),
                fakePokemonSpeciesDto(),
                null
            )
        }
        AppContainer.repository = fakeRepo
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
    fun `the tier loop checks the metered guard at tier boundary and aborts on metered network`() = runBlocking {
        PrefetchSettings.setWifiOnlyEnabled(true)
        PrefetchManager.meteredCheck = { true }

        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("FULL_DETAIL")))
            .build()

        val result = worker.doWork()

        assertEquals(
            ListenableWorker.Result.failure(workDataOf("messageRes" to R.string.settings_prefetch_error_wifi_required)),
            result
        )
    }

    @Test
    fun `the per-unit progress callback checks metered guard mid-run and aborts when network becomes metered`() = runBlocking {
        PrefetchSettings.setWifiOnlyEnabled(true)
        var checkCount = 0
        PrefetchManager.meteredCheck = {
            checkCount++
            // Allow initial tier boundaries to pass unmetered, but return true when callback runs mid-batch
            checkCount > 2
        }

        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("FULL_DETAIL")))
            .build()

        val result = worker.doWork()

        assertEquals(
            ListenableWorker.Result.failure(workDataOf("messageRes" to R.string.settings_prefetch_error_wifi_required)),
            result
        )
    }

    @Test
    fun `unmetered network allows prefetch to proceed and complete`() = runBlocking {
        PrefetchSettings.setWifiOnlyEnabled(true)
        PrefetchManager.meteredCheck = { false }

        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("FULL_DETAIL")))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(workDataOf("failed" to 0)), result)
    }

    @Test
    fun `disabled wifi-only setting allows prefetch to complete on metered network`() = runBlocking {
        PrefetchSettings.setWifiOnlyEnabled(false)
        PrefetchManager.meteredCheck = { true }

        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("FULL_DETAIL")))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(workDataOf("failed" to 0)), result)
    }
}
