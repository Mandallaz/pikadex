package com.mandallaz.pikadex.util

import androidx.annotation.StringRes
import com.mandallaz.pikadex.R

/** Restricts the Pokédex grid by rarity, using PokeAPI's own legendary/mythical species flags.
 *  ORDINARY is the complement of the other two (neither flag set), not "everything" — that's
 *  what a null filter already means, so a third "no restriction" entry here would be redundant. */
enum class RarityFilter(@StringRes val labelResId: Int) {
    LEGENDARY(R.string.detail_legendary),
    MYTHICAL(R.string.detail_mythical),
    ORDINARY(R.string.detail_ordinary)
}
