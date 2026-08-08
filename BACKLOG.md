# PikaDex — Backlog

Features discussed with the user but not yet implemented. Each entry stays here until it's built
(then it moves into the README's feature list and this entry is removed) or the user explicitly
cancels it (then it's removed with a note in the commit message, not left here as dead weight).

## F10 — Filter dex by resistance/weakness

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

## F11 — Suggest team members covering gaps

**Plan agreed with the user on 2026-08-08** — implement when asked.

When the active team has fewer than 6 members, show a "Suggestions" card offering up to 10 Pokémon
that improve **both** the team's offense and defense at once. Sorted by base stat total, **ascending**.

- `util/TeamSuggestions.kt` (+ test): pure
  `rankSuggestions(sharedWeaknesses, coverageGaps, candidates, typeDetailsByType, excludeNames, limit=10): List<TeamSuggestion>`.
  A candidate qualifies only if it resists (`<1.0`) at least one shared weakness **and** hits
  (`>1.0`) at least one coverage gap — both conditions required, not either/or. Ties/ordering: sort
  by `statTotal` ascending (not descending, and not score-weighted — the user's explicit choice).
  Offensive data is STAB only (candidate's own types from `getAllBasics()`), no per-candidate
  movepool fetch.
- Candidate pool: `getAllBasics()` (already cached, stats+types) filtered against `getMasterList()`'s
  id — exclude alternate forms (`id >= 10000`, same heuristic as
  `PokedexRepository.getPokemonDetailBundle`'s comment on mega/gmax/regional ids) and exclude
  `stats.values.sum() < 300` (cuts babies/pre-evolutions). No legendary/mythical exclusion beyond
  that threshold.
- `TeamViewModel.loadSuggestions()`: own tracked Job (same cancel/rethrow shape as `matrixJob`),
  gated on `!isMatrixStale && members.isNotEmpty() && members.size < TeamRepository.MAX_SIZE`. Reuses
  the 18 `getTypeDetail()` calls already cached by `computeMatrix`.
- UI (`TeamScreen.kt`): "Suggestions" card below the existing `MatrixCallout`, visible only when the
  team isn't full and the list isn't empty. Sprite + name + stat total + add button per row (respects
  `TeamRepository.MAX_SIZE` / reuses the existing "team is full" rejection path).

## F12 — Match team against a preset trainer

"How does my team fare against Cynthia's?"

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

Added to the backlog 2026-08-08, alongside F11's plan.

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

## Cancelled (not to be re-proposed)

- **F9 — Showdown export.** User explicitly said "je ne veux plus de cette feature" (2026-08-08).
  Do not implement without being asked again.
