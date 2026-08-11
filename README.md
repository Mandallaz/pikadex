# PikaDex

An Android Pokédex app in Kotlin + Jetpack Compose, powered entirely by [PokeAPI](https://pokeapi.co/). Presentation is inspired by [dex.radicalred.net](https://dex.radicalred.net/), but all data comes from the official games.

## Features

**Pokédex** — search by name or number; filter by type, move, ability, generation, Smogon tier, favorites, rarity, "Perfect Counter", and per-stat thresholds; sort by any stat; responsive grid.

**Pokémon detail** — percentile-colored stat bars, type matchups, evolution chain and alternate forms, moves grouped by learn method with full combat data, Smogon strategy links, animated/shiny sprite toggle, cry playback, swipe navigation, and a Team Coverage Impact card showing what adding this Pokémon would do for your current team.

**Team builder** — build a team of up to 6, see its combined defensive matrix, shared weaknesses, and coverage gaps; get suggested Pokémon that fix both at once; or load one of 81 preset teams (70 gym leaders + 11 champions, Red/Blue through Scarlet/Violet).

**Type Triangles** — reference screen for the 16 type match-up cycles, each with a diagram and its best counter typing.

**Settings** — tiered offline prefetch (essentials / sprites / full detail / cries), storage accounting with one-tap clear, team-suggestion tier cap, AMOLED black mode.

**Throughout** — favorites, system dark mode, persistent disk cache for offline-friendly warm starts, explicit error states with retry, portrait and landscape.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Retrofit/OkHttp/Gson + GraphQL for bulk queries, Coil, Navigation Compose, AndroidX Browser, a JSON + in-memory disk cache, manual DI (`AppContainer`).

## Development

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug build
```

Requires an Android SDK (compileSdk 37) and JDK 17+. minSdk 24.

`assembleRelease`/`bundleRelease` fall back to debug signing when no `keystore.properties` is present. To sign with a real key, copy `keystore.properties.example` to `keystore.properties` (gitignored) and fill in a real keystore's `storeFile`/`storePassword`/`keyAlias`/`keyPassword`.

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
