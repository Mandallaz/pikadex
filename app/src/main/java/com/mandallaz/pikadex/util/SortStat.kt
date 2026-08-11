package com.mandallaz.pikadex.util

import androidx.annotation.StringRes
import com.mandallaz.pikadex.R

/** Sort keys for the Pokédex list. [apiName] matches PokeAPI/GraphQL's stat name; the entries that
 *  aren't a single stat (a sum, or the dex number itself) have none.
 *
 *  B30 — [labelRes], not a plain `String`: this enum lives in `util/`, which has no Compose
 *  dependency, so the actual localized text is resolved at render time via
 *  [com.mandallaz.pikadex.ui.components.localizedLabel] (same `@StringRes`-then-`stringResource()`
 *  pattern as [SmogonGen]/[com.mandallaz.pikadex.util.MoveCategory]'s own extensions). */
enum class SortStat(val apiName: String?, @StringRes val labelRes: Int) {
    /** Unlike every other entry this reads the resource's own id rather than the bulk stats map, so
     *  it's the one sort that still works with no network. */
    DEX_NUMBER(null, R.string.sort_dex_number),
    /** Also needs no network — sorts on the resource's own (locally computed) display name. */
    NAME(null, R.string.sort_name),
    HP("hp", R.string.sort_hp),
    ATTACK("attack", R.string.sort_attack),
    DEFENSE("defense", R.string.sort_defense),
    SPECIAL_ATTACK("special-attack", R.string.sort_special_attack),
    SPECIAL_DEFENSE("special-defense", R.string.sort_special_defense),
    SPEED("speed", R.string.sort_speed),
    TOTAL(null, R.string.sort_total)
}
