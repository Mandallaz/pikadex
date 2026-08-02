package com.mandallaz.pikadex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class EvolutionChainDto(
    val id: Int,
    val chain: ChainLink
)

data class ChainLink(
    val species: NamedApiResource,
    @SerializedName("evolution_details") val evolutionDetails: List<EvolutionDetail>,
    @SerializedName("evolves_to") val evolvesTo: List<ChainLink>
)

data class EvolutionDetail(
    val trigger: NamedApiResource?,
    @SerializedName("min_level") val minLevel: Int?,
    val item: NamedApiResource?,
    @SerializedName("held_item") val heldItem: NamedApiResource?,
    @SerializedName("known_move") val knownMove: NamedApiResource?,
    @SerializedName("min_happiness") val minHappiness: Int?,
    @SerializedName("min_beauty") val minBeauty: Int?,
    @SerializedName("time_of_day") val timeOfDay: String?,
    @SerializedName("needs_overworld_rain") val needsOverworldRain: Boolean?
)
