package com.tg.pokedex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PokemonSpeciesDto(
    val id: Int,
    val name: String,
    @SerializedName("evolution_chain") val evolutionChain: EvolutionChainRef?,
    @SerializedName("flavor_text_entries") val flavorTextEntries: List<FlavorTextEntry>,
    val genera: List<Genus>,
    val color: NamedApiResource,
    @SerializedName("egg_groups") val eggGroups: List<NamedApiResource>,
    val generation: NamedApiResource,
    @SerializedName("is_legendary") val isLegendary: Boolean,
    @SerializedName("is_mythical") val isMythical: Boolean
)

data class EvolutionChainRef(val url: String) {
    val id: Int? get() = url.trimEnd('/').substringAfterLast('/').toIntOrNull()
}

data class FlavorTextEntry(
    @SerializedName("flavor_text") val flavorText: String,
    val language: NamedApiResource,
    val version: NamedApiResource
)

data class Genus(
    val genus: String,
    val language: NamedApiResource
)
