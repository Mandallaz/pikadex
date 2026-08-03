package com.mandallaz.pikadex.data.remote

import com.mandallaz.pikadex.data.remote.dto.AbilityDetailDto
import com.mandallaz.pikadex.data.remote.dto.EvolutionChainDto
import com.mandallaz.pikadex.data.remote.dto.MoveDetailDto
import com.mandallaz.pikadex.data.remote.dto.NamedApiResourceList
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonFormDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PokeApiService {

    @GET("pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0
    ): NamedApiResourceList

    @GET("pokemon/{nameOrId}")
    suspend fun getPokemon(@Path("nameOrId") nameOrId: String): PokemonDto

    @GET("pokemon-species/{nameOrId}")
    suspend fun getPokemonSpecies(@Path("nameOrId") nameOrId: String): PokemonSpeciesDto

    @GET("pokemon-form/{name}")
    suspend fun getPokemonForm(@Path("name") name: String): PokemonFormDto

    @GET("evolution-chain/{id}")
    suspend fun getEvolutionChain(@Path("id") id: Int): EvolutionChainDto

    @GET("type/{name}")
    suspend fun getType(@Path("name") name: String): TypeDetailDto

    @GET("type")
    suspend fun getTypeList(@Query("limit") limit: Int = 30): NamedApiResourceList

    @GET("move")
    suspend fun getMoveList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0
    ): NamedApiResourceList

    @GET("move/{name}")
    suspend fun getMove(@Path("name") name: String): MoveDetailDto

    @GET("ability")
    suspend fun getAbilityList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0
    ): NamedApiResourceList

    @GET("ability/{name}")
    suspend fun getAbility(@Path("name") name: String): AbilityDetailDto

    companion object {
        const val BASE_URL = "https://pokeapi.co/api/v2/"
    }
}
