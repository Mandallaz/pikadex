# PikaDex — Backlog

Features discussed with the user but not yet implemented. Each entry stays here until it's built
(then it moves into the README's feature list and this entry is removed) or the user explicitly
cancels it (then it moves to the "Cancelled" section at the bottom, full spec kept intact — the user
may change their mind later, and re-deriving a spec from scratch is wasted effort a kept description
avoids).

## Priority (reviewed 2026-08-08)

| Feature | Priority |
|---|---|
| F14 — Stat total minimum filter | **High** |
| F15 — Team coverage impact preview | **High** |
| F13 — Offline prefetch | Medium |
| F12 — Match team against a preset trainer | Low |

F10 is cancelled — see the Cancelled section at the bottom.

## F12 — Match team against a preset trainer

**Priority: Low.** "How does my team fare against Cynthia's?"

- `util/TeamVersus.kt` (+ test): pure
  `buildVersusGrid(myTypings, theirTypings, offensiveByType, defensiveByPokemon): List<VersusCell>`
  (my-member × their-member best multiplier each way, STAB only — same caveat as F11, state it in
  the UI). Needs typings only (no per-Pokémon fetch), and `TeamViewModel.computeMatrix`'s already-
  built `offensiveByType`.
- New `ui/team/MatchupScreen.kt` (or dialog): grid, my 6 as rows / theirs as columns, split cell or
  two small numbers, reuse `multiplierColors` from `TeamScreen`. One-line verdict
  (favourable/unfavourable count) above the grid.
- Entry point: "Test against a trainer" on `TeamScreen`, opens `PresetTeamDialog` in a new
  selection-only mode (add a `mode: PresetDialogMode` param — `LOAD` keeps today's destructive
  confirm, `COMPARE` skips it).

## F13 — Offline prefetch

**Priority: Medium.**

- New `data/PrefetchManager.kt` + a settings entry point. Tiered:
  - **Essentials** (S-1 bulk payload + move-info + 18 type details + Smogon tier files, ~1MB,
    default on)
  - **Sprites** (artwork + default sprite for every entry via Coil `ImageLoader.enqueue` with
    `memoryCachePolicy(DISABLED)`, ~50-150MB, default on)
  - **Full detail** (every `PokemonDto` + species + evolution chain via REST, explicit opt-in with a
    warning)
- `Semaphore(6)` concurrency cap + small inter-batch delay (politeness).
- `sealed interface PrefetchState { Idle / Running(done, total, phase) / Finished(failed) / Failed(message) }`
  as a `StateFlow`, owned by `PrefetchManager`'s own `CoroutineScope` (not `viewModelScope` — must
  survive navigating away).
- Partial failure (404s) counted, never aborts the run.
- Storage accounting: show `http_cache` + `disk_cache` + `image_cache` directory sizes, "Clear
  downloaded data" button.

## F14 — Stat *total* minimum filter

**Priority: High.** Added to the backlog 2026-08-08, alongside F11's plan.

Same "Minimum Stats" section as F8 (`FilterSheetContent`), one more slider for the stat **total**
(sum of all six base stats), same Slider UI as the rest of that section (the user explicitly prefers
sliders over segmented buttons/chips for this filter — see the F8 revert in git history,
commits `9a90f08`/`ec8859e`).

- `SortStat.TOTAL` currently has `apiName = null` (it's a derived sum, not a raw GraphQL field) — F8's
  `SortStat.entries.mapNotNull { it.apiName?.let {...} }` loop skips it for exactly that reason, so
  this needs its own slider row outside that loop, or `statMinimums` needs a non-apiName-keyed
  special case (e.g. a reserved `"total"` key checked separately in `computeDisplayed`'s filter pass,
  comparing `stats.values.sum()` against the threshold).
- Range: 0..~720 (highest known base stat total).

## F15 — Preview a Pokémon's impact on the current team's coverage

**Priority: High.** Added to the backlog 2026-08-08. Entry point and output format agreed with the
user; scoring/data approach not yet discussed.

From a Pokémon's detail screen (`PokedexDetailScreen.kt`), a new top-bar icon — same slot pattern as
the existing shiny toggle and the Compare entry point (`showCompareDialog` /
`loadCompareCandidatesIfNeeded()`) — opens an "impact on my team" preview showing what would change
if this Pokémon were **added** to the active team (team not full) or **replaced** one of its members
(team full, user picks who via a dialog reusing the existing member-chip list from `TeamScreen.kt`).

Output is a **text summary** of the delta (not a full before/after matrix): shared weaknesses fixed,
shared weaknesses introduced, coverage gaps closed, coverage gaps opened — e.g. "Would fix these
shared weaknesses: Water, Rock. Would introduce no new shared weaknesses. Would close this coverage
gap: Dragon."

Implementation sketch (not yet validated with the user):

- Extract the per-member matchup + matrix-assembly logic currently inlined in
  `TeamViewModel.computeMatrix()` into a shared suspend function (e.g.
  `util/TeamMatrixCalculator.kt` or a `PokedexRepository` method) so it can be run twice — once for
  the real team (already cached via `TeamViewModel`/`TeamRepository`), once for the hypothetical
  roster (current members with the candidate added or swapped in). Full movepool-based accuracy,
  **not** F11's STAB-only shortcut — this is one hypothetical team of ≤6, not a 1300-candidate scan,
  so the cost is affordable.
- New pure function, e.g. `util/TeamImpact.kt`: `computeTeamImpact(beforeSharedWeaknesses,
  afterSharedWeaknesses, beforeCoverageGaps, afterCoverageGaps): TeamImpactSummary` — plain set
  differences (fixed = in before, not in after; introduced = in after, not in before; same shape for
  gaps).
- New state/loading on `PokedexDetailViewModel` (`teamImpact: TeamImpactSummary?`,
  `isTeamImpactLoading`), triggered on demand like `loadCompareCandidatesIfNeeded()`, not as part of
  `load()`.
- Icon hidden or disabled when there's no active team to compare against (empty team — nothing to
  preview impact on).

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
