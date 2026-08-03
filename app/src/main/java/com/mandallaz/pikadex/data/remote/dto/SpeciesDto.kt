package com.mandallaz.pikadex.data.remote.dto

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
    @SerializedName("is_mythical") val isMythical: Boolean,
    /** Every playable form of this species — the default one plus megas, regional forms, Gmax...
     *  Nullable rather than defaulted to empty: Gson allocates these DTOs without running the
     *  constructor, so a `= emptyList()` default would not be applied and a response missing the
     *  field would leave this null anyway (see the same note on NamedApiResource). */
    val varieties: List<SpeciesVariety>?
) {
    /** Mega Evolutions of this species. PokeAPI models them as alternate *varieties*, not as links
     *  in the evolution chain, so they're invisible to [evolutionChain] and have to be read here. */
    val megaEvolutions: List<SpeciesVariety>
        get() = varieties.orEmpty().filter { it.pokemon.name.contains("-mega") }
}

data class SpeciesVariety(
    @SerializedName("is_default") val isDefault: Boolean,
    val pokemon: NamedApiResource
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
