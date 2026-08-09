package com.mandallaz.pikadex.data.repository

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
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * BACKLOG.md F28 — Kubfu's evolution chain names the *species* "urshifu", which has no bare-name
 * Pokémon resource of its own (only its named varieties `urshifu-single-strike`/
 * `urshifu-rapid-strike` do), so a plain `/pokemon/urshifu` fetch 404s. Reproduced on the emulator:
 * tapping Urshifu in Kubfu's Evolution card silently did nothing before the fix.
 */
class PokedexRepositoryTest {

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
}
