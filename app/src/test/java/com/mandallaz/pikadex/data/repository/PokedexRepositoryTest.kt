package com.mandallaz.pikadex.data.repository

import com.mandallaz.pikadex.data.JsonDiskCache
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.PokeApiService
import com.mandallaz.pikadex.data.remote.dto.AbilityDetailDto
import com.mandallaz.pikadex.data.remote.dto.EvolutionChainDto
import com.mandallaz.pikadex.data.remote.dto.MoveDetailDto
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.NamedApiResourceList
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonFormDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSprites
import com.mandallaz.pikadex.data.remote.dto.SpeciesVariety
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * issue #18 — Kubfu's evolution chain names the *species* "urshifu", which has no bare-name
 * Pokémon resource of its own (only its named varieties `urshifu-single-strike`/
 * `urshifu-rapid-strike` do), so a plain `/pokemon/urshifu` fetch 404s. Reproduced on the emulator:
 * tapping Urshifu in Kubfu's Evolution card silently did nothing before the fix.
 */
class PokedexRepositoryTest {

    // B23 — getPokemonDetailBundle is now disk-cached (see PokedexRepository's own doc on that
    // function), so every test in this file that calls it touches JsonDiskCache.cacheDir, a
    // lateinit field normally set by JsonDiskCache.init(context) — never called in a plain JVM
    // test. Swapped to a real temp dir here, same Context-free reflection technique
    // JsonDiskCacheTest uses for its own tests.
    private lateinit var diskCacheDir: File

    @Before
    fun setUpDiskCache() {
        diskCacheDir = createTempDirectory(prefix = "pokedex-repository-test").toFile()
        val field = JsonDiskCache::class.java.getDeclaredField("cacheDir")
        field.isAccessible = true
        field.set(JsonDiskCache, diskCacheDir)
    }

    @After
    fun tearDownDiskCache() {
        diskCacheDir.deleteRecursively()
        PokeApiGraphQLDataSource.client = null
    }

