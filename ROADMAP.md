# Roadmap

Do not build the entire application in one pass. Each phase must leave tests, documented assumptions, and no silently invented data.

## Phase 0 — Research (done)

Investigated TL-Helper (warehouse, decoder, combat-log findings), official combat-log spec, Questlog/TLDB constraints, and Nix 4.0.0 / item-level trait changes. Conclusion: structured game data exists locally **after** TL-Helper extraction; live character state does not.

## Phase 1 — Architecture and schema (done)

- `ARCHITECTURE.md`, `DATA_SOURCES.md`, `THREAT_MODEL.md`, this file.
- SQLDelight schema with `dataset_snapshot` vs `game_*` vs `user_*` vs `combat_event`.
- Kotlin Multiplatform `core`, JVM CLI, empty Android module.
- One warehouse mapper (fixture), one query path, combat-log v4 parser on a redacted fixture.

## Phase 2 — Domain models (done)

Typed models in `core` (`DatasetSnapshot`, `GameItem`, …). `SnapshotService` activates one snapshot without deleting previous game rows. Human aliases (`t4`) are labels, not primary keys. Schema version is `SchemaVersion.CURRENT` (1); later versions get SQLDelight migrations.

## Phase 3 — First real static importer (done for 24829515)

`TLHelperDataSource` + `WarehouseLocator` look at `SOLISIUM_TL_WAREHOUSE` or `%TL_DATA_ROOT%\warehouse\tl-*.sqlite` (default `D:\TL_Data\warehouse`).

Live import on this PC, 2026-08-24:

- Game install: `D:\SteamLibrary\steamapps\common\Throne and Liberty`
- Steam build **`24829515`** (warehouse and install match)
- Warehouse: `D:\TL_Data\warehouse\tl-24829515.sqlite` (gitignored; 46 decoded tables, 138,209 records)
- Decoder `0.2.0`. Game version string is `unknown` (`TLResourceVersion.ini` was empty)
- Mapper fills `game_item` plus typed `game_weapon` / `game_armor` / `game_accessory` from verified `TLItemEquip.equip_category` values, `game_trait`, `game_rune` (`TLRuneInfo` only), `game_rune_synergy`, `game_skill`, `game_skill_effect`, `game_skill_formula`, `game_recipe`, `game_material`, `game_stat` (`TLStats`)
- Measured active snapshot: 12,489 items, 455 weapons, 632 armor, 318 accessories, 217 traits, 72 runes, 13 synergies, 16,155 skills, 60,318 effects, 10,786 formulas, 4,564 recipes, 319 materials, 292 stats, 4,254 item stat values over 1,458 items
- `TLRuneGrowth` is a curve table and is skipped. `TLItemStatAttrConverter` and `TLStatAttrLooks` are kept as name-resolution inputs, not as stat rows
- Combat log folder **absent** under `%LOCALAPPDATA%\TL\Saved\CombatLogs`

Import runs inside one SQLite transaction: 105,225 rows in ~13s (was ~7 min row-by-row).

Do not treat research warehouse `24118850` as this PC's current patch. AES keys stay in gitignored files only.

### Table inventory

The collector now records every table package it can see (`tablePackageNames` in the run manifest): **1,415 tables**, of which 46 are decoded. That turns "we have no data for X" into a checkable claim — most remaining gaps are uncollected tables, not missing ones.

## Phase 4 — Local database browser (CLI and desktop UI done)

CLI: `query snapshots|counts|items|weapons|armor|accessories|traits|runes|synergies|skills|effects|formulas|recipes|materials|stats|item-stats|item-curves|lookup|characters|character|sessions|session`, `--name` filter (`query item-stats --row <item-row-id>`), `activate`, `alias`, `probe`. Character query resolves loadout keys against the active snapshot (or `--snapshot`) and labels missing keys `UNRESOLVED` instead of guessing names. `query session --id` prints observed per-skill damage from logged rows only.

Desktop UI: `desktopApp`, Compose Multiplatform 1.8.2 on JVM 17, run with `gradlew :desktopApp:run`. Four screens — Overview (provenance, coverage, stale-dataset banner), Catalog (search across all twelve catalog kinds with a detail pane showing base stats and plotted upgrade curves), Combat (observed per-skill damage), Data (snapshots, activate). It reads the same `~/.solisium/solisium.sqlite` as the CLI.

The UI holds no game logic: it calls `CatalogQuery` and renders what comes back. It never sums a base stat with a curve value, because that stacking rule is unverified. Provenance badges are attached to values rather than to screens, so a confidence label always travels with the number it describes.

## Phase 5 — Character-state ingestion (manual JSON done)

Most reliable non-OCR source: **manual / user-supplied JSON**. `ManualImportDataSource` imports identity plus equipment, weapons, traits, runes, skills, inventory, materials, currency, cooking, goals, and named builds. Reimport of the same `character.id` replaces child rows and preserves `created_at`. Example document: `examples/character.json`. CLI: `import --source manual`, `query characters`, `query character --id`.

Do not invent a local save parser. Hashed `.sav` files under `%LOCALAPPDATA%\TL\Saved\SaveGames` are detected and left unread.

Blocked as *live* sources: inventory, Lucent, Sollant, cooking level — no verified file source. Those fields are accepted in JSON with an explicit warning.

## Phase 6 — Combat-log parser (parser exists; folder watch later)

Make the parser robust against new `LogType`s. CLI: `logs`, `query sessions`, `import --source combat-log` (file, directory, or newest file in the default CombatLogs folder). Production folder watch comes after the user enables in-game Combat Meter/Log (folder is absent until then).

