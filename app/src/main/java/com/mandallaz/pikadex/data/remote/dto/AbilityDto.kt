package com.mandallaz.pikadex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AbilityDetailDto(
    val id: Int,
    val name: String,
    // Nullable, not `= emptyList()`: Gson allocates this class via Unsafe without running the
    // constructor, so a default value would never be applied — a response missing either key
    // leaves the field null regardless of what the declaration promises, and the non-null
    // version crashed at the first read site instead of degrading.
    @SerializedName("effect_entries") val effectEntries: List<EffectEntry>?,
    val pokemon: List<AbilityPokemonSlot>?
)

data class AbilityPokemonSlot(
    @SerializedName("is_hidden") val isHidden: Boolean,
    val slot: Int,
    val pokemon: NamedApiResource
)
