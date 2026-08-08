# PikaDex — Backlog

## Priority (reviewed 2026-08-08)

| Feature | Priority | Status |
|---|---|---|
| F18 — Fix ExperimentalCoilApi warnings at compile time | **High** | To groom |
| F15 — Team coverage impact preview | **High** | Plan ready |
| F16 — Swipe between Pokémon on the detail screen | **High** | Plan ready |
| F20 — Add Radical Red trainer teams | Medium | To groom |
| F19 — Black/AMOLED mode | Medium | To groom |
| F17 — Filter dex by "is legendary" | Medium | To groom |
| F12 — Match team against a preset trainer | Low | Plan ready |
| F22 — Suggestion "why" text and impact-based sort | — | Done |
| F21 — Suggestion tier ceiling filter | — | Done |
| F9 — Showdown export | — | Cancelled |
| F10 — Filter dex by resistance/weakness | — | Cancelled |

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

**To groom** — raised but no implementation plan agreed yet.

`./gradlew compileDebugKotlin` currently emits two `This declaration needs opt-in` warnings from
`SettingsViewModel.kt` (`measureStorage()` and `clearDownloadedData()`), both from calling
`ImageLoader.diskCache` (a Coil API marked `@ExperimentalCoilApi`). Currently silent (build still
succeeds), but real cleanup, not just suppression: either wrap both call sites in
`@OptIn(ExperimentalCoilApi::class)` with a comment on why the experimental surface is accepted, or
check whether the installed Coil version has since stabilized a non-experimental accessor for the
disk cache. Open question to resolve before this moves to "Plan ready": which of the two.

## F20 — Add Radical Red trainer teams

**To groom** — raised but no implementation plan agreed yet.

Extend `util/PresetTeams.kt` (currently the 70 gym leaders + 11 champions from the official games,
Red/Blue through Scarlet/Violet — see `PresetTeam`/`PresetRole`) with the trainer rosters from the
[Radical Red](https://dex.radicalred.net/) ROM hack, which already inspired this app's Pokédex
presentation per the README's intro. Open questions to resolve before this moves to "Plan ready":
which Radical Red version/difficulty's rosters to use (they're reportedly rebalanced and differ by
difficulty mode), whether `PresetRole`/`game` need a new case or Radical Red's trainers fit the
existing `GYM_LEADER`/`CHAMPION` split, and where the roster data itself would be sourced from (no
official API, unlike PokeAPI-backed official-game teams).

## F19 — Black/AMOLED mode

**To groom** — raised but no implementation plan agreed yet.

The app already has dark mode following the system setting (see README's "Throughout" section).
This is understood as a distinct ask, not a duplicate: either a true-black (pure `#000000`
background, not just Material's dark grey) variant for AMOLED screens, and/or a manual
light/dark/system toggle independent of the OS setting — which of the two (or both) needs
confirming with the user before this moves to "Plan ready".

## F17 — Filter dex by "is legendary"

**To groom** — raised but no implementation plan agreed yet.

Add a filter to the Pokédex list letting the user show only legendary (and/or mythical) Pokémon.
Open questions to resolve before this moves to "Plan ready": legendary vs. mythical as one toggle or
two, where the filter sits relative to the existing type filters in `FilterSheetContent`, and whether
PokeAPI's per-species `is_legendary`/`is_mythical` flags require a new fetch path or are already
pulled in by an existing call.

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

**Plan finalized 2026-08-08** — simplest option chosen for every open question; implement when asked.
Entry point and output format agreed with the user.

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

## F16 — Swipe between Pokémon on the detail screen

**Plan finalized 2026-08-08** — simplest option chosen for every open question; implement when asked.

From `PokedexDetailScreen.kt`, swipe left/right to move to the adjacent Pokémon without backing out
to the list and re-selecting — currently the only way to see the next entry is Back, scroll/search,
tap again.

"Adjacent" by what ordering, given the screen is reached from several places (the grid under its
active filter/sort, an evolution chain tap, Compare, a team member chip...)? Simplest option, chosen
over threading whichever ordering was active at the entry point through navigation args: always step
by `getMasterList()`'s own order (PokeAPI's `/pokemon` list, already effectively ascending id
including alt forms) regardless of entry point — predictable, matches "Pokédex" as a numbered
reference work, and needs no extra nav state.

- New pure `util/AdjacentPokemon.kt`: `fun adjacentNames(masterList: List<NamedApiResource>,
  currentName: String): Pair<String?, String?>` (previous, next; null at either end of the list or
  if `currentName` isn't found). Tests: empty list, single entry, first/last boundary, name absent.
- `PokedexDetailViewModel`: add `previousPokemonName: String?`/`nextPokemonName: String?` to
  `PokedexDetailUiState`, computed in `load()` via `repository.getMasterList()` (already cached by
  the time most detail screens open — Pokédex list, Compare, and this screen's own `Add to team`
  path all warm it first) + `adjacentNames`. Best-effort: a failure here leaves both null rather than
  blocking the rest of `load()`.
- `PokedexDetailScreen.kt`: new `onNavigateAdjacent: (String) -> Unit` param, distinct from the
  existing `onPokemonClick` (which *pushes* a new detail screen — used for cross-references like
  evolution stages, where Back should return to the page you tapped from). Two pieces:
  - `Modifier.pointerInput(pokemonNameOrId) { detectHorizontalDragGestures(...) }` on the content
    `Box`, committing to `onNavigateAdjacent(next/previous)` past a swipe-distance threshold, no-op
    at either end of the list (name null).
  - A small `IconButton` pair (`Icons.Filled.ChevronLeft`/`ChevronRight`), pinned at the vertical
    center of each screen edge, shown only when that direction's name is non-null — the swipe
    gesture alone isn't discoverable (nothing on screen hints it exists) and isn't reachable without
    touch (TalkBack, a hardware keyboard); the buttons cover both.
- `PokedexNavHost.kt`: wires `onNavigateAdjacent = { name -> ifIdle { navController.navigate(
  "detail/$name") { popUpTo(ROUTE_DETAIL) { inclusive = true } } } }` — replaces the current
  back-stack entry rather than pushing (same `popUpTo(...){inclusive=true}` pattern the Compare
  screen's own re-navigate already uses), so Back always returns to wherever the user actually
  entered the detail flow from, not back through every Pokémon swiped past on the way.

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
