package com.mandallaz.pikadex.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * issue B40 — a non-default variety's own detail page was listing itself under "Other Forms"
 * instead of the species' default form, because [PokemonSpeciesDto.otherForms] filtered by
 * [SpeciesVariety.isDefault] rather than by which pokemon is currently being viewed.
 */
class SpeciesDtoTest {

    private fun species(varieties: List<SpeciesVariety>?) = PokemonSpeciesDto(
        id = 648,
        name = "meloetta",
        evolutionChain = null,
        flavorTextEntries = null,
        genera = null,
        color = NamedApiResource("gray", "https://pokeapi.co/api/v2/pokemon-color/gray/"),
        eggGroups = null,
        generation = NamedApiResource("generation-v", "https://pokeapi.co/api/v2/generation/5/"),
        isLegendary = false,
        isMythical = true,
        varieties = varieties
    )

    private fun variety(name: String, isDefault: Boolean) = SpeciesVariety(
        isDefault = isDefault,
        pokemon = NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$name/")
    )

    @Test
    fun `default form's other forms lists the non-default variety`() {
        val meloetta = variety("meloetta", isDefault = true)
        val pirouette = variety("meloetta-pirouette", isDefault = false)
        val species = species(listOf(meloetta, pirouette))

        val otherForms = species.otherForms(currentPokemonName = "meloetta")

        assertEquals(listOf(pirouette), otherForms)
    }

    @Test
    fun `non-default form's other forms lists the default variety, not itself`() {
        val meloetta = variety("meloetta", isDefault = true)
        val pirouette = variety("meloetta-pirouette", isDefault = false)
        val species = species(listOf(meloetta, pirouette))

        val otherForms = species.otherForms(currentPokemonName = "meloetta-pirouette")

        assertEquals(listOf(meloetta), otherForms)
    }

    // issue B41 (#104) — same self-referential "Other Forms" bug as B40, reported separately
    // for Keldeo (#10024's page pointed to itself instead of #0647) and closed as a duplicate
    // of B40 since it shares the same root cause and fix.
    @Test
    fun `keldeo's resolute form lists the default keldeo form, not itself`() {
        val keldeo = variety("keldeo", isDefault = true)
        val keldeoResolute = variety("keldeo-resolute", isDefault = false)
        val species = species(listOf(keldeo, keldeoResolute))

        val otherForms = species.otherForms(currentPokemonName = "keldeo-resolute")

        assertEquals(listOf(keldeo), otherForms)
    }
}
