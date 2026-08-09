# PikaDex

An Android Pokédex app built with Kotlin and Jetpack Compose, powered entirely by [PokeAPI](https://pokeapi.co/). Its presentation (search, type/move/ability filters, per-category move tables) is inspired by [dex.radicalred.net](https://dex.radicalred.net/), but all data comes from the official games via PokeAPI — the Radical Red ROM hack's own randomizer/save-file features aren't reproduced since that data doesn't exist in PokeAPI.

## Features

### Pokédex

- Search by name or dex number across every entry PokeAPI exposes, alternate forms included
- Filters behind a single sheet, so they don't eat the grid: type (multi-select, AND semantics), move, ability, generation, Smogon competitive tier, favorites-only, legendary/mythical/ordinary rarity, "Perfect Counter" (only Pokémon whose typing is the exact best counter to a type triangle), and a minimum threshold slider per base stat plus one for the stat total
- Sort by dex number, any base stat, or the stat total — ascending or descending
- Grid sizes its columns to the screen rather than to a fixed count

### Pokémon detail

- Base stats, with bars colored by percentile rank against every other Pokémon rather than a fixed per-stat hue — so a bar's color tells you whether the number is actually good
- Type matchup chart (weaknesses, resistances, immunities), computed from PokeAPI's type damage relations
- Evolution chain, plus any other alternate forms of the species (Mega Evolutions, Gigantamax, one-off forms like Ursaluna Bloodmoon) that aren't part of the chain itself
- Moves grouped by learn method (Level Up / TM-HM / Breeding / Tutor), each with its type, damage category, power, accuracy, priority (when it changes turn order), and competitive meta info when relevant (crit rate, secondary status ailment + chance, drain/recoil, healing, flinch chance, stat changes + chance)
- Links to the [Smogon](https://www.smogon.com) strategy dex, limited to the generations the Pokémon actually has a page in — a Mega links to Gen 6-7 only, not to games that removed the mechanic. Opened in an in-app Custom Tab, so closing one returns straight to the Pokémon
- The type triangles this Pokémon's typing is the exact best counter to (shown only when it is one)
- Animated battle sprite toggle (Pokémon Showdown's sprites, via PokeAPI), alongside the existing shiny-coloring toggle — falls back to the static artwork for the (mostly newer) forms with no animated sprite
- Swipe left/right (or tap the chevrons pinned at sprite level) to move to the adjacent Pokémon without backing out to the list — steps through whatever the Pokédex list is currently showing (respects its active filters/sort), or dex order when reached some other way (an evolution chain tap, Compare, a team member chip)
- Team Coverage Impact card: appears whenever you have an active team with room to grow, showing what adding this Pokémon would change about the team's shared weaknesses and coverage gaps

### Team builder

- Up to 6 Pokémon, with the combined defensive matrix across all 18 attacking types
- A callout for types at least half the team is weak to, and one for coverage gaps — types nothing on the team can hit for more than neutral damage
- Suggestions: up to 6 Pokémon that would fix both a shared weakness and a coverage gap at once, sorted by total impact (weaknesses resisted plus gaps hit, most useful first) with each tile explaining exactly which ones; shown whenever the team has room to grow, and optionally capped to a competitive tier and below via a Settings toggle. Tap a tile's sprite to open that Pokémon's own detail page
- 81 preset teams — 70 gym leaders and 11 champions, from Red/Blue through Scarlet/Violet — loadable in one tap

### Type Triangles

A reference screen for the 16 rock-paper-scissors type cycles, including the 4 "perfect" ones where offense and defense are fully symmetric. Each gets a diagram, an explanation, and the typing that best counters it.

### Settings

- Offline prefetch, tiered: Essentials (base stats, moves, type chart, Smogon tiers, ~1MB), Sprites (artwork and sprites for every entry, 50-150MB), and an opt-in Full detail tier (every Pokémon's complete data) — each with live progress and partial-failure reporting that never aborts the run
- Storage accounting for the API and image caches, with a one-tap "Clear downloaded data"
- Team Suggestions tier limit: cap suggested Pokémon to a Gen 9 Smogon tier and below (e.g. UU also allows RU, NU...), off by default
- AMOLED black toggle: forces dark mode with a pure black background/surface instead of Material's dark grey, off by default

### Throughout

- Favorites, tracked separately from the team
- Dark mode, following the system setting
- Persistent disk cache for API responses and images, so a warm start needs far less network — see Settings to prefetch it all ahead of time
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
