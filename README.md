# PikaDex

An Android Pokédex app built with Kotlin and Jetpack Compose, powered entirely by [PokeAPI](https://pokeapi.co/). Its presentation (search, type/move/ability filters, per-category move tables) is inspired by [dex.radicalred.net](https://dex.radicalred.net/), but all data comes from the official games via PokeAPI — the Radical Red ROM hack's own randomizer/save-file features aren't reproduced since that data doesn't exist in PokeAPI.

## Features

- Searchable Pokédex list with filters by type, move, and ability
- Detail screen: base stats, abilities, official type badges, evolution chain, moves grouped by learn method (Level Up / TM-HM / Breeding / Tutor)
- Type matchup chart per Pokémon (weaknesses, resistances, immunities), computed from PokeAPI's type damage relations
- Team builder: pick up to 6 Pokémon and see the team's combined type weaknesses/resistances in a single matrix

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Retrofit + OkHttp + Gson for networking
- Coil for image loading (official artwork and type badge sprites)
- Navigation Compose
- No DI framework — a small manual singleton container (`AppContainer`)

## Building

```bash
./gradlew assembleDebug
```

Requires an Android SDK (compileSdk 36) and JDK 17+.

## License

Licensed under the GNU General Public License v3.0 — see [LICENSE](LICENSE).