Build `CombatSession` summaries from logged fields only. `query sessions` lists observed damage / observed DPS; `query session --id` groups those sums by `SkillName`/`SkillId`. That is not modeled DPS.

Do not implement game formulas here.

## Phase 7 — Deterministic upgrade comparison

Compare two items or trait ranks using stored stats. Confidence-gated. No fake DPS.

Inputs that now exist: **4,254 per-item base stat values across 1,458 items** (`game_item_stat`), 217 traits with localized names, 292 named stats, and 10,786 `TLFormulaParameterNew` rows stored at confidence `extracted`. **Extracted means the values were read from the table, not that the pipeline is solved.** Nothing reads formula rows to produce damage numbers yet.

Item stat values come from `TLItemStats.main_stat_base_id` + `main_stat_base_seed` → `TLItemMainStatInit`, verified at 1,837/1,839 pointers resolving with zero misses. Stored as raw client integers: the client scales stats per field (675 attack speed, 1600 range) and no scale factor has been verified, so Solisium must not render them as percentages.

Enchant and item-level curves are now mapped: **9,785 curve points and 2,924 item→curve links**. `TLItemMainStatEnchant` is keyed by `id` + `enchant_level`, `TLItemMainLevelStat` by `Id` + `item_level`, and both pointers resolve 1,837/1,837 with zero misses. Curves are *shared* — 42 enchant curves and 45 item-level curves serve 1,458 items — so they are stored once in `game_stat_curve` and referenced from `game_item_curve` rather than copied per item. Each item's link carries its own `enchant_level_max` (one of 0, 1, 3, 6, 9, 12), and reads are clipped to that cap so a +3 item never shows a +12 row.

Curve values are **cumulative totals at a level, not per-level deltas**. Evidence: the `kBow` curve reads 5, 10, 15, 20, 25, 30, 35, 40, 45, 51, 56, 61 across levels 1–12, so the increment stays near +5 while the stored figure ramps.

Still missing for a real comparison:

- **The stacking rule.** Whether a curve value adds to the base stat is unverified, so nothing sums them. This is the last blocker for "what does +9 actually give me".
- **Rolled extra stats.** `TLItemExtraStatInit` is a shared roll table, not per-item data; per-item rolls are not in the client tables at all.
- **Trait value curves.** `TLItemTraitsBaseValue` / `TLItemTraitsEnchantValue` are collected but unmapped.
- **The formula → skill link.** `TLEffectProperty.formula_parameter` is the candidate edge, still unverified.

**Blocker:** many combat pipeline stages are still unknown (mitigation order, contest curves, rounding). TL-Helper `unknown-formulas.md` is the register. Solisium must not "finish" those formulas without new evidence.

## Phase 8 — Should I buy this?

User provides a listing (manual / clipboard / URL later / OCR last). Compare to the bound snapshot and current build. Output BUY / SAVE with assumptions.

**Blocker:** live listings and prices need an optional market source (TLDB unofficial) or manual price entry.

## Phase 9 — What should I do next?

Rank `UpgradeAction` candidates from goals + inventory + currency + game data. Lucent path A/B/C. Inventory KEEP/SELL/etc. only with explanations.

**Blocker:** same formula gaps; missing live inventory unless the user entered it.

## Phase 10 — Android companion

Compose Android app against `core`. Character viewer, recommendations, shopping analysis. No pak access, no combat-log folder unless the user shares files.

Windows keeps extraction/import/log watch.

## Honest blockers (do not schedule as if solved)

| Item | Why it is blocked |
| --- | --- |
| Live inventory and currency | Not found in local files |
| Exact modeled DPS | Incomplete client/server formulas |
| Skill → formula link | No direct edge found yet; `TLEffectProperty.formula_parameter` is unverified |
| Stat display scale | Client stores raw integers per stat with no verified scale factor, so no stat can be shown as a percentage |
| Per-item rolled extra stats | Client tables hold only the shared roll table, not what an individual item rolled |
| Patch diffs | Only one live warehouse on this PC (`24829515`); research receipt was `24118850` |
| Blessings | TL-Helper: not in public builder payloads |
| Second-language localization | Warehouse locale currently `en` |
| `TLAbnormalContentsGroup` | Dedicated layout, not the normal RowStruct decoder |
| Redistributing game data | IP; keep dumps off git |

## Immediately implementable vs not

**Now:** schema, live warehouse import for `24829515`, catalog query (typed gear, traits, effects, formulas, materials, stats), manual character JSON, loadout name resolution against a snapshot, install/snapshot build mismatch, v4 log parse and observed skill totals, Steam build detect, stubs for public repos.

**Not now:** UI chrome, LLM explanations, Lucent optimizer scores, Android screens, OCR, any use of formula rows to produce damage numbers.

### Still-empty tables and what each one actually needs

None of these are unavailable in the paks — they are uncollected. Each names the table that would fill it.

| Table | Needs |
| --- | --- |
| `game_food` | `TLItemLotteryUnit`. Cooking results are lottery ids (`Usable_Food_Result_020_kA_Success`), not direct item ids; only 6 of 81 cooking recipe row ids are item ids |
| `game_dungeon` | `TLAbyssDungeon`, `TLChallengePartyDungeon`, `TLInteractiveDungeon` (106 dungeon/boss tables exist) |
| `game_boss` | `TLFieldBoss`, `TLGuildRaid`, `TLMagicDollExpeditionBoss` |
| `game_progression` | `TLGrowthMission`, `TLGrowthResource`, `TLSkillLevelUpRecipe` (last one is already collected) |

20 cooking ingredients still do not resolve to items; they are fish, defined in `TLFishingFishInfo`. The importer counts and warns instead of guessing.
