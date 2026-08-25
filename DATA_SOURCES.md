# Data sources

What Solisium Autopilot can actually ingest, as of the live warehouse on this PC (Steam build `24829515`, decoder `0.2.0`, game version `unknown`) plus the earlier research snapshot (Steam build `24118850`, game version `1.431.22.7761`). Unverified sources are marked as such. Nothing here may be silently treated as complete.

Status values: **verified** (observed in client data, warehouse, or official logs), **partial** (table exists but mapping or formulas are incomplete), **unavailable** (looked for, not found), **optional** (third-party, not required), **planned** (adapter exists or is stubbed; not a claim that data exists).

## Capability matrix

| Data | Source | Reliability | Automatable? | Patch sensitive? | Status |
| --- | --- | --- | --- | --- | --- |
| Item names, ids, looks | TL-Helper warehouse `records` from `TLItemLooks` / `TLItemLooks_Equip` | High for decoded rows | Yes, after TL-Helper extract | Yes | verified |
| Item stats / equip flags | `TLItemStats`, `TLItemEquip` | High for decoded rows | Yes | Yes | verified; `item_grade` / `equip_category` overlay onto matching `game_item` keys |
| Per-item base stat values | `TLItemStats.main_stat_base_id` + `main_stat_base_seed` → `TLItemMainStatInit` (keyed by its own `id` + `seed` fields, not row id) | High — 1,837/1,839 pointers resolve, 0 misses | Yes | Yes | verified as `game_item_stat` (4,254 values over 1,458 items). Values are raw client integers; the per-stat scale factor is **unverified**, so nothing is shown as a percentage |
| Rolled / extra item stats | `TLItemExtraStatInit` | Not per-item: all 1,837 rows share the group `M8_Extra_Stat` | No | Yes | **not mapped** — a roll-value table, not item stats. Mapping it would have attached ~200 identical stats to every item |
| Enchant / item-level stat curves | `TLItemMainStatEnchant` (by `enchant_level`), `TLItemMainLevelStat` (by `item_level`), `TLItemExtraStatEnchant`, `TLItemExtraLevelStat` | Tables collected and decoded | Later | Yes | collected, not mapped; `game_item_stat` holds base values only |
| Weapons / armor / accessories as typed entities | `TLItemEquip.equip_category` tokens observed on `24829515` | High for those tokens | Yes | Yes | verified for classified categories; ammo / gems / bait stay items only |
| Traits | `TLItemTraits` (217 rows); Nix 4.0.0 unlocks traits with stones rather than rolling them on drop | High for definitions; user trait ranks are not local | Definitions yes; equipped ranks no | Yes | verified as `game_trait`; names resolve 217/217 via `TraitStat` → `TLItemStatAttrConverter.DisplayItemStatType` → `TLStatAttrLooks.ItemUIName`. Per-rank values (`TLItemTraitsBaseValue`) and per-item candidates (`TLItemTraitGroup`) are collected but not mapped |
| Runes and synergies | `TLRuneInfo`, `TLRuneGrowth`, `TLRuneSynergy` | High for decoded rows | Yes | Yes | verified; `TLRuneInfo` → `game_rune`, `TLRuneSynergy` → `game_rune_synergy`; `TLRuneGrowth` skipped (curve, not a rune) |
| Skills | `TLSkill`, `TLSkillLevelSetting` | High for names/levels | Yes | Yes | verified names/`skill_category`; level-setting table is still skipped |
| Skill formulas | `TLFormulaParameterNew` (10,786 rows on `24829515`); 130/210 player skill sets exact, 51 derived, 29 unresolved in TL-Helper | High only where mapping is exact | Import yes; use in DPS no until mapped | Yes | partial — imported as `game_skill_formula` at `confidence=extracted` with no skill link; nothing computes damage from them |
| Skill effects / buffs | `TLEffectProperty` (55,333 rows on `24829515`), `TLAbnormalState_*` including Gauntlet | High for client-visible fields | Yes | Yes | verified as `game_skill_effect` keyed by table+row; no skill-id join (effects expose `Abnormal`, not a `TLSkill` id) |
| Combat power table | `TLItemCombatPower` (132 rows decoded) | Table exists; aggregation vs live CP unresolved in TL-Helper | Import later | Yes | partial; still skipped |
| Named stats | `TLStats` (292 rows), localized through `TLItemStatAttrConverter` + `TLStatAttrLooks` | High where the converter target is specific | Yes | Yes | verified as `game_stat`. Stats whose converter target is `kNone` (~86 of them) resolve to **no** name rather than inheriting an arbitrary one; the raw stat key is shown instead |
| Cooking recipes | `TLCookingRecipe` (decoder priority set) | High for decoded rows | Yes | Yes | verified table; Phase 1 maps `record_type=recipe` into `game_recipe` |
| Crafting recipes | `TLCraftingRecipe` | High for decoded rows | Yes | Yes | verified table; same recipe mapping |
| Materials | `TLCraftingMaterialGroup.Materials[].Item`, `TLCookingRecipe.*IngredientList[].ItemID` | High — explicit ingredient fields, resolved against known item rows | Yes | Yes | verified as `game_material` (319 rows). 20 refs unresolved (fish, in `TLFishingFishInfo`) and reported as a warning. Crafting `Ingredients[].RowName` are bundle ids, resolved only through `TLCraftingMaterialGroup`, never by stripping the trailing count |
| Food definitions | `TLCookingRecipe` results are lottery ids, not item ids | Needs `TLItemLotteryUnit` | Yes once collected | Yes | partial — `game_food` empty; only 6 of 81 cooking row ids are item ids |
| Dungeons / bosses | Dedicated tables exist: `TLAbyssDungeon`, `TLChallengePartyDungeon`, `TLInteractiveDungeon`, `TLFieldBoss`, `TLGuildRaid` (106 matches in the 1,415-table inventory) | Unknown until decoded | Yes once collected | Yes | not collected — available, not unavailable |
| Game build / version | Steam `appmanifest_2429640.acf` (`buildid`); warehouse `game_build` / `game_version` | High | Yes | Yes | verified |
| Pak content change | Hash/mtime of files under `TL\Content\Paks` | High for "something changed" | Yes | Yes | planned detect-only |
| Encrypted table bytes | Installed paks | Present but encrypted | Not in this repo | Yes | out of scope (TL-Helper) |
| Character level, CP, gear, traits, runes, skills, masteries | Manual JSON / TL-Helper BuildSnapshot / user-supplied Questlog package | High if the user maintains it | Semi | Yes — bind to a snapshot | verified as import path; **not** in local saves |
| Weapon item level / trait unlocks (Nix) | Same as loadout | High if entered | Semi | Yes | same |
| Inventory | No verified local file | — | No | Yes | unavailable |
| Lucent / Sollant | No verified local file | — | No | Yes | unavailable |
| Cooking level | No verified local file | — | No | Yes | unavailable |
| Appearance presets | `Documents\TL\Customize` | Appearance only | Yes, useless for builds | Low | verified irrelevant |
| Unreal `Saved` configs | `%LOCALAPPDATA%\TL\Saved` | Settings/cache, not loadout | Yes | Low | unavailable as character state |
| Combat DamageDone | `%LOCALAPPDATA%\TL\Saved\CombatLogs` `CombatLogVersion,4` | High for logged fields | Yes (after fight ends) | Log schema versioned | verified |
| Combat heals / buffs / resources | Not in the reviewed v4 dummy session; publisher released attack metrics first | Unknown | Parser stores unknown `LogType` | Yes | unavailable until a log with those types is captured |
| Observed DPS | `sum(damage) / session duration` from the log | High as a log statistic | Yes | No | verified meaning: observed, not modeled |
| Modeled DPS | Skill formulas + mitigation/contest curves | Incomplete (see unknown formulas) | No | Yes | unavailable as HIGH confidence |
| Market prices | TLDB `https://tldb.info/api/ah/prices` (personal, unsupported) | Unofficial | Optional online | Yes | optional |
| Questlog static catalog | Public tRPC read procedures (1,752 items, 132 runes, 210 skill sets, 544 masteries as of extraction-report) | Community mirror, change-prone | Optional; default off | Yes | optional reference only |
| Blessings | Not in Questlog builder payloads (TL-Helper out of scope) | — | No | Yes | unavailable |

