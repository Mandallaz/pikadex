package com.mandallaz.pikadex.util

/**
 * Every 3-cycle in the 18-type chart: types listed in beats-order (A beats B, B beats C, C beats
 * A). Verified against the real damage_relations data before hardcoding — both the offensive
 * (double_damage_to) and defensive (half/no_damage_from) relations, since [isPerfect] and
 * [TypeCounter] both depend on the defensive side too.
 *
 * A triangle is "perfect" when the defensive side mirrors the offensive one: each type doesn't
 * just beat the next type in the loop, it also resists (0.5x) the type before it. If any link is
 * only a neutral hit (1x) or a full immunity (0x) instead of a plain resist, the symmetry breaks
 * and the triangle is imperfect — even though the offensive rock-paper-scissors loop still holds.
 */
data class TypeTriangle(
    val title: String,
    val types: List<String>,
    val note: String,
    val isPerfect: Boolean,
    val counter: TypeCounter
)

/**
 * The best dual-type to "break" a triangle: the pair whose combined defensive multiplier against
 * all 3 types in the loop is as low as possible (found by brute-forcing all 153 type pairs against
 * each triangle's damage_relations), preferring immunities and double-resists over plain resists,
 * and plain resists over neutral hits.
 */
data class TypeCounter(val types: List<String>, val note: String)

