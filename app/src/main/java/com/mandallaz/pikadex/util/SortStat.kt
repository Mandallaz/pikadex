package com.mandallaz.pikadex.util

/** Sort keys for the Pokédex list. [apiName] matches PokeAPI/GraphQL's stat name; the entries that
 *  aren't a single stat (a sum, or the dex number itself) have none. */
enum class SortStat(val apiName: String?, val label: String) {
    /** Unlike every other entry this reads the resource's own id rather than the bulk stats map, so
     *  it's the one sort that still works with no network. */
    DEX_NUMBER(null, "Dex number"),
    /** Also needs no network — sorts on the resource's own (locally computed) display name. */
    NAME(null, "Name (A–Z)"),
    HP("hp", "HP"),
    ATTACK("attack", "Attack"),
    DEFENSE("defense", "Defense"),
    SPECIAL_ATTACK("special-attack", "Sp. Atk"),
    SPECIAL_DEFENSE("special-defense", "Sp. Def"),
    SPEED("speed", "Speed"),
    TOTAL(null, "Total")
}
