package com.mandallaz.pikadex.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PokemonSpeciesDto(
    val id: Int,
    val name: String,
    @SerializedName("evolution_chain") val evolutionChain: EvolutionChainRef?,
    // Nullable — same Gson-unsafe-allocation reasoning as varieties below, generalised: a
    // response missing any of these keys must degrade at the read site, not crash.
    @SerializedName("flavor_text_entries") val flavorTextEntries: List<FlavorTextEntry>?,
    val genera: List<Genus>?,
    val color: NamedApiResource,
    @SerializedName("egg_groups") val eggGroups: List<NamedApiResource>?,
    val generation: NamedApiResource,
    @SerializedName("is_legendary") val isLegendary: Boolean,
    @SerializedName("is_mythical") val isMythical: Boolean,
    /** Every playable form of this species — the default one plus megas, regional forms, Gmax...
     *  Nullable rather than defaulted to empty: Gson allocates these DTOs without running the
     *  constructor, so a `= emptyList()` default would not be applied and a response missing the
     *  field would leave this null anyway (see the same note on NamedApiResource). */
    val varieties: List<SpeciesVariety>?
) {
    /** Every alternate form of this species other than the default one — Mega Evolutions,
     *  Gigantamax forms, and one-off special forms like Ursaluna Bloodmoon (issue #19) alike.
     *  PokeAPI models all of these as [varieties], not as links in [evolutionChain], so none of
     *  them are otherwise visible anywhere the evolution chain is read. How each one is actually
     *  obtained isn't in this data at all (it's not a level/item/trade-triggered evolution), so
     *  this only says a form *exists*, not how to get it. */
    val otherForms: List<SpeciesVariety>
        get() = varieties.orEmpty().filterNot { it.isDefault }
}

data class SpeciesVariety(
    @SerializedName("is_default") val isDefault: Boolean,
    val pokemon: NamedApiResource
)

/** A specific form of a Pokémon. Only [versionGroup] is used: it says which games a form was
 *  introduced in, which is the only reliable way to tell an original Gen 6 Mega ("x-y") from a
 *  Legends Z-A one ("mega-dimension") — both are named "{species}-mega" and both can belong to a
 *  pre-Gen-8 species, so neither the name nor the species' own generation distinguishes them. */
data class PokemonFormDto(
    val name: String,
    @SerializedName("version_group") val versionGroup: NamedApiResource?
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
