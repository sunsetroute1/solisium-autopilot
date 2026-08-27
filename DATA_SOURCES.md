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
| Skills | `TLSkill`, `TLSkillLevelSetting` | High for names/levels; skill-screen family is derived from row-id prefixes | Yes | Yes | verified names/`skill_category`; `game_skill.family` is derived (`WP_` weapon, `WM_` mastery nodes, `Gem_` gemstone, `WP_Item_` equipment, `WP_Polymorph` morph). Level-setting table is still skipped |
| Skill formulas | `TLFormulaParameterNew` (10,786 rows on `24829515`); 130/210 player skill sets exact, 51 derived, 29 unresolved in TL-Helper | High only where mapping is exact | Import yes; use in DPS no until mapped | Yes | partial — imported as `game_skill_formula` at `confidence=extracted` with no skill link; nothing computes damage from them |
| Skill effects / buffs | `TLEffectProperty` (55,333 rows on `24829515`), `TLAbnormalState_*` including Gauntlet | High for client-visible fields | Yes | Yes | verified as `game_skill_effect` keyed by table+row; no skill-id join (effects expose `Abnormal`, not a `TLSkill` id) |
| Combat power table | `TLItemCombatPower` (132 rows decoded) | Item-component weights; live character CP aggregator unresolved | Yes | Yes | verified as `game_combat_power` (`confidence=extracted`). Item-to-row map is `game_item_power` (`confidence=derived`). **Not** live window CP or a gear-score watermark |
| Modeled CP / gear score | Questlog character-builder layout over warehouse item weights | Hybrid: extracted item components when mapped; community skill ×2, mastery ×3, 250 equipment base | Yes | Yes | modeled estimate on Build. Potential adds `ItemPotentialCombatPower`. Unresolved A/AA items contribute 0. Modeled GS is the equipment subtotal, not the typed watermark |
| Class types (weapon-pair titles) | `TLPcClass` when collected; otherwise community Questlog/Metabattle table | Extracted rows win; community overlay never writes `game_class` | Yes | Yes | Build lists them as selectable class types. Unknown pairs, including unpublished gauntlet titles, stay unnamed |
| Named stats | `TLStats` (292 rows), localized through `TLItemStatAttrConverter` + `TLStatAttrLooks` | High where the converter target is specific | Yes | Yes | verified as `game_stat`. Stats whose converter target is `kNone` (~86 of them) resolve to **no** name rather than inheriting an arbitrary one; the raw stat key is shown instead |
| Cooking recipes | `TLCookingRecipe` (decoder priority set) | High for decoded rows | Yes | Yes | verified table; Phase 1 maps `record_type=recipe` into `game_recipe` |
| Crafting recipes | `TLCraftingRecipe` | High for decoded rows | Yes | Yes | verified table; same recipe mapping |
| Materials | `TLCraftingMaterialGroup.Materials[].Item`, `TLCookingRecipe.*IngredientList[].ItemID` | High — explicit ingredient fields, resolved against known item rows | Yes | Yes | verified as `game_material` (319 rows). 20 refs unresolved (fish, in `TLFishingFishInfo`) and reported as a warning. Crafting `Ingredients[].RowName` are bundle ids, resolved only through `TLCraftingMaterialGroup`, never by stripping the trailing count |
| Monster drop profiles | `TLRewardNpcFoItem` (1,847 rows on `24829515`) | High for npc ids and lottery group pointers in `raw_json` | Yes | Yes | verified as `game_boss`; links to `public_lottery_group_id`, not item ids |
| Monster drop rates (exact) | `TLItemLotteryUnit` + `TLItemLotteryPublicGroup`, resolved through reward profile lottery ids | High once collected and mapped | Yes after TL-Helper collect | Yes | **not collected** on `24829515` — warehouse has reward profiles only. Import maps `TLItemLotteryUnit` → `game_item_drop` (`confidence=extracted`). Questlog sync adds community rows |
| Food definitions | `TLCookingRecipe` results are lottery ids, not item ids | Needs `TLItemLotteryUnit` | Yes once collected | Yes | partial — `game_food` empty; only 6 of 81 cooking row ids are item ids |
| Dungeons / bosses | Dedicated tables exist: `TLAbyssDungeon`, `TLChallengePartyDungeon`, `TLInteractiveDungeon`, `TLFieldBoss`, `TLGuildRaid` (106 matches in the 1,415-table inventory) | Unknown until decoded | Yes once collected | Yes | not collected — available, not unavailable |
| Game build / version | Steam `appmanifest_2429640.acf` (`buildid`); warehouse `game_build` / `game_version` | High | Yes | Yes | verified |
| Pak content change | Hash/mtime of files under `TL\Content\Paks` | High for "something changed" | Yes | Yes | verified detect-only. Desktop polls every 15 minutes (`SOLISIUM_PATCH_WATCH_MS` override). Matching `tl-<build>.sqlite` is auto-imported. Solisium never unpacks paks |
| Encrypted table bytes | Installed paks | Present but encrypted | Not in this repo | Yes | out of scope (TL-Helper) |
| Character level, CP, gear, traits, runes, skills, masteries | Manual JSON / TL-Helper BuildSnapshot / user-supplied Questlog package | High if the user maintains it | Semi | Yes — bind to a snapshot | verified as import path; **not** in local saves |
| Weapon mastery levels (167 / 151) | Manual JSON `weapon_mastery` | Typed from the skills screen | Semi | Yes | verified as import path; **not** `WM_` catalog nodes. The community mastery CP term (`×3` plus 130/260/390/520 bonuses) uses these numbers; that is not the live window aggregator |
| Skill specialization / cores / Guardian / Transcendence / Material Effect / Gemstone / equipment skills | Manual JSON `build_layers` (and alias arrays) plus classified `TLSkill` rows | Presence only | Semi | Yes | specialization, Transcendence, and Material Effect have no dedicated table on this collect. `TLWeaponSpecializationStat`, `TLItemMaterialStat`, and `TLSkillOptionalDataForPc` are in TL-Helper SamplePlan for the next collect. NPC `Skill_ImmortalGuardian*` rows are not the player Guardian slot |
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
| Questlog catalog / builds | Public tRPC read: `database.searchEntities`, `skillBuilder.getSkillSets` (user-initiated) | Community mirror, change-prone | Optional; default off | Yes | optional overlay on Build |
| Blessings | Not in Questlog builder payloads (TL-Helper out of scope) | — | No | Yes | unavailable |

