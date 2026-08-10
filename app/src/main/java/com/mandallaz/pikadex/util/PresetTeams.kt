package com.mandallaz.pikadex.util

import androidx.annotation.StringRes
import com.mandallaz.pikadex.R

/**
 * A notable trainer's roster, loadable into the team builder to inspect its type coverage.
 *
 * [pokemon] holds PokeAPI species names, not ids — the ids are resolved at load time against the
 * master list the app has already downloaded, so this table can't drift out of sync with the dex
 * (a wrong id here would silently show the wrong sprite; a wrong name simply fails to resolve).
 *
 * Rosters are the trainer's **first main story battle** in the listed version, not a rematch or
 * post-game team, and duplicate species are collapsed (the team builder holds one entry per
 * species, and a duplicate adds nothing to a type-coverage read anyway). Rosters that vary with
 * the player's starter choice use the variant noted in a comment.
 */
data class PresetTeam(
    val trainer: String,
    val role: PresetRole,
    val game: String,
    val pokemon: List<String>
)

enum class PresetRole(@param:StringRes val labelRes: Int) {
    GYM_LEADER(R.string.preset_role_gym_leader),
    CHAMPION(R.string.preset_role_champion)
}

object PresetTeams {

    /** Grouped by game in release order; [ALL] flattens them in the same order. */
    val BY_GAME: List<Pair<String, List<PresetTeam>>> by lazy {
        ALL.groupBy { it.game }.toList()
    }