## Adapter notes

### InstalledGameDataSource

Reads Steam library metadata and pak inventory. Does not decrypt archives, does not load FModel, does not search the executable for keys.

Typical install:

```text
<SteamLibrary>\steamapps\common\Throne and Liberty\TL\Content\Paks
<SteamLibrary>\steamapps\appmanifest_2429640.acf
```

### TLHelperDataSource

Reads a TL-Helper warehouse (`tl-<build>.sqlite`) as an **external** schema. Expected warehouse facts (from TL-Helper `docs/data-contract.md`): table `records` with `record_id`, `row_id`, `record_type`, `table_name`, `name_loc`, `game_build`, `game_version`, `decoder_version`, `source_path`, `source_sha256`, `raw_json`.

`record_type` values documented there: `item`, `skill`, `status_effect`, `recipe`, `reward`, `rune`, `reference`.

Solisium maps a subset into its own tables. Unknown types are counted and skipped, not forced into the wrong entity. `TLItemEquip.equip_category` tokens observed on this install (`kBow`, `kHead`, `kRing`, and the rest listed in `EquipCategory`) fill typed weapon/armor/accessory rows. Other categories stay `game_item` only.

Default warehouse location on this machine: `D:\TL_Data\warehouse\tl-24829515.sqlite`. That file must not be committed here. The research receipt used `tl-24118850.sqlite`.