## Adapter notes

### InstalledGameDataSource

Reads Steam library metadata and pak inventory. Does not decrypt archives, does not load FModel, does not search the executable for keys. Desktop `PatchWatch` polls the install and `tl-*.sqlite` files every 15 minutes and auto-imports a warehouse whose SHA-256 is not the active snapshot. CLI: `solisium patch-check [--import]`.

Typical install:

```text
<SteamLibrary>\steamapps\common\Throne and Liberty\TL\Content\Paks
<SteamLibrary>\steamapps\appmanifest_2429640.acf
```

### TLHelperDataSource

Reads a TL-Helper warehouse (`tl-<build>.sqlite`) as an **external** schema. Expected warehouse facts (from TL-Helper `docs/data-contract.md`): table `records` with `record_id`, `row_id`, `record_type`, `table_name`, `name_loc`, `game_build`, `game_version`, `decoder_version`, `source_path`, `source_sha256`, `raw_json`.

`record_type` values documented there: `item`, `skill`, `status_effect`, `recipe`, `reward`, `rune`, `reference`.

Solisium maps a subset into its own tables. Unknown types are counted and skipped, not forced into the wrong entity. `TLItemEquip.equip_category` tokens observed on this install (`kBow`, `kHead`, `kRing`, and the rest listed in `EquipCategory`) fill typed weapon/armor/accessory rows. Other categories stay `game_item` only. `TLPcClass` rows that carry two combat weapons and a title become `game_class`; the current `24829515` warehouse does not collect that table yet, so class titles fall back to the community pair list until the next collect includes it. The Build screen lists those titles as class types and limits weapon ranks to the selected pair. `TLItemCombatPower` becomes `game_combat_power`. Matching those rows to items is derived (no warehouse foreign key); unresolved A/AA families stay unmapped. The Build screen scores a Questlog-shaped modeled CP from those weights plus community skill/mastery/base terms; that sum is not a substitute for the typed character-window combat power. `TLSkill` row-id prefixes fill `game_skill.family`; re-import the warehouse after schema 8 to populate existing databases. `TLWeaponSpecializationStat`, `TLItemMaterialStat`, and `TLSkillOptionalDataForPc` are not in the `24829515` collect.

Default warehouse location on this machine: `D:\TL_Data\warehouse\tl-24829515.sqlite`. That file must not be committed here. The research receipt used `tl-24118850.sqlite`.

### CombatLogDataSource

Parses official CSV. Hit flags: `HitCritical`, `HitDouble` (Heavy). Do not infer crit/heavy from formatting.

### ManualImportDataSource

Accepts `solisium.manual-character` JSON for `user_character` plus loadout tables (`user_equipment`, `user_weapon`, `user_traits`, `user_runes`, `user_skills`, `user_weapon_mastery`, `user_build_layer`, `user_inventory`, `user_materials`, `user_currency`, `user_cooking`, `user_goals`, `user_build`). Missing fields stay null. Reimport of the same character id replaces child rows. Default location is `%USERPROFILE%\.solisium\characters\character.json`, created on first run from the bundled starter if the folder is empty. `SOLISIUM_CHARACTER` and a remembered last path win over that folder. Combat power and gear score are stored from the JSON as typed window values. Modeled CP/GS on Build is a separate Questlog-shaped estimate over the loadout and warehouse item-power map; it is not written back onto `user_character`. Allocated Strength / Dexterity / Wisdom / Perception / Fortitude are stored the same way; their typed sum is shown on the Character screen and is not Combat Power. Class title is filled from equipped main + offhand when both resolve: extracted `game_class` (`TLPcClass`) first, then the community weapon-pair table, then a typed override stored as `class_source=manual` and shown as “manually overridden character class.” Unknown pairs (including new weapons) keep the two weapon labels and do not invent a title. Equipment, weapons, inventory, skills, and build layers may use in-game `name`; at query time those names are matched to the active warehouse snapshot. Weapon mastery levels (167 / 151) are typed numbers, not `WM_` catalog nodes; they feed the community mastery term on modeled CP. The importer must not invent equipped or bag items that are absent from the JSON, and must not parse `%LOCALAPPDATA%\TL\Saved\SaveGames`. Inventory, Lucent/Sollant, and cooking level in the document are stored with a warning that they have no verified local file source. See `examples/character.json`.

### PublicRepositoryDataSource

Does not import into `game_*` tables. Live Questlog tRPC (`database.searchEntities`, `skillBuilder.getSkillSets`, `characterBuilder.getCharacter`) and the TLDB homepage patch banner are a **user-initiated overlay** on the Build screen / `solisium query advise --meta` / `--slug`. Public character listing (`getCharacters`) is 403 without auth; a pasted slug is required. Missing slugs return `{status: NOT_FOUND}`. Results are labeled community, never extracted client data. User-pasted JSON remains `ManualImportDataSource`.

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
