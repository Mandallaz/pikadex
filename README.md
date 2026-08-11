# PikaDex

An Android Pokédex app powered entirely by [PokeAPI](https://pokeapi.co/). Presentation is inspired by [dex.radicalred.net](https://dex.radicalred.net/), but all data comes from the official games.

## Features

**Pokédex** — search by name or number; filter by type, move, ability, generation, Smogon tier, favorites, rarity, "Perfect Counter", and per-stat thresholds; sort by any stat.

**Pokémon detail** — stat bars colored by percentile, type matchups, evolution chain and alternate forms, moves with full combat data, Smogon strategy links, animated/shiny sprites, cry playback, swipe to browse, and a card showing what adding this Pokémon would do for your current team.

**Team builder** — build a team of up to 6, see its shared weaknesses and coverage gaps, get suggested Pokémon that fix both at once, or load one of 81 preset teams (70 gym leaders + 11 champions, Red/Blue through Scarlet/Violet).

**Type Triangles** — reference screen for the 16 type match-up cycles, each with a diagram and its best counter.

**Settings** — tiered offline prefetch, storage accounting with one-tap clear, team-suggestion tier cap, AMOLED black mode.

**Throughout** — favorites, dark mode, offline-friendly caching, clear error states with retry, portrait and landscape.

## Development

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug build
```

Requires an Android SDK (compileSdk 37) and JDK 17+. minSdk 24.

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
