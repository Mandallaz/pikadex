package com.mandallaz.pikadex.util

/** Sortable base stats. [apiName] matches PokeAPI/GraphQL's stat name; TOTAL has none since it's a sum. */
enum class SortStat(val apiName: String?, val label: String) {
    HP("hp", "HP"),
    ATTACK("attack", "Attack"),
    DEFENSE("defense", "Defense"),
    SPECIAL_ATTACK("special-attack", "Sp. Atk"),
    SPECIAL_DEFENSE("special-defense", "Sp. Def"),
    SPEED("speed", "Speed"),
    TOTAL(null, "Total")
}
