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

@RunWith(AndroidJUnit4::class)
class PrefetchWorkerTest {

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
    fun `doWork succeeds and returns outputData with failed count on normal completion`() = runBlocking {
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to arrayOf("FULL_DETAIL")))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(workDataOf("failed" to 0)), result)
    }

    @Test
    fun `doWork returns failure when inputData missing tiers key`() = runBlocking {
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf())
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns success when tiers list is empty`() = runBlocking {
        val worker = TestListenableWorkerBuilder<PrefetchWorker>(context)
            .setInputData(workDataOf("tiers" to emptyArray<String>()))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns failure with network error resource when repository throws exception`() = runBlocking {
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
}
