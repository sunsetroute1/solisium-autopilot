# Schema

Solisium schema version **2**. SQLDelight migrations live under `core/src/commonMain/sqldelight/com/solisium/core/db/` as `<from-version>.sqm`; `1.sqm` adds `game_item_stat`. SQLite is the only database.

`JvmDatabase.openOrCreate` reads `PRAGMA user_version` and applies pending migrations. Databases written before migrations existed report `user_version` 0, which still picks up every migration, so an existing local database gains new tables instead of silently lacking them.

Static game rows always belong to a `dataset_snapshot`. User rows do not store patch-wide definitions. Recommendations (later) must store the `snapshot_id` they used.

## Provenance

### `dataset_snapshot`

| Column | Meaning |
| --- | --- |
| `id` | Stable snapshot id (UUID) |
| `source` | `tl_helper` / `public_repo` / `manual` / `combat_log` / `installed_game` |
| `extracted_at` | ISO-8601 UTC |
| `game_build` | Steam build id (e.g. `24118850`) |
| `game_version` | Client version string (e.g. `1.431.22.7761`) |
| `schema_version` | Solisium schema version that wrote the rows |
| `source_path` | File imported |
| `source_hash` | SHA-256 of that file when appropriate |
| `decoder_version` | TL-Helper decoder version if known |
| `active` | `1` if this is the default snapshot for queries |

Multiple snapshots may exist. Activating one clears the previous active flag.

### `dataset_alias`

Human labels (`nix-4.0.0`, `t4`) → `snapshot_id`. Aliases are not primary keys.

## Static game (FK `snapshot_id`)

Every game table includes `source_table` + `source_row_id` so a warehouse key such as `TLItemLooks_Equip` / `sword_aa_S1_arch_002` stays traceable without becoming our primary key.

| Table | Purpose |
| --- | --- |
| `game_item` | Generic item row |
| `game_weapon` | Typed weapon when `TLItemEquip.equip_category` is a verified weapon token |
| `game_armor` | Typed armor for verified armor tokens |
| `game_accessory` | Typed accessory for verified accessory tokens |
| `game_trait` | Trait definitions (`TLItemTraits`, 217 rows); names resolve via `TraitStat` → `TLItemStatAttrConverter` → `TLStatAttrLooks.ItemUIName` |
| `game_rune` | Rune definitions (`TLRuneInfo`) |
| `game_rune_synergy` | Rune synergy definitions (`TLRuneSynergy`) |
| `game_skill` | Skills |
| `game_skill_effect` | Status/effect rows (`TLEffectProperty`, `TLAbnormalState_*`); no skill-id join yet |
| `game_skill_formula` | `TLFormulaParameterNew` rows at `confidence` `extracted`; `expression` lists the distinct `formula_type` values. Nothing computes damage from them. `confidence` is `extracted` / `derived` / `modeled` / `unsupported` |
| `game_stat` | Named stats (`TLStats`, localized through `TLStatAttrLooks`) |
| `game_item_stat` | Per-item base stat values from `TLItemMainStatInit`, reached through `TLItemStats.main_stat_base_id` + `main_stat_base_seed`. `raw_value` is the unscaled client integer; `scope` is `main_base`; `confidence` is `extracted` |
| `game_food` | Food items (empty; needs `TLItemLotteryUnit` to resolve cooking results) |
| `game_recipe` | Cooking/crafting recipes (Phase 1 mapper target) |
| `game_material` | Items the client lists as ingredients in `TLCraftingMaterialGroup.Materials[].Item` or `TLCookingRecipe.*IngredientList[].ItemID`, resolved to a known item row |
| `game_dungeon` | Dungeons (empty; `TLAbyssDungeon` and friends exist but are not collected) |
| `game_boss` | Bosses (empty; `TLFieldBoss` and friends exist but are not collected) |
| `game_progression` | Progression tracks (empty; `TLGrowthMission` / `TLGrowthResource` not collected) |

`game_*` must not store Lucent balances or other user state.

## User state

Independent of snapshot. Bind advice to a snapshot at query time.

| Table | Purpose |
| --- | --- |
| `user_character` | Identity, level, optional CP |
| `user_equipment` | Equipped items by slot |
| `user_weapon` | Weapon slots / item level |
| `user_traits` | Selected trait ranks |
| `user_runes` | Slotted runes |
| `user_skills` | Skill loadout |
| `user_inventory` | Bags (manual; no local file source) |
| `user_materials` | Material stacks |
| `user_currency` | Lucent / Sollant (manual) |
| `user_cooking` | Cooking level (manual) |
| `user_build` | Named loadout presets |
| `user_goals` | Decision-engine goals |
| `user_combat_session` | One parsed log file / fight |

## Combat

`combat_event` stores one CSV row (or a preserved unknown line). Derived summary tables are not in v1. Observed per-skill totals are computed in memory from events.

Observed DPS is computed in memory from events (`sum(damage)/duration`). It is not a column that claims modeled damage.

## Manual character JSON

Schema id `solisium.manual-character`, `schemaVersion` 1. Top-level `character` holds identity. Arrays `equipment`, `weapons`, `traits`, `runes`, `skills`, `inventory`, `materials`, `currency`, `goals`, `builds` are optional. Equipment and weapons require `slot`. Traits/runes/skills need `source_table` / `source_row_id` (display `name` is not a key). `cooking.level` or `cooking_level` is optional. `trait.slot` is ignored in schema v1.

Loadout keys are resolved at query time against a `dataset_snapshot`. Missing keys stay unresolved; names are not guessed from similar items.

## What is not in v2

`dataset_diff`, upgrade-action history, market listings. Add them when Phase 7–8 exists, as new migrations, without rewriting snapshot rows.

Also absent by choice: enchant and item-level stat curves (`TLItemMainStatEnchant`, `TLItemMainLevelStat`) and extra/rolled stats. `TLItemExtraStatInit` is **not** mapped — all 1,837 item stat rows point at the same `M8_Extra_Stat` group, so it describes what a rolled extra stat would be worth, not what any item has.
