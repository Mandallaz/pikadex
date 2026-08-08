# PikaDex — Backlog

Features discussed with the user but not yet implemented. Each entry stays here until it's built
(then it moves into the README's feature list and this entry is removed) or the user explicitly
cancels it (then it moves to the "Cancelled" section at the bottom, full spec kept intact — the user
may change their mind later, and re-deriving a spec from scratch is wasted effort a kept description
avoids).

## Priority (reviewed 2026-08-08)

| Feature | Priority |
|---|---|
| F15 — Team coverage impact preview | **High** |
| F12 — Match team against a preset trainer | Low |

F10 is cancelled — see the Cancelled section at the bottom.

## F12 — Match team against a preset trainer

**Priority: Low.** **Plan finalized 2026-08-08** — simplest option chosen for every open question;
implement when asked. "How does my team fare against Cynthia's?"

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

**Priority: High.** **Plan finalized 2026-08-08** — simplest option chosen for every open question;
implement when asked. Entry point and output format agreed with the user.

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
