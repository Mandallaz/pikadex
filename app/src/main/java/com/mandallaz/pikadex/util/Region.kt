package com.mandallaz.pikadex.util

import androidx.annotation.StringRes
import com.mandallaz.pikadex.R

/** F117 — one entry per generation PokeAPI's `pokemon-species.generation` can report, mapped 1:1
 *  to the region it introduced (Kanto=gen1 ... Paldea=gen9) — no separate region dataset needed.
 *  Ordered by game-release order (not alphabetically), matching how the filter sheet's chips
 *  should read. */
enum class Region(val generationName: String, @StringRes val labelResId: Int) {
    KANTO("generation-i", R.string.region_kanto),
    JOHTO("generation-ii", R.string.region_johto),
    HOENN("generation-iii", R.string.region_hoenn),
    SINNOH("generation-iv", R.string.region_sinnoh),
    UNOVA("generation-v", R.string.region_unova),
    KALOS("generation-vi", R.string.region_kalos),
    ALOLA("generation-vii", R.string.region_alola),
    GALAR("generation-viii", R.string.region_galar),
    PALDEA("generation-ix", R.string.region_paldea)
}