    /** Serves a fixed GraphQL body to [PokeApiGraphQLDataSource.fetchAllBasics] — B62's
     *  getStatPercentile tests need real (non-mocked) `stats` data flowing through
     *  getAllBaseStats(), and that bulk fetch always goes through the hardcoded
     *  PokeApiGraphQLDataSource singleton, not the injectable [PokeApiService]. */
    private fun basicsGraphQLCallFactory(hpByName: Map<String, Int>): Call.Factory {
        val pokemonJson = hpByName.entries.joinToString(",") { (name, hp) ->
            """{"name":"$name","pokemonstats":[{"base_stat":$hp,"stat":{"name":"hp"}}],"pokemontypes":[],"pokemonspecy":null,"pokemonabilities":[]}"""
        }
        val body = """{"data":{"pokemon":[$pokemonJson]}}"""
        return Call.Factory { request ->
            object : Call {
                override fun request(): Request = request
                override fun execute(): OkHttpResponse = error("unexpected sync execute()")
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    responseCallback.onResponse(
                        this,
                        OkHttpResponse.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .message("OK")
                            .code(200)
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    )
                }
                override fun cancel() {}
                override fun isExecuted(): Boolean = true
                override fun isCanceled(): Boolean = false
                override fun timeout(): Timeout = Timeout.NONE
                override fun clone(): Call = this
            }
        }
    }

    private fun pokemon(name: String, id: Int, speciesName: String = name) = PokemonDto(
        id = id,
        name = name,
        height = 10,
        weight = 10,
        baseExperience = null,
        types = null,
        stats = null,
        abilities = null,
        moves = null,
        sprites = PokemonSprites(null, null, null),
        species = NamedApiResource(speciesName, "https://pokeapi.co/api/v2/pokemon-species/$id/")
    )

    private fun species(name: String, id: Int, varieties: List<SpeciesVariety>?) = PokemonSpeciesDto(
        id = id,
        name = name,
        evolutionChain = null,
        flavorTextEntries = null,
        genera = null,
        color = NamedApiResource("gray", "https://pokeapi.co/api/v2/pokemon-color/gray/"),
        eggGroups = null,
        generation = NamedApiResource("generation-viii", "https://pokeapi.co/api/v2/generation/8/"),
        isLegendary = false,
        isMythical = false,
        varieties = varieties
    )

    private fun notFound(): Nothing = throw HttpException(Response.error<Any>(404, "".toResponseBody(null)))

    /** Implements every [PokeApiService] method as "unexpected call" so a test only wires up what
     *  it actually exercises — a call this test didn't anticipate fails loudly instead of silently
     *  returning a meaningless default. */
    private open class UnexpectedApi : PokeApiService {
        override suspend fun getPokemonList(limit: Int, offset: Int): NamedApiResourceList = error("unexpected call")
        override suspend fun getPokemon(nameOrId: String): PokemonDto = error("unexpected call: getPokemon($nameOrId)")
        override suspend fun getPokemonSpecies(nameOrId: String): PokemonSpeciesDto = error("unexpected call: getPokemonSpecies($nameOrId)")
        override suspend fun getPokemonForm(name: String): PokemonFormDto = error("unexpected call")
        override suspend fun getEvolutionChain(id: Int): EvolutionChainDto = error("unexpected call")
        override suspend fun getType(name: String): TypeDetailDto = error("unexpected call")
        override suspend fun getTypeList(limit: Int): NamedApiResourceList = error("unexpected call")
        override suspend fun getMoveList(limit: Int, offset: Int): NamedApiResourceList = error("unexpected call")
        override suspend fun getMove(name: String): MoveDetailDto = error("unexpected call")
        override suspend fun getAbilityList(limit: Int, offset: Int): NamedApiResourceList = error("unexpected call")
        override suspend fun getAbility(name: String): AbilityDetailDto = error("unexpected call")
    }

    @Test
    fun `a species-only evolution name falls back to its default variety`() = runBlocking {
        val api = object : UnexpectedApi() {
            override suspend fun getPokemon(nameOrId: String): PokemonDto = when (nameOrId) {
                "urshifu" -> notFound()
                "urshifu-single-strike" -> pokemon("urshifu-single-strike", 892, speciesName = "urshifu")
                else -> error("unexpected call: getPokemon($nameOrId)")
            }
            override suspend fun getPokemonSpecies(nameOrId: String): PokemonSpeciesDto = when (nameOrId) {
                "urshifu" -> species(
                    "urshifu", 892,
                    varieties = listOf(
                        SpeciesVariety(isDefault = true, pokemon = NamedApiResource("urshifu-single-strike", "https://pokeapi.co/api/v2/pokemon/892/")),
                        SpeciesVariety(isDefault = false, pokemon = NamedApiResource("urshifu-rapid-strike", "https://pokeapi.co/api/v2/pokemon/10200/"))
                    )
                )
                else -> error("unexpected call: getPokemonSpecies($nameOrId)")
            }
        }

        val bundle = PokedexRepository(api).getPokemonDetailBundle("urshifu")
        assertEquals("urshifu-single-strike", bundle.pokemon.name)
    }

    @Test
    fun `an ordinary name resolves directly without a species fallback call`() = runBlocking {
        val api = object : UnexpectedApi() {
            override suspend fun getPokemon(nameOrId: String): PokemonDto =
                if (nameOrId == "pikachu") pokemon("pikachu", 25) else error("unexpected call: getPokemon($nameOrId)")
            override suspend fun getPokemonSpecies(nameOrId: String): PokemonSpeciesDto =
                if (nameOrId == "pikachu") species("pikachu", 25, varieties = null) else error("unexpected call: getPokemonSpecies($nameOrId)")
        }

        // getPokemonSpecies("pikachu") IS expected here — it's the bundle's own species lookup,
        // keyed by id afterwards. What must NOT happen is a *second* getPokemon fallback call,
        // which UnexpectedApi's default branch would fail on if the direct fetch weren't enough.
        val bundle = PokedexRepository(api).getPokemonDetailBundle("pikachu")
        assertEquals("pikachu", bundle.pokemon.name)
    }

    /** issue #64 (B18) — speciesCache had no maxSize, unlike pokemonDetailCache right beside it,
     *  so a long browse retained every species DTO fetched for the rest of the process. Fetching
     *  one more distinct species than the 200-entry bound should evict the least-recently-used
     *  entry, causing a repeat fetch of the first species on the next lookup. */
    @Test
    fun `species cache evicts the least recently used entry once its bound is exceeded`() = runBlocking {
        val speciesFetchCount = mutableMapOf<String, Int>()
        val api = object : UnexpectedApi() {
            override suspend fun getPokemon(nameOrId: String): PokemonDto {
                val id = nameOrId.removePrefix("species-").toInt()
                return pokemon(nameOrId, id)
            }
            override suspend fun getPokemonSpecies(nameOrId: String): PokemonSpeciesDto {
                speciesFetchCount.merge(nameOrId, 1, Int::plus)
                val id = nameOrId.removePrefix("species-").toInt()
                return species(nameOrId, id, varieties = null)
            }
        }
        val repository = PokedexRepository(api)

        // One more distinct species than the cache's 200-entry bound.
        repeat(201) { i -> repository.getPokemonDetailBundle("species-$i") }
        assertEquals(1, speciesFetchCount["species-0"])

        // B23 — getPokemonDetailBundle is now also disk-cached, and a disk hit would serve
        // "species-0" without ever touching speciesCache or the API, masking the in-memory LRU
        // eviction this test exists to prove. Clearing the disk cache isolates that behavior.
        JsonDiskCache.clear()

        repository.getPokemonDetailBundle("species-0")
        assertEquals(2, speciesFetchCount["species-0"])
    }

    @Test
    fun `a genuinely nonexistent name still fails rather than silently returning nothing`() = runBlocking {
        val api = object : UnexpectedApi() {
            override suspend fun getPokemon(nameOrId: String): PokemonDto = notFound()
            override suspend fun getPokemonSpecies(nameOrId: String): PokemonSpeciesDto = notFound()
        }

        try {
            PokedexRepository(api).getPokemonDetailBundle("not-a-real-pokemon")
            error("expected an HttpException")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
    }

    /** F108 — masterIdByName is the shared name->id map several screens used to rebuild via their
     *  own `mapNotNull { it.id }` blocks; it must mirror exactly what that derivation produced,
     *  including dropping any entry whose URL carries no id. */
    @Test
    fun `masterIdByName maps every master-list name to its id, dropping id-less entries`() = runBlocking {
        val api = object : UnexpectedApi() {
            override suspend fun getPokemonList(limit: Int, offset: Int): NamedApiResourceList =
                NamedApiResourceList(
                    count = 3,
                    next = null,
                    previous = null,
                    results = listOf(
                        NamedApiResource("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"),
                        NamedApiResource("no-id", "https://pokeapi.co/api/v2/pokemon/"),
                        NamedApiResource("charizard", "https://pokeapi.co/api/v2/pokemon/6/")
                    )
                )
        }

        assertEquals(mapOf("bulbasaur" to 1, "charizard" to 6), PokedexRepository(api).masterIdByName())
    }

    // B23 — getPokemonDetailBundle used to be persisted only in a 200-entry in-memory cache, dying
    // with the process; a fresh PokedexRepository instance (a cold start, or the ~1300th distinct
    // Pokémon in a session evicting the 1st from the bounded in-memory cache) had to re-fetch from
    // network even for data the Full Detail prefetch tier had already downloaded.
    @Test
    fun `a detail bundle survives a fresh repository instance via the disk cache`() = runBlocking {
        val firstApi = object : UnexpectedApi() {
            override suspend fun getPokemon(nameOrId: String): PokemonDto = pokemon("bulbasaur", 1)
            override suspend fun getPokemonSpecies(nameOrId: String): PokemonSpeciesDto = species("bulbasaur", 1, varieties = null)
        }
        val firstRepository = PokedexRepository(firstApi)
        val original = firstRepository.getPokemonDetailBundle("bulbasaur")

        // A second, fresh repository instance has empty in-memory caches — an API call here would
        // fail loudly (UnexpectedApi), proving the disk cache (not the in-memory one) is what
        // served this.
        val secondRepository = PokedexRepository(UnexpectedApi())
        val cached = secondRepository.getPokemonDetailBundle("bulbasaur")

        assertEquals(original.pokemon.name, cached.pokemon.name)
        assertEquals(original.species.id, cached.species.id)
    }

    // B62 — getStatPercentile had no unit tests: below/tied/boundary cases in its binary-search
    // percentile logic were unverified. hp values below are [10, 20, 20, 30, 40] once sorted.
    @Test
    fun `getStatPercentile splits ties evenly rather than pushing them to an extreme`() = runBlocking {
        PokeApiGraphQLDataSource.client = basicsGraphQLCallFactory(
            mapOf("a" to 10, "b" to 20, "c" to 20, "d" to 30, "e" to 40)
        )
        val repository = PokedexRepository(UnexpectedApi())

        // below=1 (10), equal=2 (20,20) -> (1 + 2/2.0) / 5 = 0.4
        assertEquals(0.4, repository.getStatPercentile("hp", 20), 0.0001)
    }

    @Test
    fun `getStatPercentile for a value with no exact match counts only strictly-lower entries`() = runBlocking {
        PokeApiGraphQLDataSource.client = basicsGraphQLCallFactory(
            mapOf("a" to 10, "b" to 20, "c" to 20, "d" to 30, "e" to 40)
        )
        val repository = PokedexRepository(UnexpectedApi())

        // below=3 (10,20,20), equal=0 -> 3/5 = 0.6
        assertEquals(0.6, repository.getStatPercentile("hp", 25), 0.0001)
    }

    @Test
    fun `getStatPercentile coerces below-min and above-max values to 0 and 1`() = runBlocking {
        PokeApiGraphQLDataSource.client = basicsGraphQLCallFactory(
            mapOf("a" to 10, "b" to 20, "c" to 20, "d" to 30, "e" to 40)
        )
        val repository = PokedexRepository(UnexpectedApi())

        assertEquals(0.0, repository.getStatPercentile("hp", 1), 0.0001)
        assertEquals(1.0, repository.getStatPercentile("hp", 999), 0.0001)
    }

    @Test
    fun `getStatPercentile returns 0-5 for a stat key every pokemon in the dataset is missing`() = runBlocking {
        // Every entry only carries "hp" — "speed" is absent from every stats map, so its sorted
        // array ends up empty.
        PokeApiGraphQLDataSource.client = basicsGraphQLCallFactory(mapOf("a" to 10, "b" to 20))
        val repository = PokedexRepository(UnexpectedApi())

        assertEquals(0.5, repository.getStatPercentile("speed", 50), 0.0001)
    }

    @Test
    fun `getStatPercentile returns 0-5 for an unknown stat key`() = runBlocking {
        PokeApiGraphQLDataSource.client = basicsGraphQLCallFactory(mapOf("a" to 10, "b" to 20))
        val repository = PokedexRepository(UnexpectedApi())

        assertEquals(0.5, repository.getStatPercentile("bogus-stat", 50), 0.0001)
    }
}
