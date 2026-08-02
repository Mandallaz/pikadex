package com.tg.pokedex.util

data class SmogonLink(val code: String, val label: String, val url: String)

/**
 * Builds links to Smogon's strategy dex (smogon.com/dex/{gen}/pokemon/{name}/) for a pokemon.
 *
 * PokeAPI has no link to Smogon, and live-checking each of the 9 generation pages costs ~1MB of
 * traffic per pokemon (Smogon's dex pages are a full SPA bundle, no lightweight HEAD support), so
 * instead this infers the pokemon's introduction generation from PokeAPI's species data (or from
 * well-known regional/mega/gmax name suffixes, since those forms use the base species'
 * introduction generation otherwise) and lists every generation from there to the present. A
 * handful of unusual forms can still end up with a dead link — Smogon just shows its own 404 page.
 */
object Smogon {
    // Oldest to newest.
    private val GENERATIONS = listOf(
        "generation-i" to SmogonGen("rb", "Gen 1 · RBY"),
        "generation-ii" to SmogonGen("gs", "Gen 2 · GSC"),
        "generation-iii" to SmogonGen("rs", "Gen 3 · RSE"),
        "generation-iv" to SmogonGen("dp", "Gen 4 · DPP"),
        "generation-v" to SmogonGen("bw", "Gen 5 · BW"),
        "generation-vi" to SmogonGen("xy", "Gen 6 · XY"),
        "generation-vii" to SmogonGen("sm", "Gen 7 · SM"),
        "generation-viii" to SmogonGen("ss", "Gen 8 · SwSh"),
        "generation-ix" to SmogonGen("sv", "Gen 9 · SV")
    )

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

    private data class SmogonGen(val code: String, val label: String)
}