object TypeTriangles {
    val ALL = listOf(
        // --- Perfect: every type also resists the type before it in the loop ---
        TypeTriangle(
            title = "The Classic Starter Triangle",
            types = listOf("fire", "grass", "water"),
            note = "Fire beats Grass, Grass beats Water, and Water beats Fire — the very first " +
                "type match-up every trainer learns. It's perfect: each type also resists the one " +
                "before it in the loop, not just beats the one after.",
            isPerfect = true,
            counter = TypeCounter(
                types = listOf("flying", "dragon"),
                note = "Dragon alone resists Fire and Water; Flying and Dragon both resist Grass, " +
                    "a double resist (0.25x) — e.g. Dragonite. Water / Dragon (Kingdra, Palkia) is " +
                    "a close, more thematic alternative, but it only lands at neutral against " +
                    "Grass rather than resisting it."
            )
        ),
        TypeTriangle(
            title = "Fire / Steel / Rock",
            types = listOf("fire", "steel", "rock"),
            note = "Fire beats Steel, Steel beats Rock, and Rock beats Fire — and each type " +
                "resists the one before it, so the loop is symmetric both offensively and " +
                "defensively.",
            isPerfect = true,
            counter = TypeCounter(
                types = listOf("water", "fighting"),
                note = "Water resists Fire and Steel; Fighting resists Rock — e.g. Poliwrath. " +
                    "Every link in the loop is softened to a plain 0.5x resist, with no weakness " +
                    "anywhere."
            )
        ),
        TypeTriangle(
            title = "Ground / Poison / Grass",
            types = listOf("ground", "poison", "grass"),
            note = "Ground beats Poison, Poison beats Grass, and Grass beats Ground — each type " +
                "also resists the one before it, another clean, fully symmetric triangle.",
            isPerfect = true,
            counter = TypeCounter(
                types = listOf("flying", "steel"),
                note = "Flying is immune to Ground and Steel is immune to Poison — two clean " +
                    "immunities — and both halves resist Grass for a double resist (0.25x) — " +
                    "e.g. Skarmory. Nothing in this triangle gets through."
            )
        ),
        TypeTriangle(
            title = "Fighting / Rock / Flying",
            types = listOf("fighting", "rock", "flying"),
            note = "Fighting beats Rock, Rock beats Flying, and Flying beats Fighting — every " +
                "link is backed by a resistance, so this one is perfect too.",
            isPerfect = true,
            counter = TypeCounter(
                types = listOf("ghost", "steel"),
                note = "Ghost is immune to Fighting, and Steel alone resists both Rock and Flying " +
                    "— e.g. Aegislash."
            )
        ),

        // --- Imperfect: the offensive loop holds, but a defensive link is neutral or an immunity ---
        TypeTriangle(
            title = "Dark / Psychic / Fighting",
            types = listOf("dark", "psychic", "fighting"),
            note = "Fighting beats Dark, Dark beats Psychic, and Psychic beats Fighting — a " +
                "famous thematic trio, but imperfect: Dark isn't just resistant to Psychic, it's " +
                "fully immune, so that link hits for zero instead of half.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("ghost", "dark"),
                note = "Dark is immune to Psychic and Ghost is immune to Fighting — two full " +
                    "immunities out of three — e.g. Spiritomb. Only Dark-type damage gets through, " +
                    "and even that lands at neutral (Ghost's own weakness to Dark cancels Dark's " +
                    "resistance to itself). Fairy / Dark (Grimmsnarl) is a strong alternative — " +
                    "immune to Psychic, and resists Dark down to 0.25x — but Fighting still lands " +
                    "at neutral there too, not resisted, so it doesn't \"resist absolutely " +
                    "everything\" either."
            )
        ),
        TypeTriangle(
            title = "Electric / Water / Ground",
            types = listOf("electric", "water", "ground"),
            note = "Electric beats Water, Water beats Ground, and Ground beats Electric — but " +
                "Ground is fully immune to Electric rather than merely resistant, so the loop's " +
                "third link hits for zero damage.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("grass", "bug"),
                note = "Grass alone resists Electric and Water; both halves resist Ground for a " +
                    "double resist (0.25x) — e.g. Leavanny. Grass / Ground (Torterra) looks " +
                    "tempting for the Electric immunity, but Ground's own weakness to Water " +
                    "cancels Grass's resistance there, landing at neutral rather than resisted."
            )
        ),
        TypeTriangle(
            title = "Fighting / Ice / Flying",
            types = listOf("fighting", "ice", "flying"),
            note = "Fighting beats Ice, Ice beats Flying, and Flying beats Fighting — only " +
                "Flying's win is backed by a resistance; Fighting takes neutral damage from Ice " +
                "and Ice takes neutral damage from Flying.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("ghost", "steel"),
                note = "The same Ghost / Steel core that breaks Fighting / Rock / Flying also " +
                    "breaks this one: Ghost's immunity to Fighting, plus Steel's resistance to " +
                    "both Ice and Flying — e.g. Aegislash."
            )
        ),
        TypeTriangle(
            title = "Bug / Grass / Rock",
            types = listOf("bug", "grass", "rock"),
            note = "Bug beats Grass, Grass beats Rock, and Rock beats Bug — only Bug backs up " +
                "its win with a resistance; Grass takes neutral damage from Rock and Rock takes " +
                "neutral damage from Bug.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("fighting", "steel"),
                note = "Fighting and Steel both resist Bug and both resist Rock — double resists " +
                    "(0.25x) on each — and Steel alone resists Grass — e.g. Cobalion."
            )
        ),
        TypeTriangle(
            title = "Fairy / Fighting / Steel",
            types = listOf("fairy", "fighting", "steel"),
            note = "Fairy beats Fighting, Fighting beats Steel, and Steel beats Fairy — Fairy and " +
                "Steel both back their win with a resistance, but Fighting takes neutral damage " +
                "from Steel, breaking the symmetry.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("fire", "poison"),
                note = "Fire and Poison both resist Fairy — a double resist (0.25x) — Poison " +
                    "alone resists Fighting, and Fire alone resists Steel — e.g. Salazzle."
            )
        ),
        TypeTriangle(
            title = "Fire / Grass / Ground",
            types = listOf("fire", "grass", "ground"),
            note = "Fire beats Grass, Grass beats Ground, and Ground beats Fire — Fire and Grass " +
                "both back their win with a resistance, but Ground takes neutral damage from Fire.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("fire", "flying"),
                note = "Flying is immune to Ground; Fire and Flying both resist Grass — a double " +
                    "resist (0.25x) — and Fire resists itself — e.g. Charizard."
            )
        ),
        TypeTriangle(
            title = "Fire / Grass / Rock",
            types = listOf("fire", "grass", "rock"),
            note = "Fire beats Grass, Grass beats Rock, and Rock beats Fire — Fire and Rock both " +
                "back their win with a resistance, but Grass takes neutral damage from Rock.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("fighting", "dragon"),
                note = "Dragon resists Fire and Grass; Fighting resists Rock — e.g. Kommo-o. " +
                    "Three separate single resists, evenly spread across the pair."
            )
        ),
        TypeTriangle(
            title = "Fire / Ice / Ground",
            types = listOf("fire", "ice", "ground"),
            note = "Fire beats Ice, Ice beats Ground, and Ground beats Fire — only Fire backs up " +
                "its win with a resistance; Ice takes neutral damage from Ground and Ground " +
                "takes neutral damage from Fire.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("water", "flying"),
                note = "Water resists Fire and Flying is immune to Ground — e.g. Gyarados. But " +
                    "Flying's own weakness to Ice cancels out Water's resistance there, so Ice " +
                    "still lands at neutral: this is one of only two triangles (with Ground / " +
                    "Rock / Ice) that no type pair fully resists."
            )
        ),
        TypeTriangle(
            title = "Flying / Grass / Rock",
            types = listOf("flying", "grass", "rock"),
            note = "Flying beats Grass, Grass beats Rock, and Rock beats Flying — Flying and Rock " +
                "both back their win with a resistance, but Grass takes neutral damage from Rock.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("electric", "steel"),
                note = "Electric and Steel both resist Flying — a double resist (0.25x) — and " +
                    "Steel alone resists Grass and Rock — e.g. Magnezone. This is the triangle " +
                    "Magnezone actually counters well — not Fighting / Ice / Flying, where " +
                    "Electric doesn't help at all against Fighting."
            )
        ),
        TypeTriangle(
            title = "Grass / Rock / Ice",
            types = listOf("grass", "rock", "ice"),
            note = "Grass beats Rock, Rock beats Ice, and Ice beats Grass — a purely offensive " +
                "loop: none of the three types resists the one it beats, so every link is a " +
                "neutral hit back.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("fighting", "steel"),
                note = "The same Fighting / Steel pair that breaks Bug / Grass / Rock handles " +
                    "this one too: both halves resist Rock — a double resist (0.25x) — and Steel " +
                    "alone resists Grass and Ice — e.g. Cobalion."
            )
        ),
        TypeTriangle(
            title = "Ground / Rock / Ice",
            types = listOf("ground", "rock", "ice"),
            note = "Ground beats Rock, Rock beats Ice, and Ice beats Ground — only Ground backs " +
                "up its win with a resistance; Rock takes neutral damage from Ice and Ice takes " +
                "neutral damage from Ground.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("flying", "steel"),
                note = "Flying is immune to Ground — e.g. Skarmory — but this is the other " +
                    "triangle with no fully clean counter: Flying's own weaknesses to Rock and " +
                    "Ice each cancel out Steel's resistances to those same types, landing both at " +
                    "neutral instead of resisted."
            )
        ),
        TypeTriangle(
            title = "Ground / Steel / Ice",
            types = listOf("ground", "steel", "ice"),
            note = "Ground beats Steel, Steel beats Ice, and Ice beats Ground — only Steel backs " +
                "up its win with a resistance; Ground takes neutral damage from Steel and Ice " +
                "takes neutral damage from Ground.",
            isPerfect = false,
            counter = TypeCounter(
                types = listOf("water", "bug"),
                note = "Bug resists Ground, and Water resists Steel and Ice — e.g. Golisopod. " +
                    "Each link is carried by one half of the pair, for an even 0.5x resist across " +
                    "the board."
            )
        )
    )

    /** Every triangle whose best-counter typing exactly matches the given types (order-independent) —
     *  i.e. this is one of the dual-types that "breaks" that triangle. */
    fun counteredBy(types: Collection<String>): List<TypeTriangle> {
        val typeSet = types.toSet()
        return ALL.filter { triangle -> triangle.counter.types.toSet() == typeSet }
    }

    /** True if [types] is the exact best-counter typing to at least one triangle in [ALL] — the
     *  per-Pokémon predicate behind the Pokédex list's "Perfect Counter" filter (F33), same check
     *  the detail screen's Type Triangles card already runs via [counteredBy] to decide whether it
     *  has anything to show for a given Pokémon. */
    fun isPerfectCounter(types: Collection<String>): Boolean = counteredBy(types).isNotEmpty()

    /** Every triangle whose best-counter typing [types] merely *shares* a type with (F69) — at
     *  least one of the two types in the triangle's [TypeCounter.types], without matching it
     *  exactly (those exact matches are [counteredBy]'s job, and the two are mutually exclusive by
     *  construction). Deliberately narrower than "resists at least one leg of the loop", which
     *  would flag far more typings for a much weaker signal — see the comment at
     *  `PokedexDetailScreen.kt` on why the old "member of a triangle" callout was dropped. */
    fun partiallyCounteredBy(types: Collection<String>): List<TypeTriangle> {
        val typeSet = types.toSet()
        return ALL.filter { triangle ->
            val counterSet = triangle.counter.types.toSet()
            typeSet != counterSet && typeSet.any { it in counterSet }
        }
    }
}
