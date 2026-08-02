package com.tg.pokedex.util

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

    private val FORM_SUFFIX_OVERRIDES = listOf(
        "-mega" to "xy",
        "-primal" to "rs",
        "-alola" to "sm",
        "-totem" to "sm",
        "-galar" to "ss",
        "-gmax" to "ss",
        "-hisui" to "sv",
        "-paldea" to "sv",
        "-bloodmoon" to "sv"
    )

    /** Most recent generation first, since that's what players usually care about. */
    fun linksFor(pokemonName: String, speciesGeneration: String): List<SmogonLink> {
        val overrideCode = FORM_SUFFIX_OVERRIDES.firstOrNull { (suffix, _) -> pokemonName.endsWith(suffix) }?.second
        val startIndex = if (overrideCode != null) {
            GENERATIONS.indexOfFirst { it.second.code == overrideCode }
        } else {
            GENERATIONS.indexOfFirst { it.first == speciesGeneration }
        }.coerceAtLeast(0)

        return GENERATIONS.drop(startIndex)
            .map { (_, gen) -> SmogonLink(gen.code, gen.label, "https://www.smogon.com/dex/${gen.code}/pokemon/$pokemonName/") }
            .reversed()
    }
}
