# PikaDex

An Android Pokédex app built with Kotlin and Jetpack Compose, powered entirely by [PokeAPI](https://pokeapi.co/). Its presentation (search, type/move/ability filters, per-category move tables) is inspired by [dex.radicalred.net](https://dex.radicalred.net/), but all data comes from the official games via PokeAPI — the Radical Red ROM hack's own randomizer/save-file features aren't reproduced since that data doesn't exist in PokeAPI.

## Features

### Pokédex

- Search by name or dex number across every entry PokeAPI exposes, alternate forms included
- Filters behind a single sheet, so they don't eat the grid: type (multi-select, AND semantics), move, ability, generation, Smogon competitive tier, favorites-only, and a minimum per-stat threshold slider
- Sort by dex number, any base stat, or the stat total — ascending or descending
- Grid sizes its columns to the screen rather than to a fixed count

### Pokémon detail

- Base stats, with bars colored by percentile rank against every other Pokémon rather than a fixed per-stat hue — so a bar's color tells you whether the number is actually good
- Type matchup chart (weaknesses, resistances, immunities), computed from PokeAPI's type damage relations
- Evolution chain, plus Mega Evolutions where the species has them
- Moves grouped by learn method (Level Up / TM-HM / Breeding / Tutor), each with its type, damage category, power and accuracy
- Links to the [Smogon](https://www.smogon.com) strategy dex, limited to the generations the Pokémon actually has a page in — a Mega links to Gen 6-7 only, not to games that removed the mechanic. Opened in an in-app Custom Tab, so closing one returns straight to the Pokémon
- The type triangles this Pokémon belongs to or counters

### Team builder

- Up to 6 Pokémon, with the combined defensive matrix across all 18 attacking types
- A callout for types at least half the team is weak to
- 81 preset teams — 70 gym leaders and 11 champions, from Red/Blue through Scarlet/Violet — loadable in one tap

### Type Triangles

A reference screen for the 16 rock-paper-scissors type cycles, including the 4 "perfect" ones where offense and defense are fully symmetric. Each gets a diagram, an explanation, and the typing that best counters it.

### Throughout

- Favorites, tracked separately from the team
- Dark mode, following the system setting
- Persistent disk cache for API responses and images, so a warm start needs far less network
- Explicit error states with a retry, instead of a failed fetch quietly rendering as "no results"
- Portrait and landscape

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Retrofit + OkHttp + Gson for the REST API, plus a GraphQL data source for the bulk queries (base stats, move details) that would otherwise be hundreds of round trips
- Coil for image loading (official artwork and type badge sprites)
- Navigation Compose, with a bottom navigation bar across Pokédex / Triangles / Team
- AndroidX Browser for Custom Tabs
- A JSON disk cache plus an in-memory async cache that memoizes the in-flight request rather than the value, so concurrent callers share one fetch
- No DI framework — a small manual singleton container (`AppContainer`)

## Testing

```bash
./gradlew testDebugUnitTest
```

Unit tests cover the pure logic where the rules are dense enough to get quietly wrong: Smogon generation ranges for alternate forms, the artwork fallback for forms with no images of their own, defensive multiplier stacking, and version-group ranking.

## Building

```bash
./gradlew assembleDebug
```

Requires an Android SDK (compileSdk 36) and JDK 17+. minSdk 24.

## License

Licensed under the GNU General Public License v3.0 — see [LICENSE](LICENSE).
