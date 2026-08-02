package com.tg.pokedex.util

/**
 * Chronological order of PokeAPI version_groups, oldest to newest. Used to pick, for each learned
 * move, the entry from the most recent game instead of showing one duplicate per generation. A
 * version_group missing from this list (a game newer than this mapping's knowledge) is treated as
 * the most recent possible, so data is never hidden.
 */
object VersionGroups {
    private val order = listOf(
        "red-blue", "yellow",
        "gold-silver", "crystal",
        "ruby-sapphire", "emerald", "firered-leafgreen",
        "diamond-pearl", "platinum", "heartgold-soulsilver",
        "colosseum", "xd",
        "black-white", "black-2-white-2",
        "x-y", "omega-ruby-alpha-sapphire",
        "sun-moon", "ultra-sun-ultra-moon",
        "lets-go-pikachu-lets-go-eevee",
        "sword-shield",
        "brilliant-diamond-shining-pearl", "legends-arceus",
        "scarlet-violet"
    )

    fun rank(versionGroupName: String): Int {
        val index = order.indexOf(versionGroupName)
        return if (index >= 0) index else Int.MAX_VALUE
    }
}