    val ALL: List<PresetTeam> = listOf(
        // ---- Generation I ----
        gym("Brock", RB, "geodude", "onix"),
        gym("Misty", RB, "staryu", "starmie"),
        gym("Lt. Surge", RB, "voltorb", "pikachu", "raichu"),
        gym("Erika", RB, "victreebel", "tangela", "vileplume"),
        gym("Koga", RB, "koffing", "muk", "weezing"),
        gym("Sabrina", RB, "kadabra", "mr-mime", "venomoth", "alakazam"),
        gym("Blaine", RB, "growlithe", "ponyta", "rapidash", "arcanine"),
        gym("Giovanni", RB, "rhyhorn", "dugtrio", "nidoqueen", "nidoking", "rhydon"),
        // Blue's roster follows the starter the player did *not* pick; this is the Bulbasaur-start
        // version (so he carries the Charmander line).
        champion("Blue", RB, "pidgeot", "alakazam", "rhydon", "gyarados", "arcanine", "charizard"),

        // ---- Generation II ----
        gym("Falkner", GS, "pidgey", "pidgeotto"),
        gym("Bugsy", GS, "metapod", "kakuna", "scyther"),
        gym("Whitney", GS, "clefairy", "miltank"),
        gym("Morty", GS, "gastly", "haunter", "gengar"),
        gym("Chuck", GS, "primeape", "poliwrath"),
        gym("Jasmine", GS, "magnemite", "steelix"),
        gym("Pryce", GS, "seel", "dewgong", "piloswine"),
        gym("Clair", GS, "dragonair", "kingdra"),
        champion("Lance", GS, "gyarados", "dragonite", "aerodactyl", "charizard"),

        // ---- Generation III ----
        gym("Roxanne", RS, "geodude", "nosepass"),
        gym("Brawly", RS, "machop", "makuhita"),
        gym("Wattson", RS, "magnemite", "voltorb", "magneton"),
        gym("Flannery", RS, "numel", "slugma", "torkoal"),
        gym("Norman", RS, "slaking", "vigoroth"),
        gym("Winona", RS, "swellow", "pelipper", "skarmory", "altaria"),
        gym("Tate & Liza", RS, "solrock", "lunatone"),
        gym("Wallace", RS, "luvdisc", "whiscash", "sealeo", "crawdaunt", "milotic"),
        champion("Steven", RS, "skarmory", "aggron", "claydol", "cradily", "armaldo", "metagross"),
        champion("Wallace", EMERALD, "wailord", "tentacruel", "ludicolo", "whiscash", "gyarados", "milotic"),

        // ---- Generation IV ----
        gym("Roark", DP, "geodude", "onix", "cranidos"),
        gym("Gardenia", DP, "cherubi", "turtwig", "roserade"),
        gym("Maylene", DP, "meditite", "machoke", "lucario"),
        gym("Crasher Wake", DP, "gyarados", "quagsire", "floatzel"),
        gym("Fantina", DP, "duskull", "haunter", "mismagius"),
        gym("Byron", DP, "bronzor", "steelix", "bastiodon"),
        gym("Candice", DP, "sneasel", "piloswine", "abomasnow", "froslass"),
        gym("Volkner", DP, "raichu", "ambipom", "octillery", "luxray"),
        champion("Cynthia", DP, "spiritomb", "roserade", "gastrodon", "lucario", "milotic", "garchomp"),

        // ---- Generation V ----
        // The Striaton gym is a three-trainer battle; the monkeys are pooled here since which one
        // you face depends on your starter.
        gym("Cilan, Chili & Cress", BW, "lillipup", "pansage", "pansear", "panpour"),
        gym("Lenora", BW, "herdier", "watchog"),
        gym("Burgh", BW, "whirlipede", "dwebble", "leavanny"),
        gym("Elesa", BW, "emolga", "zebstrika"),
        gym("Clay", BW, "krokorok", "palpitoad", "excadrill"),
        gym("Skyla", BW, "swoobat", "unfezant", "swanna"),
        gym("Brycen", BW, "vanillish", "cryogonal", "beartic"),
        gym("Drayden", BW, "fraxure", "druddigon", "haxorus"),
        champion("Alder", BW, "accelgor", "bouffalant", "druddigon", "vanilluxe", "escavalier", "volcarona"),
        champion("Iris", B2W2, "hydreigon", "druddigon", "aggron", "archeops", "lapras", "haxorus"),

        // ---- Generation VI ----
        gym("Viola", XY, "surskit", "vivillon"),
        gym("Grant", XY, "amaura", "tyrunt"),
        gym("Korrina", XY, "mienfoo", "machoke", "hawlucha"),
        gym("Ramos", XY, "jumpluff", "weepinbell", "gogoat"),
        gym("Clemont", XY, "emolga", "magneton", "heliolisk"),
        gym("Valerie", XY, "mawile", "mr-mime", "sylveon"),
        gym("Olympia", XY, "sigilyph", "slowking", "meowstic-male"),
        gym("Wulfric", XY, "abomasnow", "cryogonal", "avalugg"),
        champion("Diantha", XY, "hawlucha", "tyrantrum", "aurorus", "gourgeist-average", "goodra", "gardevoir"),

        // ---- Generation VII ----
        // Alola has island kahunas and trial captains rather than gyms; the kahunas are the
        // closest equivalent to a gym leader battle.
        gym("Hala", SM, "makuhita", "machop", "crabrawler"),
        gym("Olivia", SM, "anorith", "lileep", "lycanroc-midday"),
        gym("Nanu", SM, "sableye", "krokorok", "persian"),
        gym("Hapu", SM, "golurk", "gastrodon", "flygon", "mudsdale"),
        champion("Kukui", SM, "lycanroc-midday", "ninetales", "braviary", "magnezone", "snorlax", "incineroar"),

        // ---- Generation VIII ----
        gym("Milo", SWSH, "gossifleur", "eldegoss"),
        gym("Nessa", SWSH, "goldeen", "arrokuda", "drednaw"),
        gym("Kabu", SWSH, "ninetales", "arcanine", "centiskorch"),
        gym("Bea", SWORD, "hitmontop", "pangoro", "sirfetchd", "machamp"),
        gym("Allister", SHIELD, "yamask", "mimikyu-disguised", "cursola", "gengar"),
        gym("Opal", SWSH, "weezing", "mawile", "togekiss", "alcremie"),
        gym("Gordie", SWORD, "barbaracle", "shuckle", "stonjourner", "coalossal"),
        gym("Melony", SHIELD, "frosmoth", "darmanitan-standard", "eiscue-ice", "lapras"),
        gym("Piers", SWSH, "scrafty", "malamar", "skuntank", "obstagoon"),
        gym("Raihan", SWSH, "gigalith", "flygon", "sandaconda", "duraludon"),
        champion("Leon", SWSH, "aegislash-shield", "dragapult", "haxorus", "seismitoad", "mr-rime", "charizard"),

        // ---- Generation IX ----
        gym("Katy", SV, "nymble", "tarountula", "teddiursa"),
        gym("Brassius", SV, "petilil", "smoliv", "sudowoodo"),
        gym("Iono", SV, "wattrel", "bellibolt", "luxio", "mismagius"),
        gym("Kofu", SV, "veluza", "wugtrio", "crabominable"),
        gym("Larry", SV, "komala", "dudunsparce-two-segment", "staraptor"),
        gym("Ryme", SV, "mimikyu-disguised", "banette", "houndstone", "toxtricity-amped"),
        gym("Tulip", SV, "farigiraf", "gardevoir", "espathra", "florges"),
        gym("Grusha", SV, "frosmoth", "beartic", "cetitan", "altaria"),
        champion("Geeta", SV, "espathra", "avalugg", "kingambit", "veluza", "gogoat", "glimmora")
    )
}

private const val RB = "Red / Blue / Yellow"
private const val GS = "Gold / Silver / Crystal"
private const val RS = "Ruby / Sapphire"
private const val EMERALD = "Emerald"
private const val DP = "Diamond / Pearl / Platinum"
private const val BW = "Black / White"
private const val B2W2 = "Black 2 / White 2"
private const val XY = "X / Y"
private const val SM = "Sun / Moon"
private const val SWSH = "Sword / Shield"
private const val SWORD = "Sword"
private const val SHIELD = "Shield"
private const val SV = "Scarlet / Violet"

private fun gym(trainer: String, game: String, vararg pokemon: String) =
    PresetTeam(trainer, PresetRole.GYM_LEADER, game, pokemon.toList())

private fun champion(trainer: String, game: String, vararg pokemon: String) =
    PresetTeam(trainer, PresetRole.CHAMPION, game, pokemon.toList())
