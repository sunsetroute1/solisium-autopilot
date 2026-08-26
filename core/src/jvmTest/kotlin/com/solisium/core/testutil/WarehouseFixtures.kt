package com.solisium.core.testutil

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

object WarehouseFixtures {
    /**
     * A warehouse holding the same item key twice. `insertGameItem` is a plain INSERT
     * against `UNIQUE(snapshot_id, source_table, source_row_id)`, so the import writes
     * the first row and then fails on the duplicate. Used to prove the import is atomic.
     */
    fun writeBrokenWarehouse(build: String = "24999999"): Path {
        val warehouse = Files.createTempFile("tl-helper-broken", ".sqlite")
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE records (
                      record_id TEXT,
                      row_id TEXT,
                      record_type TEXT,
                      table_name TEXT,
                      name_loc TEXT,
                      game_build TEXT,
                      game_version TEXT,
                      decoder_version TEXT,
                      raw_json TEXT
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO records VALUES
                    ('TLItemLooks_Equip:ok_bow','ok_bow','item','TLItemLooks_Equip','Sound Bow','$build','9.9.9','0.2.0','{"grade":"Epic"}'),
                    ('TLItemLooks_Equip:dupe','dupe_bow','item','TLItemLooks_Equip','Duplicate Bow','$build','9.9.9','0.2.0','{"grade":"Epic"}'),
                    ('TLItemLooks_Equip:dupe-again','dupe_bow','item','TLItemLooks_Equip','Duplicate Bow','$build','9.9.9','0.2.0','{"grade":"Epic"}')
                    """.trimIndent(),
                )
            }
        }
        return warehouse
    }

    fun writeMiniWarehouse(build: String = "24118850"): Path {
        val warehouse = Files.createTempFile("tl-helper-fixture", ".sqlite")
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE records (
                      record_id TEXT,
                      row_id TEXT,
                      record_type TEXT,
                      table_name TEXT,
                      name_loc TEXT,
                      game_build TEXT,
                      game_version TEXT,
                      decoder_version TEXT,
                      raw_json TEXT
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO records VALUES
                    ('TLItemLooks_Equip:fixture_bow','fixture_bow','item','TLItemLooks_Equip','Fixture Longbow','$build','1.431.22.7761','0.2.0','{"grade":"Epic","IconPath":{"assetPath":"/Game/Icon/bow","subPath":""}}'),
                    ('TLItemEquip:fixture_bow','fixture_bow','item','TLItemEquip',null,'$build','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kBow","item_grade":"EItemGrade::kAA"}'),
                    ('TLRuneInfo:fixture_rune','fixture_rune','rune','TLRuneInfo','Fixture Attack Rune','$build','1.431.22.7761','0.2.0','{"grade":"Rare"}'),
                    ('TLRuneGrowth:fixture_growth','fixture_growth','rune','TLRuneGrowth','Growth Curve','$build','1.431.22.7761','0.2.0','{"Name":"growth"}'),
                    ('TLRuneSynergy:fixture_synergy','fixture_synergy','rune','TLRuneSynergy','Fixture Synergy','$build','1.431.22.7761','0.2.0','{"ItemSeasonId":1}'),
                    ('TLSkill:fixture_skill','fixture_skill','skill','TLSkill','Fixture Skill','$build','1.431.22.7761','0.2.0','{"skill_category":"ESkillCategory::kFo"}'),
                    ('TLCookingRecipe:fixture_recipe','fixture_recipe','recipe','TLCookingRecipe','Fixture Stew','$build','1.431.22.7761','0.2.0','{"MainIngredientList":[{"ItemID":"fixture_herb","Count":2}],"SubIngredientList":[{"ItemID":"fixture_missing","Count":1}]}'),
                    ('TLItemLooks:fixture_herb','fixture_herb','item','TLItemLooks','Fixture Herb','$build','1.431.22.7761','0.2.0','{}'),
                    ('TLCraftingMaterialGroup:fixture_bundle','fixture_bundle','reference','TLCraftingMaterialGroup',null,'$build','1.431.22.7761','0.2.0','{"Materials":[{"Item":"fixture_ore","Count":100}]}'),
                    ('TLItemLooks:fixture_ore','fixture_ore','item','TLItemLooks','Fixture Ore','$build','1.431.22.7761','0.2.0','{}'),
                    ('TLEffectProperty:fixture_effect','fixture_effect','status_effect','TLEffectProperty',null,'$build','1.431.22.7761','0.2.0','{"Abnormal":"abn_fixture"}'),
                    ('TLItemStatAttrConverter:1','1','reference','TLItemStatAttrConverter',null,'$build','1.431.22.7761','0.2.0','{"StatType":"EItemStats::kSTR","DisplayItemStatType":"EItemAttrType::kSTR"}'),
                    ('TLStats:str','str','reference','TLStats','Strength','$build','1.431.22.7761','0.2.0','{"stat_enum":"EItemStats::kSTR"}'),
                    ('TLItemTraits:kAllAccuracy','kAllAccuracy','reference','TLItemTraits','Accuracy','$build','1.431.22.7761','0.2.0','{"TraitStat":["EItemTraitStats::kAllAccuracy"]}'),
                    ('TLFormulaParameterNew:fixture_formula','fixture_formula','reference','TLFormulaParameterNew',null,'$build','1.431.22.7761','0.2.0','{"FormulaParameter":[{"skill_level":1,"formula_type":"EFormulaType::kAmountFromMinMax"}]}'),
                    ('TLItemStats:fixture_bow','fixture_bow','item','TLItemStats',null,'$build','1.431.22.7761','0.2.0','{"main_stat_base_id":"kBow_Basic","main_stat_base_seed":2,"extra_stat_base_id":"M8_Extra_Stat","extra_fixed_stat_seed_1":1,"main_stat_enchant_id":"kBow","main_level_stat_id":"kNone_Main_Level","enchant_level_max":2}'),
                    ('TLItemStats:fixture_orphan','fixture_orphan','item','TLItemStats',null,'$build','1.431.22.7761','0.2.0','{"main_stat_base_id":"kBow_Basic","main_stat_base_seed":2}'),
                    ('TLItemMainStatInit:1','1','reference','TLItemMainStatInit',null,'$build','1.431.22.7761','0.2.0','{"Name":"1","id":"kBow_Basic","seed":1,"attack_power_main_hand":11,"hp_max":0}'),
                    ('TLItemMainStatInit:2','2','reference','TLItemMainStatInit',null,'$build','1.431.22.7761','0.2.0','{"Name":"2","id":"kBow_Basic","seed":2,"attack_power_main_hand":17,"attack_speed_main_hand":550,"hp_max":0}'),
                    ('TLItemExtraStatInit:1','1','reference','TLItemExtraStatInit',null,'$build','1.431.22.7761','0.2.0','{"Name":"1","seed_group_id":"M8_Extra_Stat","stat_seed":1,"str":4,"dex":0}'),
                    ('TLItemMainStatEnchant:1','1','reference','TLItemMainStatEnchant',null,'$build','1.431.22.7761','0.2.0','{"id":"kBow","enchant_level":1,"attack_power_main_hand":5,"damage_reduction":0}'),
                    ('TLItemMainStatEnchant:2','2','reference','TLItemMainStatEnchant',null,'$build','1.431.22.7761','0.2.0','{"id":"kBow","enchant_level":2,"attack_power_main_hand":10}'),
                    ('TLItemMainStatEnchant:3','3','reference','TLItemMainStatEnchant',null,'$build','1.431.22.7761','0.2.0','{"id":"kBow","enchant_level":3,"attack_power_main_hand":15}'),
                    ('TLItemMainLevelStat:1','1','reference','TLItemMainLevelStat',null,'$build','1.431.22.7761','0.2.0','{"Id":"kNone_Main_Level","item_level":1,"melee_armor":7}'),
                    ('TLDataHandle:skip_me','skip_me','reference','TLDataHandle','Ignored Handle','$build','1.431.22.7761','0.2.0','{}')
                    """.trimIndent(),
                )
            }
        }
        return warehouse
    }

    fun withPcClass(warehouse: Path, build: String = "24118850"): Path {
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO records VALUES
                    ('TLPcClass:gladiator','gladiator','reference','TLPcClass','Gladiator','$build','1.431.22.7761','0.2.0','{"weapon_a":"EItemCategory::kSword2h","weapon_b":"EItemCategory::kSpear"}'),
                    ('TLPcClass:incomplete','incomplete','reference','TLPcClass','No Pair','$build','1.431.22.7761','0.2.0','{"weapon_a":"EItemCategory::kGauntlet"}'),
                    ('TLItemLooks_Equip:fixture_gs','fixture_gs','item','TLItemLooks_Equip','Fixture Greatsword','$build','1.431.22.7761','0.2.0','{"grade":"Epic"}'),
                    ('TLItemEquip:fixture_gs','fixture_gs','item','TLItemEquip',null,'$build','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kSword2h","item_grade":"EItemGrade::kAA"}'),
                    ('TLItemLooks_Equip:fixture_spear','fixture_spear','item','TLItemLooks_Equip','Fixture Spear','$build','1.431.22.7761','0.2.0','{"grade":"Epic"}'),
                    ('TLItemEquip:fixture_spear','fixture_spear','item','TLItemEquip',null,'$build','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kSpear","item_grade":"EItemGrade::kAA"}')
                    """.trimIndent(),
                )
            }
        }
        return warehouse
    }

    fun withCalanthiaGear(warehouse: Path, build: String = "24118850"): Path {
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO records VALUES
                    ('TLItemLooks_Equip:calanthia_head','calanthia_head','item','TLItemLooks_Equip','Calanthia''s Visage','$build','1.431.22.7761','0.2.0','{"grade":"Heroic"}'),
                    ('TLItemEquip:calanthia_head','calanthia_head','item','TLItemEquip',null,'$build','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kHead","item_grade":"EItemGrade::kAAA"}'),
                    ('TLItemLooks:calanthia_box','calanthia_box','item','TLItemLooks','Calanthia Armor Selection Chest','$build','1.431.22.7761','0.2.0','{}')
                    """.trimIndent(),
                )
            }
        }
        return warehouse
    }

    fun withCombatPower(warehouse: Path, build: String = "24118850"): Path {
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO records VALUES
                    ('TLItemCombatPower:weapon_aa_t2','weapon_aa_t2','reference','TLItemCombatPower',null,'$build','1.431.22.7761','0.2.0','{"BaseCombatPower":64,"ItemPotentialCombatPower":30,"Category":"ETLCombatPowerCategory::kWeapon","ItemEnchantCombatPowerList":[{"CombatPower":0},{"CombatPower":8}]}'),
                    ('TLItemCombatPower:weapon_aaa_t1','weapon_aaa_t1','reference','TLItemCombatPower',null,'$build','1.431.22.7761','0.2.0','{"BaseCombatPower":80,"Category":"ETLCombatPowerCategory::kWeapon"}'),
                    ('TLItemLooks_Equip:bow_aa_t2_fixture','bow_aa_t2_fixture','item','TLItemLooks_Equip','Fixture Tier Bow','$build','1.431.22.7761','0.2.0','{"grade":"Epic"}'),
                    ('TLItemEquip:bow_aa_t2_fixture','bow_aa_t2_fixture','item','TLItemEquip',null,'$build','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kBow","item_grade":"EItemGrade::kAA"}'),
                    ('TLItemLooks_Equip:sword_a_t1_fixture','sword_a_t1_fixture','item','TLItemLooks_Equip','Fixture Unresolved Sword','$build','1.431.22.7761','0.2.0','{"grade":"Rare"}'),
                    ('TLItemEquip:sword_a_t1_fixture','sword_a_t1_fixture','item','TLItemEquip',null,'$build','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kSword","item_grade":"EItemGrade::kA"}'),
                    ('TLItemLooks_Equip:sword_aaa_unambiguous','sword_aaa_unambiguous','item','TLItemLooks_Equip','Fixture AAA Sword','$build','1.431.22.7761','0.2.0','{"grade":"Heroic"}'),
                    ('TLItemEquip:sword_aaa_unambiguous','sword_aaa_unambiguous','item','TLItemEquip',null,'$build','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kSword2h","item_grade":"EItemGrade::kAAA"}')
                    """.trimIndent(),
                )
            }
        }
        return warehouse
    }

    fun withSkillFamilies(warehouse: Path, build: String = "24118850"): Path {
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO records VALUES
                    ('TLSkill:WP_SW2_Slam','WP_SW2_Slam','skill','TLSkill','Gauntlet Slam','$build','1.431.22.7761','0.2.0','{"skill_category":"ESkillCategory::kSkill"}'),
                    ('TLSkill:WM_GT_Unstoppable','WM_GT_Unstoppable','skill','TLSkill','Unstoppable','$build','1.431.22.7761','0.2.0','{"skill_category":"ESkillCategory::kPassive"}'),
                    ('TLSkill:WP_Item_core','WP_Item_core','skill','TLSkill','Talus''s Transcendent Barrier','$build','1.431.22.7761','0.2.0','{"skill_category":"ESkillCategory::kItem"}'),
                    ('TLSkill:Gem_Attack_01','Gem_Attack_01','skill','TLSkill','Gemstone Attack','$build','1.431.22.7761','0.2.0','{"skill_category":"ESkillCategory::kItem"}'),
                    ('TLItemLooks:perk_orb_aa_t3_boss_001','perk_orb_aa_t3_boss_001','item','TLItemLooks','Skill Core: Talus''s Transcendent Barrier','$build','1.431.22.7761','0.2.0','{}')
                    """.trimIndent(),
                )
            }
        }
        return warehouse
    }
}
