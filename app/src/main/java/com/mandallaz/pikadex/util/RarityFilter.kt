package com.mandallaz.pikadex.util

/** Restricts the Pokédex grid by rarity, using PokeAPI's own legendary/mythical species flags.
 *  ORDINARY is the complement of the other two (neither flag set), not "everything" — that's
 *  what a null filter already means, so a third "no restriction" entry here would be redundant. */
enum class RarityFilter(val label: String) {
    LEGENDARY("Legendary"),
    MYTHICAL("Mythical"),
    ORDINARY("Ordinary")
}
