package com.tg.pokedex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TypeDetailDto(
    val id: Int,
    val name: String,
    @SerializedName("damage_relations") val damageRelations: DamageRelations,
    val pokemon: List<TypePokemonSlot>
)

data class DamageRelations(
    @SerializedName("double_damage_from") val doubleDamageFrom: List<NamedApiResource>,
    @SerializedName("double_damage_to") val doubleDamageTo: List<NamedApiResource>,
    @SerializedName("half_damage_from") val halfDamageFrom: List<NamedApiResource>,
    @SerializedName("half_damage_to") val halfDamageTo: List<NamedApiResource>,
    @SerializedName("no_damage_from") val noDamageFrom: List<NamedApiResource>,
    @SerializedName("no_damage_to") val noDamageTo: List<NamedApiResource>
)

data class TypePokemonSlot(
    val slot: Int,
    val pokemon: NamedApiResource
)
