# PikaDex — Backlog

> Mirrored to [GitHub Issues](https://github.com/Mandallaz/pikadex/issues?q=is%3Aissue) — kept in
> sync in parallel while we decide which of the two stays the source of truth. Every entry below
> links to its issue number.

## Priority (reviewed 2026-08-08)

| Feature | Priority | Status | Issue |
|---|---|---|---|
| F15 — Team coverage impact preview | — | Done | [#2](https://github.com/Mandallaz/pikadex/issues/2) |
| F20 — Radical Red mode (rebalanced stats + trainer teams) | Medium | To groom | [#3](https://github.com/Mandallaz/pikadex/issues/3) |
| F19 — Black/AMOLED mode | — | Done | [#4](https://github.com/Mandallaz/pikadex/issues/4) |
| F17 — Filter dex by "is legendary" | — | Done | [#5](https://github.com/Mandallaz/pikadex/issues/5) |
| F12 — Match team against a preset trainer | Low | Plan ready | [#6](https://github.com/Mandallaz/pikadex/issues/6) |
| F16 — Swipe between Pokémon on the detail screen | — | Done | [#7](https://github.com/Mandallaz/pikadex/issues/7) |
| F18 — Fix ExperimentalCoilApi warnings at compile time | — | Done | [#8](https://github.com/Mandallaz/pikadex/issues/8) |
| F23 — Fix unreachable coverage matrix on Team screen | — | Done | [#9](https://github.com/Mandallaz/pikadex/issues/9) |
| F22 — Suggestion "why" text and impact-based sort | — | Done | [#10](https://github.com/Mandallaz/pikadex/issues/10) |
| F21 — Suggestion tier ceiling filter | — | Done | [#11](https://github.com/Mandallaz/pikadex/issues/11) |
| F9 — Showdown export | — | Cancelled | [#12](https://github.com/Mandallaz/pikadex/issues/12) |
| F10 — Filter dex by resistance/weakness | — | Cancelled | [#13](https://github.com/Mandallaz/pikadex/issues/13) |
| F24 — Reorder detail screen sections | — | Done | [#14](https://github.com/Mandallaz/pikadex/issues/14) |
| F25 — Shrink Smogon Strategy Dex card | — | Done | [#15](https://github.com/Mandallaz/pikadex/issues/15) |
| F26 — Simplify Type Triangles card to perfect-counter-only | — | Done | [#16](https://github.com/Mandallaz/pikadex/issues/16) |
| F27 — Tap a suggestion's sprite to open its detail page | — | Done | [#17](https://github.com/Mandallaz/pikadex/issues/17) |
| F28 — Bug: can't open Urshifu's detail from Kubfu's Evolution card | — | Done | [#18](https://github.com/Mandallaz/pikadex/issues/18) |
| F29 — Bug: Evolution card doesn't surface Ursaluna Bloodmoon / other one-off forms | — | Done | [#19](https://github.com/Mandallaz/pikadex/issues/19) |
| F30 — Bottom nav bar too tall in portrait mode | — | To groom | [#20](https://github.com/Mandallaz/pikadex/issues/20) |
| F31 — Top app bar too tall in portrait mode | — | To groom | [#21](https://github.com/Mandallaz/pikadex/issues/21) |
| F32 — Survey: unused PokeAPI data | — | To groom | [#22](https://github.com/Mandallaz/pikadex/issues/22) |
| F33 — Filter dex by "perfect counter to a type triangle" | — | To groom | [#23](https://github.com/Mandallaz/pikadex/issues/23) |
| F34 — Play a Pokémon's cry + prefetch tier for cry audio | Medium | To groom | [#24](https://github.com/Mandallaz/pikadex/issues/24) |
| F35 — Translate the app into PokeAPI's supported languages | Medium | To groom | [#25](https://github.com/Mandallaz/pikadex/issues/25) |

Status values: **To groom** (idea captured, not yet planned) · **Plan ready** (spec finalized,
implement when asked) · **In progress** (currently being implemented) · **Done** (built and in the
README's feature list — entry kept here rather than removed, as a log of what shipped and why) ·
**Cancelled** (spec kept in the Cancelled section below in case the user revisits it).

Features discussed with the user, whether or not yet implemented. An entry stays here permanently:
once built it's marked **Done** (and also added to the README's feature list) rather than removed,
so this file doubles as a log of what shipped and why — removing it once done would lose the
rationale behind it. A cancelled entry instead moves to the "Cancelled" section at the bottom, full
spec kept intact — the user may change their mind later, and re-deriving a spec from scratch is
wasted effort a kept description avoids.

## F18 — Fix ExperimentalCoilApi warnings at compile time

**Done 2026-08-08.** `./gradlew compileDebugKotlin` emitted two `This declaration needs opt-in`
warnings from `SettingsViewModel.kt` (`measureStorage()` and `clearDownloadedData()`).

- Checked the installed `coil-compose` version (2.7.0, via `gradle/libs.versions.toml`) against its
  bundled sources: the warnings don't come from `ImageLoader.diskCache` itself (stable since 2.2) but
  from the two `DiskCache` members it exposes that are called — `DiskCache.size` and
  `DiskCache.clear()` — both still annotated `@ExperimentalCoilApi` in 2.7.0's `DiskCache.kt`. No
  stabilized non-experimental accessor exists yet in this version, resolving the open question in
  favor of opting in rather than switching APIs.
- `@OptIn(ExperimentalCoilApi::class)` added to both `measureStorage()` and `clearDownloadedData()`,
  each with a one-line comment on why the experimental surface is accepted.
- Verified via `./gradlew compileDebugKotlin --rerun-tasks` (config cache otherwise no-ops an
  unchanged file): warnings gone, build succeeds. `./gradlew testDebugUnitTest` still green — no
  behavior change, so no new test needed.

## F20 — Radical Red mode

**To groom** — data source resolved, dataset extracted and enriched 2026-08-08; implementation plan
not yet agreed.

A toggleable mode (not just an addition to the existing preset list) that swaps in data from the
[Radical Red](https://dex.radicalred.net/) ROM hack, which already inspired this app's Pokédex
presentation per the README's intro:

- **Rebalanced stats — data source question resolved.** Source is
  [JwowSquared/Radical-Red-Pokedex](https://github.com/JwowSquared/Radical-Red-Pokedex) (what
  dex.radicalred.net itself is built from), specifically its root `data.js` — a ~4.6MB JS object
  literal (single-quoted keys, `\uXXXX`-escaped text, not valid JSON as-is) covering species,
  moves, abilities, items, types, natures, trainers. Parsed and converted into
  `app/src/main/assets/radicalred/radicalred_pokedex.json` (17MB, 1343 species, PokeAPI-DTO-shaped:
  `types[].type.name`, `stats[].base_stat`/`stat.name`, `abilities[].ability.name`/`is_hidden`,
  `moves[].move.name` + `version_group_details[].move_learn_method.name` ∈
  {level-up,machine,tutor,egg}, `held_items`, `sprites.front_default`/`front_shiny` pointing at
  bundled files — back sprites dropped per user request, front-only) plus
  `app/src/main/assets/radicalred/sprites/front{,_shiny}/{id}.png` (2752 files, 64×64 GBA-style,
  from the same repo's `graphics/species/`) — together ~27MB bundled into the APK. Every species
  carries an `is_radical_red_exclusive` flag (40 true): Sevii regional forms (e.g. `Noibat-Sevii`),
  non-canonical custom Mega forms (e.g. `Machamp-Mega`), `Dialga-Primal`, and the one genuine
  fakemon `Chillet` (dexID 2001, Ice/Dragon, only species outside the national dex range).
  Groudon-Primal/Kyogre-Primal are present too but *not* flagged exclusive — their RR stats are
  identical to the canonical PokeAPI `-primal` forms.
- **Evolutions — decoded.** `evolutions[]` per species now carries a human-readable `description`
  (e.g. `"at Level 16"`, `"with the Charzardite X"` — RR's own typo, kept verbatim) built by
  reimplementing the template-string lookup table found at `data.js`'s top-level `evolutions` key
  (26 trigger codes: level/friendship/held-item/stone/party-species/party-type/nature-group/
  time-of-day/overworld-rain/50%-chance/Ninjask-slot/Attack-vs-Defense, plus a combined
  move-or-item code `254`). Raw `trigger_raw`/`param_raw`/`extra_raw` kept alongside the text for
  anyone who later wants structured fields instead of prose. All 17 trigger codes actually used
  across the dex are covered — verified by enumerating every code that appears before writing the
  decoder, not by decoding on faith.
- **Missing fields — filled from PokeAPI.** `height`, `weight`, `base_experience`,
  `is_legendary`/`is_mythical`, `generation_id`, `color`, `egg_groups` (names, not RR's internal
  IDs), and `genus` now live under a new `pokeapi_metadata` object per species, bulk-fetched in one
  GraphQL call to `graphql.pokeapi.co/v1beta2` (`pokemon(limit: 2000){...}`, same endpoint/shape
  `PokeApiGraphQLDataSource.kt` already uses) rather than ~2700 individual REST calls. Matched by
  slug in three tiers, recorded per entry as `pokeapi_metadata.source`: `"exact"` (1190 — RR's key
  slug *is* a real PokeAPI name, covers canonical forms like `venusaur-mega` and the two Primals),
  `"base-species-fallback"` (90 — RR exclusive forms like `Noibat-Sevii`/`Machamp-Mega` fall back to
  the base species' metadata, since these are reskins of an existing species, not new ones — the
  attached color/legendary-flag/egg-groups describe the *base* Pokémon, not the RR variant, worth
  keeping in mind wherever this shows in UI), `"base-species-default-variety"` (62 — species with no
  bare PokeAPI slug because the real dex only has named forms, e.g. `deoxys` → `deoxys-normal`,
  `giratina` → `giratina-altered`, resolved via each family's `is_default` flag). Only **one**
  species has no metadata at all: `Chillet` (`pokeapi_metadata: null`) — genuinely absent from
  PokeAPI, nothing to fall back to.
- **Reproducible pipeline.** `tools/radicalred/build_dataset.py` (downloads `data.js`, parses it,
  decodes evolutions, enriches via PokeAPI, writes the asset JSON — `--data-js`/`--skip-pokeapi`
  flags for offline/no-network reruns) and `tools/radicalred/fetch_sprites.sh` (sparse-clones just
  `graphics/species/` and copies front sprites into the asset folder) — both idempotent, rerun
  either whenever Radical Red's own data updates rather than hand-editing the generated JSON.
- **Still open.** A real repository-level data source (`RadicalRedDataSource` or similar) reading
  this bundled JSON instead of hitting PokeAPI/GraphQL when the mode is on — not yet implemented,
  this item only covers getting the data itself in place and correct. Also unresolved: `moves[]`
  still produces one entry per learn method for the same move rather than PokeAPI's one-entry-with-
  multiple-`version_group_details` shape (functionally fine, structurally different); `NamedApiResource`
  fields throughout (`type.name`, `move.name`, `ability.name`...) have no `url`, so any code that
  derives an id by parsing that url will need a different path for Radical Red data; and the
  species-identity mapping (RR's `key`/`name` vs. whatever the rest of the app keys screens off)
  still needs deciding before detail/filters/suggestions can share one lookup path.
- **Replaced trainer teams.** `util/PresetTeams.kt`'s 70 gym leaders + 11 champions (official-game
  rosters, `PresetTeam`/`PresetRole`) get replaced by Radical Red's own trainer rosters while the
  mode is on, not appended alongside them — the user asked for a swap, not a second preset list.
  Boss trainer rosters for both Normal and Hardcore mode are available at
  [apescasio.fr/apecio/docs](https://apescasio.fr/apecio/docs/) — a candidate source, though which
  of Normal/Hardcore (or both, as a sub-toggle) still needs deciding, same as the difficulty-mode
  question already open for the stats side below. The user also pointed at a
  [Google Drive folder](https://drive.google.com/drive/folders/1YaYM-8dzRlBRuJm1bmYrjJC6HGwTwl-x) as
  a possible source — noted here unread: Drive requires an authenticated session neither WebFetch
  nor any available tool can provide, so its contents haven't been reviewed. Whoever grooms this
  item next needs to open it manually (or export/share the specific files as plain links) to see
  what's actually in it before it factors into the plan.
- **Scope of "mode".** Toggled where (Settings, alongside the new F19 dark-mode toggle?) and what it
  affects — base stats + trainer teams only, or does it extend to movepools/abilities too (Radical
  Red also changes those, but that's a much bigger fetch/parse surface)? Also: which Radical Red
  version's data (still under active development, values shift between releases), and whether
  rebalanced stats replace PokeAPI's everywhere in the app (Pokédex list/detail/suggestions/matrix)
  or only within the trainer-team/team-builder flows. All open questions to resolve before this
  moves to "Plan ready" — likely the most involved item in this backlog once scoped, given it touches
  a new external data source plus every screen that reads base stats today.

## F19 — Black/AMOLED mode

**Done 2026-08-09.** Of the two options this backlog entry left open (true-black AMOLED variant vs.
a manual light/dark/system override independent of the OS setting), implemented autonomously as the
former only — the simpler, narrower ask, and the one actually named "AMOLED mode". A manual
dark-mode override (independent of the system setting) is still open; split out below if wanted.

- `data/DisplaySettings.kt`: new `SharedPreferences`-backed singleton (`amoled_settings` prefs file,
  same pattern as `PrefetchSettings`/`SuggestionSettings`), one `amoledEnabled: StateFlow<Boolean>`,
  default `false`. `init()`'d from `PikaDexApplication.onCreate()` alongside the others.
- `ui/theme/Theme.kt`: new `AmoledDarkColors` (`DarkColors.copy(background = Color.Black, surface =
  Color.Black, surfaceVariant = Color.Black)` — accent colors unchanged, only the true-black
  swap). Selection logic pulled into a pure `internal fun selectColorScheme(darkTheme, amoledBlack):
  ColorScheme`, kept outside `PokeDexTheme`'s `@Composable` body so it's unit-testable without a
  Compose runtime; the composable gained an `amoledBlack: Boolean = false` parameter (same pattern as
  the existing `dynamicColor` parameter/comment).
- `MainActivity.kt`: collects `DisplaySettings.amoledEnabled` as state and passes it into
  `PokeDexTheme(amoledBlack = ...)`. **Revised 2026-08-09** after the user tried the toggle with the
  emulator in light mode and saw no change: `darkTheme` is now computed as `isSystemInDarkTheme() ||
  amoledBlack` rather than left at its system-only default — enabling AMOLED black is itself a
  request for dark mode, not just a modifier of it, so the user shouldn't also have to flip the
  system theme separately.
- `SettingsViewModel`/`SettingsScreen`: new `amoledEnabled` field on `SettingsUiState`,
  `setAmoledEnabled()` delegator, and a "Display" section with one `PrefetchTierRow`-style Switch row
  ("AMOLED black" / "True black background in dark mode, to save battery on AMOLED screens.").
- Test: `ui/theme/ThemeTest.kt` — light theme ignores the flag, dark-without-amoled keeps Material
  grey, dark-with-amoled is pure black, and accent colors (`primary`/`secondary`/`tertiary`) stay
  identical between the two dark variants.
- README's Settings and Throughout sections, and `PRIVACY_POLICY.md`'s "Data stored on your device"
  paragraph, updated to mention the new local-only preference.

## F17 — Filter dex by "is legendary"

**Done — found already shipped 2026-08-09**, tracking status here and on the issue was stale. Built
as commit `9d676b3` ("legendary/mythical badges and dex rarity filter"), predating this backlog
entry's last edit.

- `util/RarityFilter.kt`: `RarityFilter` enum (`LEGENDARY`, `MYTHICAL`, `ORDINARY`) — legendary and
  mythical resolved as two states of one filter rather than independent booleans.
- `is_legendary`/`is_mythical` were already pulled in by the existing bulk GraphQL call
  (`PokeApiGraphQLDataSource.QUERY`'s `pokemonspecy { is_legendary is_mythical }`), no new fetch path
  needed — `PokedexListViewModel.loadBaseStatsIfNeeded()` populates `legendaryNames`/`mythicalNames`
  alongside `baseStats` in the same call.
- `PokedexListScreen.kt`'s `FilterSheetContent` — Rarity sits as a `SelectableChip` in the "Other
  filters" row alongside Favorites/Move/Ability/Format/Tier (not among the type-filter chips), opens
  an `OptionsDialog` listing Legendary/Mythical/Ordinary/Any.
- Test: `PokedexListViewModelTest.kt`, "Rarity filter" section — legendary-only, mythical-only,
  ordinary-exclusion, and the not-yet-loaded no-op case.

## F12 — Match team against a preset trainer

**Plan finalized 2026-08-08** — simplest option chosen for every open question; implement when asked.
"How does my team fare against Cynthia's?"

- `util/TeamVersus.kt` (+ test): pure. Simplified from the original sketch — `offensiveByType` turned
  out to be unnecessary: with a defensive multiplier per Pokémon already in hand, "best I deal to
  them" is just the max over *my* member's own types of *their* member's defensive multiplier for
  that attacking type (and symmetrically the other way), so one map covers both directions:
  ```kotlin
  data class VersusCell(val myName: String, val theirName: String, val myBest: Double, val theirBest: Double)

  fun buildVersusGrid(
      myTypings: Map<String, List<String>>,     // name -> own types, both rosters
      theirTypings: Map<String, List<String>>,
      defensiveByPokemon: Map<String, Map<String, Double>>  // name -> attackingType -> multiplier, covers both rosters
  ): List<VersusCell> = myTypings.flatMap { (myName, myTypes) ->
      theirTypings.map { (theirName, theirTypes) ->
          val myBest = myTypes.mapNotNull { defensiveByPokemon[theirName]?.get(it) }.maxOrNull() ?: 0.0
          val theirBest = theirTypes.mapNotNull { defensiveByPokemon[myName]?.get(it) }.maxOrNull() ?: 0.0
          VersusCell(myName, theirName, myBest, theirBest)
      }
  }
  ```
  STAB only (own types, no movepool) — same caveat as F11, state it in the UI.
- `TeamViewModel` additions: `versusResult: List<VersusCell>?`, `isVersusLoading`, `versusTrainer:
  PresetTeam?` on `TeamUiState`. `fun loadVersus(trainer: PresetTeam)`: own tracked job (same
  cancel/rethrow shape as `matrixJob`); resolves the trainer's roster via `repository.getMasterList()`
  (same lookup `loadPreset` already does); fetches `getPokemonTypes` for both rosters and
  `getTypeDetail` for the union of types involved (all `AsyncCache`d, cheap on a warm cache); builds
  `defensiveByPokemon` via `computeDefensiveMultipliers` per member; calls `buildVersusGrid`.
- New `ui/team/MatchupScreen.kt`: a full-screen `Dialog`, same shape as `PresetTeamDialog`/
  `CompareScreen`. Grid: my roster as rows, theirs as columns, split cell (two small numbers) colored
  via `multiplierColors` — change that function's visibility from `private` to `internal` in
  `TeamScreen.kt` (same package) rather than duplicating the palette. One-line verdict above the grid:
  count of cells where `myBest > theirBest` ("favourable") vs `theirBest > myBest` ("unfavourable").
- `PresetTeamDialog` gets `mode: PresetDialogMode = PresetDialogMode.LOAD` (`LOAD` / `COMPARE`) and an
  optional `onCompare: ((PresetTeam) -> Unit)? = null`. Under `COMPARE`, a row tap calls `onCompare`
  directly — the existing "replace vs. new team" `AlertDialog` confirmation is skipped entirely
  (wrapped in `if (mode == PresetDialogMode.LOAD)`), since comparing isn't destructive.
- Entry point: new `IconButton` "Test against a trainer" in `TeamScreen`'s top bar, opens
  `PresetTeamDialog(mode = COMPARE, onCompare = { viewModel.loadVersus(it); showMatchup = true })`.
- Test: `util/TeamVersusTest.kt` — grid shape, correct best-multiplier direction each way, empty
  roster edge cases, same style as `TeamSuggestionsTest.kt`.

## F15 — Preview a Pokémon's impact on the current team's coverage

**Done 2026-08-09.** Built exactly to the finalized plan below, with the extraction/reuse steps
verified to actually land rather than assumed:

- `util/TeamMatrixCalculator.kt`: new `computeTeamMatrices(repository, members): TeamMatrixResult`
  (`TeamMatrixResult(defensive, offensive)`) — the `supervisorScope` body that used to be inlined in
  `TeamViewModel.computeMatrix()` moved out verbatim. `TeamViewModel.computeMatrix()` now just calls
  it and copies the two maps into `TeamUiState`; behavior and exception handling unchanged (its own
  `supervisorScope`/try-catch stays in `computeMatrix`, per plan — the extracted function assumes a
  caller already provides both).
- `util/TypeEffectiveness.kt`: new `sharedWeaknesses(defensiveMatrix, memberNames): List<String>`,
  a twin of the existing `coverageGaps`. `TeamUiState.sharedWeaknesses` is now a one-line delegate to
  it — no behavior change, confirmed by the full existing `TeamViewModel`/Team screen test coverage
  still passing.
- `util/TeamImpact.kt`: `TeamImpactSummary(weaknessesFixed, weaknessesIntroduced, gapsClosed,
  gapsOpened)` + `computeTeamImpact(...)`, plain set differences exactly as planned.
- `PokedexDetailViewModel`: `teamImpact`/`isTeamImpactLoading`/`teamImpactError` on
  `PokedexDetailUiState`; `loadTeamImpact(replacingIndex: Int? = null)` builds `afterMembers` from
  `TeamRepository.team.value` (append or swap at index), computes `computeTeamMatrices` for both the
  current roster and `afterMembers` concurrently (`async` inside one `supervisorScope`, not
  sequential — the plan's "costs nothing extra on a warm cache" held, this doesn't leave one on the
  table either), derives shared-weaknesses/coverage-gaps for both via the two extracted pure
  functions, and calls `computeTeamImpact`. `clearTeamImpact()` cancels the tracked job and resets
  the three fields.
- `PokedexDetailScreen.kt`: new top-bar `IconButton` (`Icons.AutoMirrored.Filled.TrendingUp`,
  "Preview impact on my team"), hidden when `team.isEmpty()`. Team not full → calls
  `loadTeamImpact(null)` directly. Team full → a small inline `AlertDialog` member picker (sprite +
  name per row, tap to pick) rather than reusing `TeamScreen`'s private `TeamMemberChip`, per plan.
  Result shown in its own `AlertDialog`: spinner while loading, error text + Retry (re-issues the
  exact same request via a remembered `pendingReplaceIndex`) on failure, else the four-line summary
  with "no new..."/"no..." wording whenever a list is empty, matching the user's original example
  phrasing. `onDismissRequest` calls `clearTeamImpact()`.
- Tests: `util/TeamImpactTest.kt` (fixed/introduced/closed/opened set-difference cases, plus a
  no-op case) and 4 new `sharedWeaknesses` cases in `TypeEffectivenessTest.kt` (half-or-more weak,
  exact-half tie, a type absent from the matrix, empty team) — mirroring `coverageGaps`' existing
  test shape per plan. No dedicated `PokedexDetailViewModel` test, consistent with the rest of that
  ViewModel's untested wiring (`TeamViewModel`'s F11 wiring wasn't unit-tested either); the two pure
  functions above are where the real logic risk lives, and both are covered.
- `./gradlew compileDebugKotlin` and `./gradlew testDebugUnitTest` both green.

**Revised 2026-08-09** — the user asked to drop the entry-point button entirely and instead show the
preview as an always-on card. The top-bar icon, the full-team replace picker, and the two dialogs are
all removed:

- New card ("Team Coverage Impact") sits in `DetailContent`'s `LazyColumn` between the Abilities card
  and the Type Matchups card, shown only when `team.isNotEmpty() && team.size <
  TeamRepository.MAX_SIZE` — the "team full" replace-target flow that button used to offer is gone
  along with the button, since the card's whole premise is "there's room to add this one directly".
  A Pokémon already on the roster is excluded too (`loadTeamImpact()`'s own self-gate) — nothing to
  preview about "adding" something already there.
  Three body states, same as before: a `CircularProgressIndicator` while loading, red error text on
  failure (no explicit Retry button now — the card recomputes on its own whenever its trigger
  condition re-fires, e.g. the team changing), or the same four-line delta summary
  (`TeamImpactSummaryText`, unchanged).
- `PokedexDetailScreen`: a `LaunchedEffect(uiState.pokemon?.name, team.map { it.name })` calls
  `viewModel.loadTeamImpact()` whenever the card's visibility condition holds and
  `viewModel.clearTeamImpact()` otherwise — keyed on team *membership*, not just size, since swapping
  one member for another externally (e.g. from the Team screen while this page is still open) leaves
  the count unchanged but should still recompute.
- `PokedexDetailViewModel.loadTeamImpact()`: dropped the `replacingIndex` parameter (dead once the
  replace-picker was removed) and made the function self-gating on the same three conditions the
  screen checks, so calling it unconditionally from the `LaunchedEffect` is safe — the two checks
  can't drift into slightly different rules for the same thing. Always computes `afterMembers =
  beforeMembers + candidate` now.
- No new tests needed: the change is pure wiring/UI reshuffling around the same two pure functions
  (`computeTeamMatrices`, `computeTeamImpact`) the original F15 tests already cover.

**Revised again 2026-08-09** — two more readability requests on the card's body text:

- Each of the 4 categories (fixed/introduced/closed/opened) now renders its types as [TypeBadge]
  icons in a `FlowRow`, not a comma-joined plain-text list — matching how every other type list on
  this screen (Type Matchups, Type Triangles) already reads.
- A category with nothing to report is omitted entirely — no more "Would fix no shared
  weaknesses."-style empty-handed lines. `TeamImpactSummaryText` → `ImpactTypeRow(label, types)`
  per category, each returning with no composable output when `types` is empty. A Pokémon whose
  addition changes nothing on any of the 4 now shows no body text at all under the card's title.
- No logic change — `TeamImpactSummary`/`computeTeamImpact` untouched, so no new tests.

Original plan, finalized 2026-08-08 — simplest option chosen for every open question; entry point and
output format agreed with the user:

From a Pokémon's detail screen (`PokedexDetailScreen.kt`), a new top-bar icon — same slot pattern as
the existing shiny toggle and the Compare entry point (`showCompareDialog` /
`loadCompareCandidatesIfNeeded()`) — opens an "impact on my team" preview showing what would change
if this Pokémon were **added** to the active team (team not full) or **replaced** one of its members
(team full, user picks who via a small new picker dialog).

Output is a **text summary** of the delta (not a full before/after matrix): shared weaknesses fixed,
shared weaknesses introduced, coverage gaps closed, coverage gaps opened — e.g. "Would fix these
shared weaknesses: Water, Rock. Would introduce no new shared weaknesses. Would close this coverage
gap: Dragon."

- **Extract, don't duplicate.** `TeamViewModel.computeMatrix()`'s `supervisorScope { ... }` body
  (member matchup fetch + matrix assembly, currently ~60 lines inlined) moves verbatim into a new
  top-level suspend function in `util/TeamMatrixCalculator.kt`:
  `suspend fun computeTeamMatrices(repository: PokedexRepository, members: List<NamedApiResource>):
  TeamMatrixResult` (`TeamMatrixResult(defensive, offensive)`, same shape `computeMatrix` builds
  today). `TeamViewModel.computeMatrix` calls this instead of the inline block — behavior and
  exception handling unchanged. Being a plain top-level function (not a `TeamViewModel` method), it's
  directly callable from `PokedexDetailViewModel` with no cross-ViewModel dependency. Full
  movepool-based accuracy, **not** F11's STAB-only shortcut — this is one hypothetical team of ≤6,
  not a 1300-candidate scan, so the cost is affordable.
- **Reuse the "at least half weak" / "no member hits it" rules**, don't reimplement them.
  `TeamUiState.sharedWeaknesses`'s logic (weak-count ≥ half the roster) currently lives inline as a
  private getter; extract it into `util/TypeEffectiveness.kt` as a twin of the existing
  `coverageGaps(offensiveMatrix, memberNames)`: `fun sharedWeaknesses(defensiveMatrix, memberNames):
  List<String>`. `TeamUiState.sharedWeaknesses` becomes a one-line delegate to it — a small refactor
  with no behavior change, giving both `TeamViewModel` and `PokedexDetailViewModel` one source of
  truth.
- New pure `util/TeamImpact.kt`: `data class TeamImpactSummary(weaknessesFixed: List<String>,
  weaknessesIntroduced: List<String>, gapsClosed: List<String>, gapsOpened: List<String>)` +
  `fun computeTeamImpact(beforeSharedWeaknesses, afterSharedWeaknesses, beforeCoverageGaps,
  afterCoverageGaps): TeamImpactSummary` — plain set differences (fixed = in before, not in after;
  introduced = in after, not in before; same shape for gaps).
- `PokedexDetailViewModel` additions: `teamImpact: TeamImpactSummary?`, `isTeamImpactLoading`,
  `teamImpactError: String?` on `PokedexDetailUiState`. `fun loadTeamImpact(replacingIndex: Int? =
  null)`: own tracked job (same cancel/rethrow shape as every other job in this codebase); builds
  `afterMembers` from `TeamRepository.team.value` (append the candidate if `replacingIndex == null`,
  swap it in at that index otherwise); calls `computeTeamMatrices` **twice** — once for the current
  roster, once for `afterMembers` (simplest option: recomputing "before" costs nothing extra in
  practice, since every fetch involved is `AsyncCache`d and near-instant on a warm cache — no need to
  thread `TeamViewModel`'s already-computed matrix across ViewModels); derives
  `sharedWeaknesses`/`coverageGaps` for both from the two `TeamMatrixResult`s; calls
  `computeTeamImpact`. `fun clearTeamImpact()` resets the three fields, called when the preview
  dialog is dismissed so reopening it for a different replace target doesn't flash stale data.
- UI: `PokedexDetailScreen.kt` top bar gets one more `IconButton` (e.g. `Icons.AutoMirrored.Filled
  .TrendingUp`, "Preview impact on my team"), hidden entirely when `team.isEmpty()` (the screen
  already collects `team` for the existing add-to-team button). On tap: if `team.size <
  TeamRepository.MAX_SIZE`, call `loadTeamImpact(null)` directly; if full, open a small new
  `AlertDialog` + sprite row (not a reuse of `TeamScreen`'s private `TeamMemberChip` — cross-package
  reuse there would point `ui.detail` at `ui.team` for one tiny composable, simpler to write a
  ~15-line picker inline) letting the user pick which member to replace, then call
  `loadTeamImpact(pickedIndex)`. Result shown in an `AlertDialog`: loading spinner while
  `isTeamImpactLoading`, `teamImpactError` text + retry on failure, else the four-line summary
  ("Would fix...", "Would introduce no new...", "Would close...", "Would open no new..." — "no new"
  wording whenever a list is empty, matching the user's example). `onDismissRequest` calls
  `clearTeamImpact()`.
- Tests: `util/TeamImpactTest.kt` (set-difference cases, no-op case) and new `sharedWeaknesses` cases
  added to `TypeEffectivenessTest.kt` (mirroring the existing `coverageGaps` tests: half-or-more weak,
  ties, empty team). No dedicated `PokedexDetailViewModel` test — no such test file exists today, and
  `TeamViewModel`'s own F11 wiring wasn't unit-tested either; the two pure-function suites above are
  where the real logic risk is.

**Revised a third time 2026-08-09** — a loaded result where all 4 categories are empty (this
Pokémon genuinely changes nothing about the team's coverage) used to leave the generic subtitle
("What adding this Pokémon to your active team would change.") as the only text on an otherwise
blank card body, reading as if it had silently failed to load. `TeamImpactCard` now computes
`hasNoImpact` (all 4 of `TeamImpactSummary`'s lists empty) and swaps that subtitle for "Nothing."
in that case, skipping the now-pointless call to `TeamImpactSummaryText`. No logic change, no new
tests — same `TeamImpactSummary` shape, just one more UI branch on it.

**Revised a fourth time 2026-08-09** — the 4 rows (fix/introduce weaknesses, close/open gaps) read
as 4 same-looking lines with no hint that the first pair is this Pokémon's *defensive* contribution
and the second pair is its *offensive* one. `TeamImpactSummaryText` now groups them under two
headings, "Defensively" (fixes/introduces shared weaknesses) and "Offensively" (closes/opens
coverage gaps), via a new `ImpactSection(heading, vararg rows)` that also omits the whole section
(heading included) when both of its rows are empty — not just each row individually as before. No
logic change, no new tests.

**Bug fixed 2026-08-09** — user-reported: adding Toedscool (Ground/Grass) to Blaine's all-Fire
preset team correctly showed the offensive side but said nothing on defense, even though Toedscool
brings the team's first immunity to Electric and its own ×4 weakness to Ice. Root cause:
`weaknessesFixed`/`weaknessesIntroduced` are majority-based (`sharedWeaknesses` requires at least
half the roster to share a weakness) — a real systemic-risk signal, but blind to a single new
member's own severe typing, which never moves a 5-6-Pokémon team's majority on its own.

- `util/TypeEffectiveness.kt`: two new pure functions parallel to `sharedWeaknesses`/`coverageGaps`
  but per-member rather than majority-based — `teamImmunities(defensiveMatrix, memberNames)` (types
  at least one member is immune, 0x, to) and `teamQuadWeaknesses(...)` (types at least one member is
  ×4 weak to). A lone immunity is already a real asset and a lone ×4 weakness a real liability,
  regardless of how many teammates share it.
- `util/TeamImpact.kt`: `TeamImpactSummary` gains `immunitiesGained`/`quadWeaknessesGained`;
  `computeTeamImpact` takes the before/after immunity and quad-weakness lists and set-diffs them,
  same shape as the other 2 axes. Gained-only (no symmetric "lost" case) is correct here: the
  screen only ever *adds* a candidate, never removes one, and a team's collective min (immunity)
  and max (quad weakness) multiplier per type can only move toward "more covered" as members are
  added, never regress.
- `PokedexDetailViewModel.loadTeamImpact()`: computes `teamImmunities`/`teamQuadWeaknesses` for both
  rosters alongside the existing `sharedWeaknesses`/`coverageGaps` calls, same before/after pattern.
- `PokedexDetailScreen.kt`: "Defensively" section gains two more rows, "Adds an immunity to:" and
  "Adds a severe (×4) weakness to:", using the same `ImpactTypeRow` (type badges, omitted when
  empty) as the other four. `TeamImpactCard`'s `hasNoImpact` check (for the "Nothing." case) now
  covers all 6 lists, not 4.
- Tests: `TeamImpactTest.kt` rewritten around a `impact(...)` helper defaulting every axis to "no
  change" so each case only spells out what it exercises, plus 3 new cases for the two gained-only
  axes. `TypeEffectivenessTest.kt` gains 5 cases for `teamImmunities`/`teamQuadWeaknesses` (single
  member is enough, a double weakness doesn't count as quad, absent-from-matrix, empty team) —
  regression coverage for the exact bug reported (a lone immune/quad-weak member being missed).

**Bug fixed again 2026-08-09** — user-reported: adding Kingdra (Water/Dragon) to Blaine's all-Fire
preset team didn't mention it bringing its own ½x resistance to Water. Same root cause and shape as
the immunity/quad-weakness fix above, just the third and last cell of the same 0/½·¼/1/2/4
multiplier spectrum left uncovered by a per-member (not majority-based) signal.

- `util/TypeEffectiveness.kt`: `teamResistances(defensiveMatrix, memberNames)` — types at least one
  member resists (½x or ¼x), explicitly excluding 0x since that's already `teamImmunities`'
  territory rather than folded in as "any resistance".
- `util/TeamImpact.kt`: `TeamImpactSummary` gains `resistancesGained`; `computeTeamImpact` takes the
  before/after resistance lists and set-diffs them, same shape as the other two per-member axes.
  Gained-only again for the same reason (add-only flow, a team's collective min multiplier per type
  can only improve as members are added).
- `PokedexDetailViewModel.loadTeamImpact()`: computes `teamResistances` for both rosters alongside
  the other 3 pairs.
- `PokedexDetailScreen.kt`: "Defensively" section gains "Adds a resistance to:" between the
  immunity and ×4-weakness rows (0x → ½x/¼x → ×4, matching the multiplier's own severity order).
  `hasNoImpact` now covers all 7 lists.
- Tests: `TeamImpactTest.kt`'s `impact(...)` helper gains the two resistance parameters (still
  defaulted to "no change"), plus a gained-only case and both existing "already present"/"unchanged"
  cases extended to also cover resistances. `TypeEffectivenessTest.kt` gains 5 cases for
  `teamResistances` (single member, ¼x counts too, 0x is not double-counted as a resistance,
  absent-from-matrix, empty team).


**Done 2026-08-08.** From `PokedexDetailScreen.kt`, swipe left/right (or tap a chevron) to move to
the adjacent Pokémon without backing out to the list and re-selecting.

- "Adjacent" by what ordering — **not** the plan's original "always `getMasterList()`'s order
  regardless of entry point" simplest-option choice. The user explicitly asked for filtered-list
  awareness instead: swiping from a Pokémon reached via the Pokédex list's active filter/sort stays
  inside that filtered set (e.g. filtered to Fire types, swiping only ever visits other Fire types),
  falling back to master list order for every other entry point (an evolution chain tap, Compare, a
  team member chip) or before the list screen has loaded this session.
  - New `data/PokedexListContext.kt`: in-memory singleton (no persistence needed, unlike
    `FavoritesRepository`/`TeamRepository`) holding `StateFlow<List<String>>` of the list screen's
    current filtered/sorted names.
  - `PokedexListViewModel` collects its own `displayedPokemon` in `init` and pushes the names into
    `PokedexListContext.update(...)` on every change.
  - `util/AdjacentPokemon.kt`: `fun adjacentNames(names: List<String>, currentName: String):
    Pair<String?, String?>` (previous, next; null at either end or if not found) plus `fun
    namesForAdjacency(displayedNames, masterNames, currentName): List<String>` — picks
    `displayedNames` when `currentName` is actually part of it, else `masterNames`. Tests:
    `AdjacentPokemonTest` — both functions, including the fallback and "not filtered in" cases.
  - `PokedexDetailViewModel.load()`: reads `PokedexListContext.displayedNames.value`, resolves
    `repository.getMasterList()` (already cached by the time most detail screens open) as the
    fallback, and calls `namesForAdjacency` + `adjacentNames` — best-effort, leaves both null on
    failure rather than blocking the rest of `load()`.
- `PokedexDetailScreen.kt`: new `onNavigateAdjacent: (String) -> Unit` param, distinct from the
  existing `onPokemonClick` (which *pushes* a new detail screen — used for cross-references like
  evolution stages, where Back should return to the page you tapped from). Two pieces:
  - `Modifier.pointerInput(pokemonNameOrId, previousName, nextName) { detectHorizontalDragGestures(...) }`
    on the content `Box`, committing to `onNavigateAdjacent(next/previous)` past an 80dp drag
    threshold, no-op at either end of the list (name null).
  - A chevron pair (`Icons.Filled.ChevronLeft`/`ChevronRight`) pinned level with the sprite (not
    vertically centered on the whole screen, per user feedback — that landed on top of scrolling
    body text like the flavor paragraph lower down the page), each wrapped in a small elevated
    `Surface` circle so it reads as a floating control rather than a stray icon drawn over whatever
    content happens to be underneath. Shown only when that direction's name is non-null.
- `PokedexNavHost.kt`: wires `onNavigateAdjacent = { name -> ifIdle { navController.navigate(
  "detail/$name") { popUpTo(ROUTE_DETAIL) { inclusive = true } } } }` — replaces the current
  back-stack entry rather than pushing (same `popUpTo(...){inclusive=true}` pattern the Compare
  screen's own re-navigate already uses), so Back always returns to wherever the user actually
  entered the detail flow from, not back through every Pokémon swiped past on the way.
- Verified manually on the emulator: filtered the list to Fire types, opened Charmander, swiped
  through Charmander → Charmeleon → Charizard → **Vulpix (#37)**, confirming the chevron skips
  Squirtle/Wartortle/Blastoise (#7-9, the master-list neighbors) and stays inside the filter.

## F23 — Fix unreachable coverage matrix on Team screen

**Done 2026-08-08.** User-reported bug: on the Team screen, scrolling down no longer reached the
type coverage matrix.

- **Root cause.** `036fe14` ("Make the team coverage matrix reachable in landscape") introduced a
  `compact` layout switch: below `COMPACT_LAYOUT_MIN_HEIGHT` (400dp) of *total viewport* height,
  the whole screen scrolls as one page; above it, the matrix gets whatever space the header leaves
  over, in its own scrollable viewport, and the outer page doesn't scroll at all. That threshold
  compared total height against a hardcoded guess of the header's size (~250dp) — not the header's
  actual, measured height. F21/F22 (tier-ceiling line, wider suggestion tiles, multi-line "why"
  text) grew the real header past that guess, so on an ordinary portrait phone (well over 400dp
  total) the non-compact branch kept being chosen even though the grown header now left ~0dp for
  the matrix — and with the page itself not scrolling, there was no gesture left to reach it.
- **Fix.** Replaced the static threshold with a measured one. The header content (team chips
  through the sprite row, everything above the matrix grid) is wrapped in a `Column` with
  `Modifier.onGloballyPositioned` reporting its real height; `compact` now compares *remaining*
  space (`maxHeight - measuredHeaderHeight`) against a 150dp floor
  (`COMPACT_LAYOUT_MIN_REMAINING_HEIGHT`), pulled out as pure function `isCompactMatrixLayout` in
  new `util/TeamMatrixLayout.kt` so the decision has a regression test independent of Compose.
- Tests: `util/TeamMatrixLayoutTest.kt` — plenty of room stays non-compact, a tall viewport with a
  header that leaves too little room goes compact (the exact regression), header taller than the
  viewport, and the floor's boundary (exactly at it vs. just above).

## F22 — Suggestion "why" text and impact-based sort

**Done 2026-08-08.** Follow-up to F21: sorting Suggestions by stat total no longer made sense once
a tier ceiling was in play, and a tile never explained *why* that particular Pokémon was suggested
— the user had to work it out from the type badges against the card's general "would help both..."
subtitle.

- `TeamSuggestion` gains `weaknessesResisted: List<String>` and `gapsHit: List<String>` — exactly
  which of the team's shared weaknesses/coverage gaps this candidate qualified on, not the full
  input lists.
- `util/TeamSuggestions.kt`: `qualifies()` refactored into `qualification()`, returning those two
  lists (null unless both non-empty, same "both required" rule as before) instead of a bare
  `Boolean`; `qualifies()` kept as a thin wrapper for `findConflictingForms`, unchanged.
- `rankSuggestions` sorts by total impact (`weaknessesResisted.size + gapsHit.size`) descending,
  stat total ascending as a tiebreak — the most useful pick leads, no longer just the weakest one.
  Default `limit` lowered from 10 to 6 (user feedback: 10 was too many to page through).
- `SuggestionTile` (`TeamScreen.kt`) shows a "Resists water · Hits dragon"-style line under the
  type badges — tile widened 76dp → 96dp to fit it.
- Tests: `TeamSuggestionsTest` — impact beats stat total, exact resisted/hit lists (not the full
  input), default limit is 6.

## F21 — Suggestion tier ceiling filter

**Done 2026-08-08.** Team builder Suggestions were sorted by base stat total, which surfaced
Pokémon far too weak to be a realistic pick; requested a way to cap them to a competitive tier
instead.

- `util/SmogonTierLabels.isAtOrBelowCeiling(tier, ceiling)`: "this tier or below" rule (a UU ceiling
  allows UU, RU, NU... not OU/Uber); an unrecognized tier is treated as weaker than every known one.
- `util/TeamSuggestions.filterByTierCeiling(candidates, maxTier, tierByShowdownKey)`: pure filter
  step ahead of `rankSuggestions`; a candidate with no tier entry (unclassified on Showdown) is kept
  rather than excluded.
- `data/SuggestionSettings.kt`: `SharedPreferences`-backed `StateFlow<String?>` for the chosen
  ceiling (null = no limit, the default), same pattern as `PrefetchSettings`.
- `TeamViewModel.loadSuggestions()`: fetches Gen 9 (`"sv"`) Smogon tiers only when a ceiling is set,
  applies the filter before ranking; also collects `SuggestionSettings.maxTier` directly in `init`
  (not just on team changes) since this ViewModel survives a tab switch and Settings lives on a
  different tab.
- Settings screen: new "Team suggestions" section with a tier picker (reuses `OptionsDialog`).
- Team screen: `SuggestionsCard` shows "Limited to `<tier>` and below (Settings)" when a ceiling is
  active, so the card explains itself without the user needing to remember a setting on another tab.
- Tests: `SmogonTierLabelsTest`, tier-ceiling cases added to `TeamSuggestionsTest`.

## F24 — Reorder detail screen sections

**Done 2026-08-09.** Implemented autonomously (BACKLOG.md batch: F24/F25/F26/F27/F28 on
`feature/backlog-f24-f28`) — landed last in the batch, after F25/F26 had already settled the final
shape of the Smogon and Type Triangles cards this reorder moves around.

**One gap in the original spec, resolved.** The requested order never mentioned the Abilities or
Type Matchups cards, which the actual page has always had between Base Stats and the
Team-Impact/Triangle/Smogon cluster — an oversight in the original ask, not a real ambiguity once
noticed. Resolved by treating them as part of the same "core stat block" as Base Stats (both are
this Pokémon's own always-present profile data, same as Base Stats, unlike Team Impact/Triangles/
Smogon which are either conditional or link out) and leaving them exactly where they already were:
right after Base Stats, before Evolution.

**Final order** (sprite/description and Base Stats unchanged at the top, moves unchanged at the
bottom, per the original spec): sprite/description → Base Stats → Abilities → Type Matchups →
**Evolution (+ Mega Evolution)** → Team Impact (F15, conditional) → Type Triangles (conditional,
F26) → Smogon Strategy Dex (F25) → Level Up → TM/HM → Breeding → Tutor. Net change from before:
only the Evolution card moved, from after Smogon to right after Type Matchups — every other card
kept its existing relative order, exactly as the original "net effect" paragraph specified.

- Single change in `PokedexDetailScreen.kt`: the `if (evolutionChain != null || megaEvolutions.isNotEmpty())`
  item block cut from after `SmogonLinksCard(...)` and reinserted right after the
  `TypeMatchupsCard(typeMatchups)` item, ahead of the `showTeamImpactCard`/`counteredTriangles`/
  `SmogonLinksCard` block. No composable bodies changed, just their order in the `LazyColumn`.
- No `MIN_TYPE_TRIANGLES_BEFORE_COLLAPSE`-style scroll-position assumptions existed to update — that
  was speculative in the original ask; F26 already removed the Type Triangles card's own
  collapse/expand state entirely, and nothing else in the file assumed a fixed card order.
- Verified on the emulator on Charizard (has Evolution + Mega Evolution + Type Triangles + Smogon,
  the busiest realistic case): scrolled the full page top to bottom, confirmed the order above,
  swipe-to-adjacent-Pokémon (F16) and the move-category expand/collapse both still worked normally.
- No new tests: pure reordering of existing composables, no new logic to cover.

## F25 — Shrink Smogon Strategy Dex card

**Done 2026-08-09.** Implemented autonomously (BACKLOG.md batch: F24/F25/F26/F27/F28 on
`feature/backlog-f24-f28`), landed as its own independent, smaller change ahead of F24's reordering
rather than folded into it — the two touch the same file but are otherwise unrelated.

Went with the first option from the original list, confirmed correct by the on-device check: the
double-16dp (outer `Card` padding + inner `Column` padding) really was the biggest lever.

- `SmogonLinksCard`'s inner `Column` padding: `16.dp` → `12.dp` (outer `Card` padding left at
  `16.dp`, unchanged, so this card's outer margin still lines up with every other card on the page).
- Title's bottom gap and the chip `FlowRow`'s own spacing: `8.dp` → `6.dp` each.
- Chip label switched to `labelMedium` (from the `AssistChip` default) and the trailing external-link
  icon shrunk `16.dp` → `14.dp`.
- **Not done:** forcing the chip's own height below Material3's default 32dp. Tried it first, but
  `AssistChip`'s internal padding assumes that height — a smaller explicit height risked clipping the
  label/icon rather than actually saving visible space, so reverted before it ever reached the
  emulator.
- Verified on-device on Aegislash (4 Smogon generation links, the card's worst case for stacking):
  the 4 chips now wrap 2-per-row instead of each taking a full line, visibly tighter without any
  clipped text or icons.

## F26 — Simplify Type Triangles card to perfect-counter-only

**Done 2026-08-09.** Implemented autonomously (BACKLOG.md batch: F24/F25/F26/F27/F28 on
`feature/backlog-f24-f28`) — decisions below made without further user confirmation, per that
session's mandate.

**Open questions resolved:**

- **"Perfect counter" = `counteredBy` match, not additionally filtered on `TypeTriangle.isPerfect`.**
  `counteredBy` already means "this typing is the exact best-counter dual-type for this triangle" —
  a property of the *typing*, independent of whether the *triangle itself* is symmetric
  (`isPerfect`). Verified on-device with Aegislash (Ghost/Steel): its card correctly shows both
  Fighting/Rock/Flying (Perfect) and Fighting/Ice/Flying (Imperfect) as things it counters, matching
  the pre-existing doc comment's own example.
- **`PokedexDetailViewModel` simplified as anticipated.** `memberTriangles` field, its
  `TypeTriangles.containing(pokemonTypes)` call, and the `PokedexDetailUiState` field are all
  removed — only `counteredTriangles` remains. `TypeTriangles.containing()` itself deleted from
  `util/TypeTriangles.kt` too: unused anywhere else in the app (checked before deleting), and no
  existing test referenced it either.
- **`TypeTrianglesScreen.kt` (the "View chart" full screen) left untouched** — out of scope, per the
  original ask reading "the triangle card" as the embedded detail-screen card only.
- **Collapse/expand state and `COLLAPSED_TRIANGLE_LIMIT` dropped entirely**, not kept for the single
  remaining list — each triangle's counter typing is fixed and no typing counters more than a
  couple of triangles in practice, so the "show all" affordance had nothing left to guard against.
- **Per-row "This typing is the best counter to this triangle." note removed too** — redundant once
  every row in the card is, by construction, something this typing counters; the card-level header
  ("This typing is the best counter to:") already says it once.

**Verified on the emulator**, both directions: Kubfu (pure Fighting, a *member* of 2 triangles but
the exact counter to none) now shows no Type Triangles card at all — goes straight from Type
Matchups to Smogon Strategy Dex. Aegislash Shield (Ghost/Steel, the counter to 2 triangles) shows
both, uncollapsed, with the redundant per-row note gone.

Original ask, requested 2026-08-09 — `TypeTrianglesCard` (`PokedexDetailScreen.kt`, around line 924)
used to show two sections for a
Pokémon's typing: triangles it *counters* (`TypeTriangles.counteredBy(types)` — its typing exactly
matches a triangle's best-counter pair, "This typing is the best counter to:") and triangles it's
merely *a member of* (`TypeTriangles.containing(types)` — one of its types appears somewhere in the
3-type loop, regardless of whether it's a good defensive answer to it). Both lists get combined,
capped at `COLLAPSED_TRIANGLE_LIMIT` with a "show all" expand, per the doc comment around line 916-921.

Request: drop the "member of" section (`memberTriangles`/`containing(...)`) entirely — a Pokémon
merely sharing a type with one leg of a triangle isn't a meaningful callout on its own. Keep only the
counter section: show the card **only when** the Pokémon's typing is an exact match to a triangle's
`counter.types` (i.e. `TypeTriangles.counteredBy(types)` is non-empty), and hide the whole card
otherwise instead of falling back to listing membership. See "Open questions resolved" above for how
each open question from this original spec was settled.

## F27 — Tap a suggestion's sprite to open its detail page

**Done 2026-08-09.** Implemented autonomously (BACKLOG.md batch: F24/F25/F26/F27/F28 on
`feature/backlog-f24-f28`) — every "not yet scoped" question below resolved as its own noted
likely default, without further user confirmation, per that session's mandate.

- **Plain push, not `popUpTo`.** `TeamScreen` gains `onPokemonClick: (String) -> Unit`; wired in
  `PokedexNavHost.kt`'s `TeamScreen(...)` call as `{ name -> ifIdle { navController.navigate("detail/$name") } }`
  — the same plain-push pattern the Pokédex list's own `onPokemonClick` uses, so Back returns to the
  Team screen. No `popUpTo`/back-stack replacement (that's F16's adjacent-swipe case, not this one).
- **Sprite-only, not the whole tile.** `SuggestionTile` gains an `onSpriteClick: () -> Unit` param,
  applied as `Modifier.clickable(onClick = onSpriteClick)` on just the `PokemonSprite`, leaving the
  `+` `IconButton`'s own tap target and the rest of the tile (name/BST/badges/"why" text) inert —
  matching the original ask's literal "le sprite".
- **Scope stayed to the Suggestions row only** — team member chips elsewhere on the Team screen
  (`AddMemberChip`/`TeamMemberChip`) untouched, as the original spec anticipated.
- Threaded through `TeamScreen` → `SuggestionsCard` → `SuggestionTile` → `PokedexNavHost.kt`.
- Verified on the emulator: loaded Blaine's preset team, scrolled to Suggestions, tapped
  Toedscool's sprite — opens Toedscool's detail page; Back returns to the Team screen.
- No new tests: pure navigation wiring, no new pure-function logic to cover.

## F28 — Bug: can't open Urshifu's detail from Kubfu's Evolution card

**Fixed 2026-08-09.** Implemented autonomously (BACKLOG.md batch: F24/F25/F26/F27/F28 on
`feature/backlog-f24-f28`).

**Repro confirmed on the emulator** before writing the fix: Pokédex → search "kubfu" → open Kubfu →
scroll to Evolution → tap Urshifu → **nothing happens**, screen stays on Kubfu (silent no-op, no
error, no crash) — narrowing down which of the 3 "not yet scoped" symptoms this was.

**Root cause confirmed**, exactly as suspected: `api.getPokemon("urshifu")` 404s (Urshifu has no
bare-name Pokémon resource, only `urshifu-single-strike`/`urshifu-rapid-strike`), which
`PokedexDetailViewModel.load()`'s catch block turns into the generic "Couldn't load this Pokémon.
Check your connection." error — misleading text for a request that never touched connectivity, but
that's this app's existing generic-error pattern everywhere, not something this fix's scope covers.
(The apparent "silent no-op" on first repro attempt turned out to be two mistaken adb tap coordinates
in a row — screenshots are scaled 1.2x from device pixels — not a second bug; `uiautomator dump`
gave the real bounds, and the actual behavior is the error screen described above.)

**Fix — resolved in the repository, not at tap time.** Of the 3 mechanisms floated
(evolution-chain-parse time / tap time / inside `getPokemonDetailBundle`), the repository lookup won:
it fixes the bug for every bare-species-name call site at once (not just evolution taps), and costs
nothing extra for the ~99% of names that resolve directly.

- `PokedexRepository.kt`: new private `fetchPokemonResolvingDefaultVariety(nameOrId)`, used by
  `getPokemonDetailBundle()` in place of the bare `api.getPokemon(nameOrId)` call. Tries the direct
  fetch first; on an `HttpException` with code 404 specifically (any other failure rethrows
  untouched), falls back to `api.getPokemonSpecies(nameOrId)` (which *does* resolve for a bare
  species name) and retries with `varieties.firstOrNull { it.isDefault }?.pokemon?.name` — the same
  `is_default` resolution the F20 dataset pipeline already uses for Deoxys/Giratina-style species. If
  the species has no default variety either (shouldn't happen, but not assumed), the original 404
  rethrows rather than swallowing it into a confusing empty state.
- Scoped correctly per the "not yet scoped" list: this fixes every split-form species reachable via
  an evolution chain, not just Urshifu specifically, since the fallback triggers on the 404 itself
  rather than a Urshifu-specific name check.
- Test: new `data/repository/PokedexRepositoryTest.kt` (first repository-level test in the
  project) — a hand-written `PokeApiService` fake per BACKLOG convention (no mocking library in this
  project), 3 cases: the Urshifu repro (species-only name falls back to its default variety), an
  ordinary name resolves directly with no fallback call at all (regression guard against the fallback
  firing when it shouldn't), and a genuinely nonexistent name still fails loudly rather than the
  fallback swallowing it. `HttpException` built via `retrofit2.Response.error(404, ...)`, matching
  what Retrofit's suspend functions actually throw on a non-2xx response.
- Verified end-to-end on the emulator post-fix: Kubfu → Evolution → tap Urshifu now opens **Urshifu
  Single Strike** (#0892) correctly.

## F29 — Bug: Evolution card gives no way to discover Ursaluna Bloodmoon (and other one-off forms)

**Fixed 2026-08-09.** User-reported: opening Ursaluna's page gives no hint that Ursaluna Bloodmoon
exists or how to reach it — nothing on the Evolution card mentions it.

**Root cause.** Ursaluna Bloodmoon is a `pokemon-species` *variety* of `ursaluna` (like a Mega
Evolution is), not a further step in the evolution chain — PokeAPI's `evolution-chain` data for
this family stops at `ursaluna` with zero `evolves_to` entries. Confirmed directly against the API
(`GET /pokemon-species/ursaluna`'s `varieties` includes both `ursaluna` (default) and
`ursaluna-bloodmoon`, but `GET /evolution-chain/110` never mentions the latter). It's genuinely
reachable in the Pokédex today (`#10272`, searchable by name, its own full detail page) — just
invisible from Ursaluna's *Evolution card* specifically, which is where a user would naturally look
for "how do I get the other form of this Pokémon". The existing "Mega Evolution" section already
solved this exact problem for one case (`SpeciesDto.megaEvolutions`, filtering `varieties` for names
containing `"-mega"`) but never generalized — Gigantamax forms had the identical invisibility bug,
just not yet reported.

Asked the user how far to take the fix; chose "generalize to all forms" over a Bloodmoon-only patch.

- `SpeciesDto.kt`: `megaEvolutions` (filtered by name) replaced with `otherForms` — every
  `varieties` entry that isn't the default one, full stop. Covers Mega, Gigantamax, and one-off
  forms like Bloodmoon in one property, since all three are the same underlying case (a variety
  PokeAPI doesn't model as an evolution step).
- `PokedexDetailScreen.kt`: the Evolution card's "Mega Evolution" section (header + the
  Mega-specific "A temporary in-battle form, not a permanent evolution." subtitle) replaced with a
  generic "Other Forms" section — header text now "Alternate forms of this species, not covered by
  the evolution steps above.", deliberately not claiming to explain *how* each is obtained, since
  PokeAPI has no field for that on a non-evolution variety and fabricating an explanation would be
  worse than omitting one. Same `PokemonSpriteTile` grid as before, so tapping any listed form still
  navigates to its own detail page (including back to the default variety, when viewing a form).
  The whole Evolution card's visibility condition (`evolutionChain != null || ...`) now checks
  `otherForms.isNotEmpty()` instead of the old Mega-only check.
- Verified on the emulator: Ursaluna's page now shows "Other Forms" → Ursaluna Bloodmoon, tapping it
  opens Bloodmoon's detail page (#10272) correctly; Charizard's Mega X/Mega Y still show under the
  same section (spot-checked the code path, not re-screenshotted — identical rendering logic, only
  the filter changed).
- No new tests: `otherForms` is a one-line `filterNot` with no branching logic to cover beyond what
  compiling against real API shapes already confirms.

## F30 — Bottom nav bar too tall in portrait mode

**To groom** — requested 2026-08-09, not yet planned or implemented.

User feedback: in portrait orientation, the bottom navigation bar (Pokédex / Triangles / Team /
Settings, `NavigationBar` wired up in `PokedexNavHost.kt`) takes up too much vertical screen space.

Not yet scoped: exact current height vs. target, whether to drop the text labels (icon-only, always
or only when unselected), reduce `NavigationBarItem` internal padding, or a Material3
`NavigationBarDefaults` override — no measurements or before/after taken yet, no code looked at.

## F31 — Top app bar too tall in portrait mode

**To groom** — requested 2026-08-09, not yet planned or implemented.

User feedback: in portrait orientation, the top bar showing "PikaDex" takes up too much vertical
screen space.

Not yet scoped: which screen(s) this refers to (the Pokédex list's large `"PikaDex"` title looks
different from the smaller per-Pokémon `TopAppBar` on the detail screen — worth confirming which one
before touching anything), target height, and whether this is a `TopAppBar` vs `LargeTopAppBar`/
`CenterAlignedTopAppBar` sizing choice or just excess padding — no code looked at yet.

## F32 — Survey: PokeAPI data fetched but not yet used

**To groom** — requested 2026-08-09. Research only, nothing implemented. Compiled by diffing every
DTO in `data/remote/dto/` against the live PokeAPI response for each endpoint the app already calls
(`pokemon/6`, `pokemon-species/6`, `move/1`, `ability/1`, `type/1`), plus the full endpoint list at
`GET /api/v2/`. Each item below is a grooming candidate, not a commitment — pick from this list when
deciding what's next, don't treat it as a queue.

### Unused fields on resources the app already fetches (no new request needed)

- **`PokemonDto`**: `held_items` (wild-encounter held items — a "held item" fact card), `cries`
  (Gen 9 added `cries.latest`/`cries.legacy` audio URLs — a play-cry button), `forms` (list of form
  URLs, distinct from `species.varieties`), `game_indices`, `past_types`/`past_abilities` (a
  Pokémon's typing/abilities in older generations, e.g. pre-Fairy-type Clefable) — the last one is
  the most interesting: a "this used to be different" note tied to the existing Smogon
  generation-links pattern.
- **`PokemonSprites`**: only `front_default`/`front_shiny`/`official-artwork` read today. Unused:
  `back_default`/`back_shiny` (+ their `_female` variants), `sprites.other.home` (higher-res HOME
  renders), `sprites.other["dream_world"]` (art style, Gen 5-era), `sprites.other.showdown`
  (animated battle sprites — Sprites.kt already builds GBA-style RR sprites by hand, so
  `showdown` is the one source of *animated* sprites without a bespoke asset pipeline; if this ever
  gets picked up, the natural fit is a toggle on the detail screen next to the existing shiny
  toggle, not the Pokédex grid or Team/Suggestions tiles — Showdown sprites are the same small
  scale as `PokemonSprite`, not the large official artwork, and dozens of simultaneously-looping
  GIFs in a list would be a real perf/battery cost for little gain at that size),
  `sprites.versions` (per-generation historical sprites — a "how this Pokémon looked across games"
  strip).
- **`PokemonSpeciesDto`**: `capture_rate`, `base_happiness`, `growth_rate` (leveling curve),
  `gender_rate`, `has_gender_differences`, `hatch_counter`, `shape`, `habitat`, `is_baby`,
  `evolves_from_species`, `pokedex_numbers` (per-regional-dex numbering, not just national),
  `pal_park_encounters`, `names` (species name in other languages — an in-app language picker).
  `capture_rate`/`base_happiness`/`growth_rate`/`gender_rate` together would round out the detail
  screen's existing Height/Weight/Egg Groups row into a fuller "Breeding & Capture" info block.
- **`MoveDetailDto`**: `priority` (e.g. Quick Attack's +1 — currently invisible even though damage
  class/power/accuracy/PP are all shown), `target` (single foe / all foes / user / field...),
  `meta` (critical-hit rate, status ailment inflicted + its chance, drain/healing %, flinch
  chance, stat changes — genuinely useful competitive info absent from every move row today),
  `contest_type`/`contest_effect`/`super_contest_effect` (Contest stats, unlikely to be worth it
  outside a dedicated Contests feature), `generation` (first game a move appeared in),
  `effect_chance` (secondary effect probability, pairs with `effect_entries`' text).
- **`AbilityDetailDto`**: `generation`, `is_main_series` (filters out Colosseum/XD-only abilities),
  `flavor_text_entries` (an in-game flavor blurb, same shape as species flavor text already shown).
- **`TypeDetailDto`**: `generation` (when a type's damage relations last changed — relevant since
  `past_damage_relations` exists precisely because they've shifted, e.g. Steel resisting Ghost/Dark
  pre-Gen-6), `past_damage_relations` (older-generation matchups, same "this used to be different"
  idea as `PokemonDto.past_types`), `moves` (every move of this type, redundant with the existing
  per-move `type` field so low value), `sprites` (small type-icon images — `TypeBadge.kt` currently
  builds its own icon URL by convention rather than reading this).
- **`EvolutionDetail`** (`util/EvolutionUtils.kt`'s `describeEvolutionDetail`): only 8 of its ~20
  real fields are read (`trigger`, `min_level`, `item`, `held_item`, `known_move`, `min_happiness`,
  `min_beauty`, `time_of_day`, `needs_overworld_rain`). Unused: `location` (region-specific
  evolutions, e.g. Magneton at Mt. Coronet), `min_affection`, `party_species`/`party_type`
  (evolve-with-a-specific-teammate, e.g. Mantyke needs a Remoraid in-party), `trade_species`
  (species-specific trade evolutions, e.g. Karrablast/Shelmet), `relative_physical_stats`
  (Attack-vs-Defense evolutions, Tyrogue's 3-way split), `turn_upside_down` (Inkay), `used_move`
  (Rhyperior-style move-known-at-evolution triggers, distinct from `known_move`), `gender`,
  `min_steps` (Feebas-in-Let's-Go-style), `region`, `needs_multiplayer`, `min_damage_taken`,
  `near_special_rock`, `min_move_count`. `describeEvolutionDetail`'s `else -> trigger.toDisplayName()`
  fallback likely already produces something readable for many of these triggers even unread — but
  the condition-specific detail (which teammate, which move, which location) is missing.

### Whole endpoints never called

- **`/nature`** — the 25 natures (stat +10%/-10% pairs) with no in-app representation at all; a
  natural fit for the existing Base Stats card (e.g. "+Attack/-Defense" note) if ever paired with
  a way to pick a nature, but has no obvious hook without one.
- **`/item`** — held items, their effects, sprites. `PokemonDto.held_items` above only lists *wild
  encounter* items; the full `/item/{name}` resource has the effect text. Relevant to Peat Block
  (Ursaluna) and every other held-item evolution trigger already surfaced via `EvolutionDetail.item`.
- **`/pokedex`** — the real regional dex groupings (Kanto, Johto, National...) and their own
  numbering, pairs with `PokemonSpeciesDto.pokedex_numbers` above. Would let the Pokédex list sort/
  filter/number by a specific game's dex instead of only the national one.
- **`/location`, `/location-area`, `/encounter-method`, `/encounter-condition*`** — where a wild
  Pokémon is actually found, by game and area (`PokemonDto.location_area_encounters` is the URL
  into this data). A "Where to catch" card.
- **`/generation`, `/version`, `/version-group`** — already read piecemeal by *name* everywhere
  (`species.generation.name`, evolution `EvolutionDetail.version_group`...), but the full resources
  (a generation's own Pokémon range, a version's release year) are never fetched.
- **`/growth-rate`** — the actual level-vs-experience curve/formula, pairs with
  `PokemonSpeciesDto.growth_rate` above.
- **`/characteristic`** — flavor text tied to a Pokémon's highest IV stat; needs an IV input the
  app has no other use for, so low priority without a broader "build a specific Pokémon" feature.
- **`/berry`, `/berry-firmness`, `/berry-flavor`, `/contest-*`, `/super-contest-effect`,
  `/pal-park-area`, `/pokeathlon-stat`, `/machine`, `/move-battle-style`, `/move-ailment`,
  `/move-category`, `/item-attribute`, `/item-category`, `/item-fling-effect`, `/item-pocket`,
  `/currency`** — niche/legacy game-mechanic data (Contests, Pal Park, Pokéathlon, item-crafting
  categories) with no obvious fit for this app's current scope; listed for completeness, not as
  candidates.
- **`/language`** — every `names`/localized-text field across every resource above (species,
  move, ability, type...) is keyed by language; the app always filters to `"en"`. A language
  picker/setting would be the actual feature this unlocks, not the endpoint itself.

## F33 — Filter dex by "is a perfect counter to a type triangle"

**To groom** — requested 2026-08-09, not yet planned or implemented.

Request: a Pokédex filter that only shows Pokémon whose typing is the exact best counter to a type
triangle — e.g. Charizard (Fire/Flying), the counter typing to the Fire/Grass/Ground triangle per
`util/TypeTriangles.kt`'s own existing entry for it. The mechanism is exactly
`TypeTriangles.counteredBy(pokemonTypes).isNotEmpty()`, the same predicate BACKLOG.md F26 already
uses to decide whether the detail screen's "Type Triangles" card shows at all for a given Pokémon.

Not yet scoped:

- **Binary toggle vs. pick-a-specific-triangle.** "Counters *any* triangle" is a single boolean,
  matching `RarityFilter`'s on/off shape in `PokedexListViewModel`. "Counters *this specific*
  triangle" (e.g. only Fighting/Rock/Flying's counters) is closer to the existing Move/Ability
  filters, which pick one value from a list — here that list would be the 15 triangles in
  `TypeTriangles.ALL`, shown by title or by their 3 types. The user's phrasing ("un triangle") reads
  ambiguously between the two; needs confirming which (or both, as a 2-step filter: toggle on, then
  optionally narrow to one triangle) before implementing.
- **Data already available, no new fetch needed.** Every Pokémon's types are already bulk-fetched
  via `getAllBasics()` (same source `RarityFilter`/type filtering already reads), so
  `TypeTriangles.counteredBy(types)` can run client-side over the full ~1300-entry list with no
  network cost — likely a `util/TypeTriangles.kt` addition like
  `fun perfectCounters(allTypesByName: Map<String, List<String>>): Set<String>` (name -> filters in)
  or reusing `counteredBy` per-entry, whichever reads cleaner at the call site.
- **UI slot.** Likely alongside `RarityFilter`'s `SelectableChip` in `FilterSheetContent`'s "Other
  filters" row (`PokedexListScreen.kt`), not the type-filter chips — this is a triangle-counter
  predicate, not a type selection, same reasoning `RarityFilter` used to land in that row rather
  than among the type chips.
- **Should this share code with, or fully replace, `util/TypeTriangles.kt`'s existing per-Pokémon
  `counteredBy` call on the detail screen** (BACKLOG.md F26), or stay a separate list-level
  predicate — likely the same function reused both places, but worth confirming there's no
  detail-vs-list-scope mismatch (e.g. alternate forms) before assuming.

## F34 — Play a Pokémon's cry, with an offline prefetch tier for cry audio

**To groom** — requested 2026-08-09. Priority **Medium**. Not yet planned or implemented.

Request: a play button on the detail screen to hear a Pokémon's cry, plus the ability to prefetch
the audio for offline use (alongside Settings' existing Essentials/Sprites/Full detail tiers) —
picks up `PokemonDto.cries.latest`/`cries.legacy` directly from BACKLOG.md F32's survey (an unused
field on a resource the app already fetches for every Pokémon, so no new per-Pokémon request is
needed to know the cry URL exists).

Not yet scoped:

- **`latest` vs `legacy`, or both.** `cries.latest` is the current-gen cry, `cries.legacy` is the
  Gen 5-era one for Pokémon that have had theirs redone since — needs deciding whether to expose a
  choice or just always play `latest` (falling back to `legacy` if `latest` is somehow absent).
- **Playback mechanism.** The app has no audio playback anywhere today — needs picking a player
  (`android.media.MediaPlayer` is the simplest fit for "play one short one-shot sound", no need for
  ExoPlayer's streaming/playlist machinery) and deciding lifecycle handling (release on screen
  leave, don't leak across Pokémon swipes per F16).
- **Entry point.** Likely a small speaker/play icon near the sprite on `PokedexDetailScreen.kt`,
  similar in spirit to the existing shiny-toggle icon in the top bar — exact placement not decided.
- **New `PrefetchTier`.** `PrefetchManager.kt`'s `enum class PrefetchTier` would gain a `CRIES`
  entry alongside `ESSENTIALS`/`SPRITES`/`FULL_DETAIL`, wired into `PrefetchSettings` the same
  on/off-toggle way. Needs a rough total-size estimate (~1300 short audio clips) for the Settings
  copy, the way Sprites' row already says "50-150MB" — not measured yet.
- **Caching.** Whether downloaded cries reuse the same disk-cache mechanism `PrefetchManager`
  already uses for sprite images, or need their own — likely yes (same shape: URL keyed by
  Pokémon id, immutable content), but not confirmed against the actual cache abstraction yet.

## F35 — Translate the app into the languages PokeAPI supports

**To groom** — requested 2026-08-09, scope refined same day. Priority **Medium**. Not yet planned
or implemented.

Request: localize the app into a subset of the 14 languages PokeAPI's `/language` endpoint lists
(verified directly against the API for F32's survey — see that section for the full list and the
`official` flag per language). **Default is English regardless of device locale**; a Settings picker
lets the user choose a language that then drives **both** axes at once — the app's own UI chrome
and the game data — not a per-axis choice.

**Translation source and language list, decided 2026-08-09: Claude translates the UI chrome
directly** (no external translation service) — reliable for major languages, but with no in-game
reference text to check against for `cs`, unlike every other language where PokeAPI's own
`names`/`flavor_text_entries` double as a correctness check. **The picker is therefore restricted to
languages Claude can translate with reasonable confidence, dropping `cs`** (Czech, unofficial, no
in-game reference — see below) and **collapsing the `ja`/`ja-hrkt`/`ja-roma` trio to just `ja`**
(kanji/kana, the one an actual Japanese-locale user would expect — `ja-hrkt`/`ja-roma` stay as
internal fallback candidates for game-data lookups, never separate picker entries). Resulting
picker list (10): `en` (default), `fr`, `de`, `es`, `es-419`, `it`, `pt-br`, `ja`, `ko`, `zh-hans`,
`zh-hant` — every *official* language except the two redundant Japanese variants. A native-speaker
review pass before any real release remains a good idea (noted when this source was proposed), but
isn't a blocker for a first implementation.

The two axes remain worth naming even though both are now in scope, since they're implemented
completely differently:

- **The app's own UI chrome** — button labels, screen titles, static strings like "Base Stats",
  "Type Matchups", "Would fix these shared weaknesses:"... A normal Android
  `strings.xml`/`values-{locale}` localization, entirely unrelated to PokeAPI — the API has no
  bearing on it at all, since none of these strings come from network data. This is the larger
  effort: every user-facing string across every screen needs extracting to resources and
  translating, and the app's default locale needs pinning to English independent of the device's
  system locale (Android normally follows system locale automatically) so the picker is the only
  way to change it.
- **Game data itself** — species/move/ability/type names and flavor text. Already server-side
  localized: every relevant resource (`PokemonSpeciesDto`, moves, abilities, types) has a `names`
  and/or `flavor_text_entries` field carrying every language PokeAPI has a translation for, filtered
  to `"en"` everywhere in the app today (e.g. `flavorTextEntries.firstOrNull { it.language.name ==
  "en" }` on the detail screen). Switching this axis is "read a different language code" at
  existing call sites, not a new fetch — the smaller of the two efforts.

Not yet scoped:

- **Coverage isn't uniform.** Not every resource has a translation in all 14 languages for every
  entry — rarer/newer species in particular. Falling back to `"en"` when a chosen language's entry
  is missing needs to be the rule from the start, not an afterthought — applies to both axes (a
  missing `strings.xml` translation falls back to the default resource the same way Android already
  handles missing locale-qualified resources).
- **Picker UI and persistence.** Where it lives in Settings (presumably the same section as the
  existing display/prefetch toggles), and how `DisplaySettings`-style `SharedPreferences`
  persistence would store the choice — no code looked at yet. Default is decided (English), so this
  is just the mechanism, not the default value.

## Cancelled

Full specs kept here rather than deleted — the user may revisit any of these later, and a kept
description means a re-approval doesn't require re-deriving the design from scratch.

### F9 — Showdown export

Cancelled 2026-08-08 — user explicitly said "je ne veux plus de cette feature". No detailed spec was
ever recorded before cancellation (cancelled the same session it was raised), so there's nothing more
to keep beyond the name/summary above.

### F10 — Filter dex by resistance/weakness

Cancelled 2026-08-08 (backlog priority review).

Filter the Pokédex list by how it matches up against a chosen attacking type (resists / weak to).

- New repository method `getDefensiveMultipliersAgainst(attackingType): Map<String, Double>` —
  fetch all 18 `TypeDetailDto` concurrently (already cached), combine with `getAllBasics()`'s typing
  per Pokémon via `computeDefensiveMultipliers`, memoize in `AsyncCache<String, Map<String, Double>>`.
  Real CPU work (~1300×2 map merges) — `AsyncCache` already runs fetches on `Dispatchers.Default`,
  don't move it.
- New state on `PokedexListUiState`: `matchupFilterType`, `matchupFilterDirection` (RESISTS = `<1.0`
  incl. immunities, WEAK_TO = `>1.0`), `matchupFilterNames`.
- New `onMatchupFilterSelected` mirrors `onTypeToggled`'s exact shape (tracked job, cancel-then-clear,
  rethrow cancellation).
- UI: type picker + 2-option segmented control in `FilterSheetContent`.