### CombatLogDataSource

Parses official CSV. Hit flags: `HitCritical`, `HitDouble` (Heavy). Do not infer crit/heavy from formatting.

### ManualImportDataSource

Accepts `solisium.manual-character` JSON for `user_character` plus loadout tables (`user_equipment`, `user_weapon`, `user_traits`, `user_runes`, `user_skills`, `user_inventory`, `user_materials`, `user_currency`, `user_cooking`, `user_goals`, `user_build`). Missing fields stay null. Reimport of the same character id replaces child rows. The importer must not fill gaps from game data guesses, map display names to warehouse keys, or parse `%LOCALAPPDATA%\TL\Saved\SaveGames`. Inventory, Lucent/Sollant, and cooking level in the document are stored with a warning that they have no verified local file source. See `examples/character.json`.

### PublicRepositoryDataSource

Stub. Live Questlog tRPC fetch is undocumented and ToS-sensitive. User-pasted JSON is `ManualImportDataSource`, not this adapter.

## What changes between patches

Almost all static rows: items, traits, runes, skills, formulas, recipes, localization. Combat log column sets can gain `LogType`s. Steam `buildid` changes even for hotfixes.

Human aliases (`t4`, `nix-4.0.0`) are stored in `dataset_alias` and can be retargeted. Do not use them as primary keys.

Patch-to-patch diffs cannot be proven until a second receipted warehouse exists on the same pipeline. This PC has `24829515`; research recorded `24118850`.

## Licensing constraints on reuse

| Work | Constraint |
| --- | --- |
| TL-Helper | No LICENSE file on GitHub as of research; default copyright. Same-author sibling may import a local warehouse. Do not vendor TL-Helper sources until that repo has an explicit license. |
| FModel | GPL-3. External tool inside TL-Helper only. Never link into Solisium. |
| CUE4Parse | Used by FModel, not by this repo. |
| Game data and assets | NCSoft / Amazon IP. Personal local analysis. Do not ship decoded tables, icons, or localization in git. |
| Questlog | Third-party ToS restricts reproducing game-related content. Prefer decoded warehouse. |
| TLDB internal APIs | Documented for personal projects, no official support. |
| SQLDelight / Kotlin / Compose | Apache-2.0. |
| CKdps / STOOP / tl-dps-mcp | Do not copy. Parse from the publisher spec and independent fixtures. |

## Honesty rule

If a field was not in the source document, the database stores NULL. The UI and engine must say so. Fixtures are synthetic and labeled.
