package com.solisium.core.bootstrap

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Synthetic TL-Helper warehouse used in the installer starter pack. Derived from test
 * fixtures; large enough to exercise catalog, build, and combat screens out of the box.
 */
object StarterWarehouse {
    const val BUILD_ID = "24118850"

    fun write(path: Path) {
        if (Files.exists(path)) Files.delete(path)
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
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
                    ('TLItemLooks_Equip:fixture_bow','fixture_bow','item','TLItemLooks_Equip','Fixture Longbow','$BUILD_ID','1.431.22.7761','0.2.0','{"grade":"Epic","IconPath":{"assetPath":"/Game/Icon/bow","subPath":""}}'),
                    ('TLItemEquip:fixture_bow','fixture_bow','item','TLItemEquip',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kBow","item_grade":"EItemGrade::kAA"}'),
                    ('TLRuneInfo:fixture_rune','fixture_rune','rune','TLRuneInfo','Fixture Attack Rune','$BUILD_ID','1.431.22.7761','0.2.0','{"grade":"Rare"}'),
                    ('TLRuneGrowth:fixture_growth','fixture_growth','rune','TLRuneGrowth','Growth Curve','$BUILD_ID','1.431.22.7761','0.2.0','{"Name":"growth"}'),
                    ('TLRuneSynergy:fixture_synergy','fixture_synergy','rune','TLRuneSynergy','Fixture Synergy','$BUILD_ID','1.431.22.7761','0.2.0','{"ItemSeasonId":1}'),
                    ('TLSkill:fixture_skill','fixture_skill','skill','TLSkill','Fixture Skill','$BUILD_ID','1.431.22.7761','0.2.0','{"skill_category":"ESkillCategory::kFo"}'),
                    ('TLCookingRecipe:fixture_recipe','fixture_recipe','recipe','TLCookingRecipe','Fixture Stew','$BUILD_ID','1.431.22.7761','0.2.0','{"MainIngredientList":[{"ItemID":"fixture_herb","Count":2}],"SubIngredientList":[{"ItemID":"fixture_missing","Count":1}]}'),
                    ('TLItemLooks:fixture_herb','fixture_herb','item','TLItemLooks','Fixture Herb','$BUILD_ID','1.431.22.7761','0.2.0','{}'),
                    ('TLCraftingMaterialGroup:fixture_bundle','fixture_bundle','reference','TLCraftingMaterialGroup',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"Materials":[{"Item":"fixture_ore","Count":100}]}'),
                    ('TLItemLooks:fixture_ore','fixture_ore','item','TLItemLooks','Fixture Ore','$BUILD_ID','1.431.22.7761','0.2.0','{}'),
                    ('TLEffectProperty:fixture_effect','fixture_effect','status_effect','TLEffectProperty',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"Abnormal":"abn_fixture"}'),
                    ('TLItemStatAttrConverter:1','1','reference','TLItemStatAttrConverter',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"StatType":"EItemStats::kSTR","DisplayItemStatType":"EItemAttrType::kSTR"}'),
                    ('TLStats:str','str','reference','TLStats','Strength','$BUILD_ID','1.431.22.7761','0.2.0','{"stat_enum":"EItemStats::kSTR"}'),
                    ('TLItemTraits:kAllAccuracy','kAllAccuracy','reference','TLItemTraits','Accuracy','$BUILD_ID','1.431.22.7761','0.2.0','{"TraitStat":["EItemTraitStats::kAllAccuracy"]}'),
                    ('TLFormulaParameterNew:fixture_formula','fixture_formula','reference','TLFormulaParameterNew',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"FormulaParameter":[{"skill_level":1,"formula_type":"EFormulaType::kAmountFromMinMax"}]}'),
                    ('TLItemStats:fixture_bow','fixture_bow','item','TLItemStats',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"main_stat_base_id":"kBow_Basic","main_stat_base_seed":2,"extra_stat_base_id":"M8_Extra_Stat","extra_fixed_stat_seed_1":1,"main_stat_enchant_id":"kBow","main_level_stat_id":"kNone_Main_Level","enchant_level_max":2}'),
                    ('TLItemMainStatInit:2','2','reference','TLItemMainStatInit',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"Name":"2","id":"kBow_Basic","seed":2,"attack_power_main_hand":17,"attack_speed_main_hand":550,"hp_max":0}'),
                    ('TLItemMainStatEnchant:1','1','reference','TLItemMainStatEnchant',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"id":"kBow","enchant_level":1,"attack_power_main_hand":5}'),
                    ('TLItemMainStatEnchant:2','2','reference','TLItemMainStatEnchant',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"id":"kBow","enchant_level":2,"attack_power_main_hand":10}'),
                    ('TLItemMainLevelStat:1','1','reference','TLItemMainLevelStat',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"Id":"kNone_Main_Level","item_level":1,"melee_armor":7}'),
                    ('TLPcClass:gladiator','gladiator','reference','TLPcClass','Gladiator','$BUILD_ID','1.431.22.7761','0.2.0','{"weapon_a":"EItemCategory::kSword2h","weapon_b":"EItemCategory::kSpear"}'),
                    ('TLItemLooks_Equip:fixture_gs','fixture_gs','item','TLItemLooks_Equip','Fixture Greatsword','$BUILD_ID','1.431.22.7761','0.2.0','{"grade":"Epic"}'),
                    ('TLItemEquip:fixture_gs','fixture_gs','item','TLItemEquip',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kSword2h","item_grade":"EItemGrade::kAA"}'),
                    ('TLItemLooks_Equip:calanthia_head','calanthia_head','item','TLItemLooks_Equip','Calanthia''s Visage','$BUILD_ID','1.431.22.7761','0.2.0','{"grade":"Heroic"}'),
                    ('TLItemEquip:calanthia_head','calanthia_head','item','TLItemEquip',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kHead","item_grade":"EItemGrade::kAAA"}'),
                    ('TLItemCombatPower:weapon_aa_t2','weapon_aa_t2','reference','TLItemCombatPower',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"BaseCombatPower":64,"ItemPotentialCombatPower":30,"Category":"ETLCombatPowerCategory::kWeapon","ItemEnchantCombatPowerList":[{"CombatPower":0},{"CombatPower":8}]}'),
                    ('TLItemLooks_Equip:bow_aa_t2_fixture','bow_aa_t2_fixture','item','TLItemLooks_Equip','Fixture Tier Bow','$BUILD_ID','1.431.22.7761','0.2.0','{"grade":"Epic"}'),
                    ('TLItemEquip:bow_aa_t2_fixture','bow_aa_t2_fixture','item','TLItemEquip',null,'$BUILD_ID','1.431.22.7761','0.2.0','{"equip_category":"EItemCategory::kBow","item_grade":"EItemGrade::kAA"}'),
                    ('TLSkill:WP_SW2_Slam','WP_SW2_Slam','skill','TLSkill','Gauntlet Slam','$BUILD_ID','1.431.22.7761','0.2.0','{"skill_category":"ESkillCategory::kSkill"}'),
                    ('TLSkill:WP_Item_core','WP_Item_core','skill','TLSkill','Talus''s Transcendent Barrier','$BUILD_ID','1.431.22.7761','0.2.0','{"skill_category":"ESkillCategory::kItem"}')
                    """.trimIndent(),
                )
            }
        }
    }
}
