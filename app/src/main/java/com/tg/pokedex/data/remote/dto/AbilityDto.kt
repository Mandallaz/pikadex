package com.tg.pokedex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AbilityDetailDto(
    val id: Int,
    val name: String,
    @SerializedName("effect_entries") val effectEntries: List<EffectEntry>,
    val pokemon: List<AbilityPokemonSlot>
)

data class AbilityPokemonSlot(
    @SerializedName("is_hidden") val isHidden: Boolean,
    val slot: Int,
    val pokemon: NamedApiResource
)
