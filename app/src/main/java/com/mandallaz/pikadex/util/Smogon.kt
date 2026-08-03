package com.mandallaz.pikadex.util

data class SmogonGen(val code: String, val label: String)

data class SmogonLink(val code: String, val label: String, val url: String)

/**
 * Builds links to Smogon's strategy dex (smogon.com/dex/{gen}/pokemon/{name}/) for a pokemon, and
 * exposes the generation list/labels shared with the competitive-tier filter.
 *
 * PokeAPI has no link to Smogon, and live-checking each of the 9 generation pages costs ~1MB of
 * traffic per pokemon (Smogon's dex pages are a full SPA bundle, no lightweight HEAD support), so
 * instead this infers the pokemon's introduction generation from PokeAPI's species data (or from
 * well-known regional/mega/gmax name suffixes, since those forms use the base species'
 * introduction generation otherwise) and lists every generation from there to the present. A
 * handful of unusual forms can still end up with a dead link — Smogon just shows its own 404 page.
 */
object Smogon {
    // Oldest to newest. speciesGeneration (PokeAPI) -> Smogon generation code + a clear label,
    // since the bare 2-letter dex codes (rb, ss, sv...) aren't self-explanatory.
    private val GENERATIONS = listOf(
        "generation-i" to SmogonGen("rb", "Gen 1 · Red/Blue"),
        "generation-ii" to SmogonGen("gs", "Gen 2 · Gold/Silver"),
        "generation-iii" to SmogonGen("rs", "Gen 3 · Ruby/Sapphire"),
        "generation-iv" to SmogonGen("dp", "Gen 4 · Diamond/Pearl"),
        "generation-v" to SmogonGen("bw", "Gen 5 · Black/White"),
        "generation-vi" to SmogonGen("xy", "Gen 6 · X/Y"),
        "generation-vii" to SmogonGen("sm", "Gen 7 · Sun/Moon"),
        "generation-viii" to SmogonGen("ss", "Gen 8 · Sword/Shield"),
        "generation-ix" to SmogonGen("sv", "Gen 9 · Scarlet/Violet")
    )

    /** Oldest to newest. */
    val ALL_GENERATIONS: List<SmogonGen> = GENERATIONS.map { it.second }

    /** PokeAPI version_group -> the Smogon generation that covers it. A version group *absent* here
     *  has no Smogon dex at all, and forms introduced in it get no links: "mega-dimension" (Legends
     *  Z-A) is the case that matters, since it accounts for roughly half of all "-mega" forms. */
    private val VERSION_GROUP_TO_GEN = mapOf(
        "red-blue" to "rb", "yellow" to "rb",
        "gold-silver" to "gs", "crystal" to "gs",
        "ruby-sapphire" to "rs", "emerald" to "rs", "firered-leafgreen" to "rs",
        "colosseum" to "rs", "xd" to "rs",
        "diamond-pearl" to "dp", "platinum" to "dp", "heartgold-soulsilver" to "dp",
        "black-white" to "bw", "black-2-white-2" to "bw",
        "x-y" to "xy", "omega-ruby-alpha-sapphire" to "xy",
        "sun-moon" to "sm", "ultra-sun-ultra-moon" to "sm",
        "lets-go-pikachu-lets-go-eevee" to "sm",
        "sword-shield" to "ss", "brilliant-diamond-shining-pearl" to "ss", "legends-arceus" to "ss",
        "scarlet-violet" to "sv"
    )

    /** Fallback introduction generation per form suffix, used only when the form's own version group
     *  couldn't be fetched — otherwise a regional form would inherit its species' generation and
     *  link to dex pages from long before the form existed. */
    private val FORM_SUFFIX_START = listOf(
        "-mega" to "xy",
        "-primal" to "xy", // Primal Reversion is ORAS (Gen 6), not Gen 3 like the species
        "-alola" to "sm",
        "-totem" to "sm",
        "-galar" to "ss",
        "-gmax" to "ss",
        "-hisui" to "ss",
        "-paldea" to "sv",
        "-bloodmoon" to "sv"
    )

    /** Last generation a form's battle mechanic existed in. Links used to run from a form's debut
     *  all the way to the present, so every Mega linked to Sword/Shield and Scarlet/Violet dex pages
     *  for a mechanic those games removed. */
    private val FORM_SUFFIX_END = listOf(
        "-mega" to "sm",   // Mega Evolution: Gen 6-7 only
        "-primal" to "sm", // same lifespan
        "-totem" to "sm",  // Totem Pokémon: Sun/Moon only
        "-gmax" to "ss"    // Gigantamax: Gen 8 only
    )

    private fun indexOfCode(code: String?) = GENERATIONS.indexOfFirst { it.second.code == code }

    /**
     * Most recent generation first, since that's what players usually care about.
     *
     * [formVersionGroup] is the form's own PokeAPI version group when known; it's the only signal
     * that separates an original Mega from a Legends Z-A one, so without it the caller gets the
     * coarser suffix-based guess instead.
     */
    fun linksFor(
        pokemonName: String,
        speciesGeneration: String,
        formVersionGroup: String? = null
    ): List<SmogonLink> {
        val startIndex = if (formVersionGroup != null) {
            val code = VERSION_GROUP_TO_GEN[formVersionGroup] ?: return emptyList()
            indexOfCode(code)
        } else {
            // contains, not endsWith: "charizard-mega-x" is a Mega too, and matching only the end
            // sent it back to its species' generation — links from Red/Blue onwards for a Gen 6 form.
            val suffixCode = FORM_SUFFIX_START.firstOrNull { (suffix, _) -> pokemonName.contains(suffix) }?.second
            if (suffixCode != null) indexOfCode(suffixCode) else GENERATIONS.indexOfFirst { it.first == speciesGeneration }
        }.coerceAtLeast(0)

        val endCode = FORM_SUFFIX_END.firstOrNull { (suffix, _) -> pokemonName.contains(suffix) }?.second
        val endIndex = endCode?.let { indexOfCode(it) }?.takeIf { it >= 0 } ?: GENERATIONS.lastIndex
        if (startIndex > endIndex) return emptyList()

        return GENERATIONS.subList(startIndex, endIndex + 1)
            .map { (_, gen) -> SmogonLink(gen.code, gen.label, "https://www.smogon.com/dex/${gen.code}/pokemon/$pokemonName/") }
            .reversed()
    }
}
