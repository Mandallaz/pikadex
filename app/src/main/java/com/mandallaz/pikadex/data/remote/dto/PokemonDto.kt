package com.mandallaz.pikadex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PokemonDto(
    val id: Int,
    val name: String,
    val height: Int,
    val weight: Int,
    @SerializedName("base_experience") val baseExperience: Int?,
    // Nullable, not defaulted to emptyList(): Gson allocates this class via Unsafe without
    // running the constructor, so a default value would never be applied — a response missing
    // any of these keys leaves the field null regardless of what the declaration promises, and
    // the non-null version crashed at the first read site (the detail screen, movesForCategory)
    // instead of degrading.
    val types: List<PokemonTypeSlot>?,
    val stats: List<PokemonStatSlot>?,
    val abilities: List<PokemonAbilitySlot>?,
    val moves: List<PokemonMoveEntry>?,
    val sprites: PokemonSprites,
    val species: NamedApiResource
)

data class PokemonTypeSlot(
    val slot: Int,
    val type: NamedApiResource
)

data class PokemonStatSlot(
    @SerializedName("base_stat") val baseStat: Int,
    val effort: Int,
    val stat: NamedApiResource
)

data class PokemonAbilitySlot(
    val ability: NamedApiResource,
    @SerializedName("is_hidden") val isHidden: Boolean,
    val slot: Int
)

data class PokemonMoveEntry(
    val move: NamedApiResource,
    @SerializedName("version_group_details") val versionGroupDetails: List<VersionGroupDetail>
)

data class VersionGroupDetail(
    @SerializedName("level_learned_at") val levelLearnedAt: Int,
    @SerializedName("move_learn_method") val moveLearnMethod: NamedApiResource,
    @SerializedName("version_group") val versionGroup: NamedApiResource
)

data class PokemonSprites(
    @SerializedName("front_default") val frontDefault: String?,
    @SerializedName("front_shiny") val frontShiny: String?,
    val other: OtherSprites?
)

data class OtherSprites(
    @SerializedName("official-artwork") val officialArtwork: OfficialArtworkSprites?,
    // Animated battle-sprite GIFs sourced from Pokémon Showdown (F38) — coverage is incomplete
    // (especially newer forms), so both this and its fields are nullable, degrading at the read
    // site the same way as every other sprite field here.
    val showdown: ShowdownSprites?
)

data class OfficialArtworkSprites(
    @SerializedName("front_default") val frontDefault: String?,
    @SerializedName("front_shiny") val frontShiny: String?
)

/** Front-only, matching the rest of the app: no back sprites are shown anywhere today (the
 *  existing shiny toggle on [PokemonSprites] is front-only too), so [back_default]/[back_shiny]
 *  aren't read here. */
data class ShowdownSprites(
    @SerializedName("front_default") val frontDefault: String?,
    @SerializedName("front_shiny") val frontShiny: String?
)
