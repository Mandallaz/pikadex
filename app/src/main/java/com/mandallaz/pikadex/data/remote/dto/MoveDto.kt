package com.mandallaz.pikadex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MoveDetailDto(
    val id: Int,
    val name: String,
    val power: Int?,
    val pp: Int?,
    val accuracy: Int?,
    val type: NamedApiResource,
    @SerializedName("damage_class") val damageClass: NamedApiResource,
    // Nullable — see the identical note on AbilityDetailDto.effectEntries.
    @SerializedName("effect_entries") val effectEntries: List<EffectEntry>?,
    @SerializedName("learned_by_pokemon") val learnedByPokemon: List<NamedApiResource>?
)

data class EffectEntry(
    val effect: String,
    @SerializedName("short_effect") val shortEffect: String,
    val language: NamedApiResource
)
