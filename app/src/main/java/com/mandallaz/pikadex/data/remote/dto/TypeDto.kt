package com.mandallaz.pikadex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TypeDetailDto(
    val id: Int,
    val name: String,
    @SerializedName("damage_relations") val damageRelations: DamageRelations,
    // Nullable — see the identical note on AbilityDetailDto.effectEntries.
    val pokemon: List<TypePokemonSlot>?
)

data class DamageRelations(
    // All six nullable for the same Gson-unsafe-allocation reason as AbilityDetailDto's fields
    // above — computeDefensiveMultipliers/computeOffensiveMultipliers absorb the nullability
    // with .orEmpty() rather than every one of the six crashing independently.
    @SerializedName("double_damage_from") val doubleDamageFrom: List<NamedApiResource>?,
    @SerializedName("double_damage_to") val doubleDamageTo: List<NamedApiResource>?,
    @SerializedName("half_damage_from") val halfDamageFrom: List<NamedApiResource>?,
    @SerializedName("half_damage_to") val halfDamageTo: List<NamedApiResource>?,
    @SerializedName("no_damage_from") val noDamageFrom: List<NamedApiResource>?,
    @SerializedName("no_damage_to") val noDamageTo: List<NamedApiResource>?
)

data class TypePokemonSlot(
    val slot: Int,
    val pokemon: NamedApiResource
)
