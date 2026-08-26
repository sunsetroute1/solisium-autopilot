package com.solisium.core.source

import com.solisium.core.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CombatPowerLookupTest {
    @Test
    fun itemIdTierMapsBowAaT2() {
        val mapping = CombatPowerLookup.infer(
            itemId = "bow_aa_t2_polymorph_001",
            equipCategory = "EItemCategory::kBow",
            itemGrade = "EItemGrade::kAA",
            affectsCategoryLevel = null,
            levelSelectId = null,
            minLevel = null,
            maxLevel = null,
            availableRows = setOf("weapon_aa_t2", "weapon_aaa_t1"),
        )
        assertEquals("weapon_aa_t2", mapping.rowId)
        assertEquals("item-id-tier", mapping.evidence)
    }

    @Test
    fun gradeAStaysUnresolvedWhenSeveralRowsCouldMatch() {
        val mapping = CombatPowerLookup.infer(
            itemId = "sword_plain",
            equipCategory = "EItemCategory::kSword",
            itemGrade = "EItemGrade::kA",
            affectsCategoryLevel = null,
            levelSelectId = null,
            minLevel = null,
            maxLevel = null,
            availableRows = setOf("weapon_a_t1", "weapon_a_t2"),
        )
        assertNull(mapping.rowId)
        assertEquals("unresolved", mapping.evidence)
    }

    @Test
    fun unambiguousAaaMapsWhenExactlyOneRowExists() {
        val mapping = CombatPowerLookup.infer(
            itemId = "calanthia_head",
            equipCategory = "EItemCategory::kHead",
            itemGrade = "EItemGrade::kAAA",
            affectsCategoryLevel = null,
            levelSelectId = null,
            minLevel = null,
            maxLevel = null,
            availableRows = setOf("head_aaa_t1", "weapon_aaa_t1"),
        )
        assertEquals("head_aaa_t1", mapping.rowId)
        assertEquals("source-unambiguous-grade", mapping.evidence)
    }

    @Test
    fun seasonalSelectorMapsWhenBoundsMatch() {
        val mapping = CombatPowerLookup.infer(
            itemId = "legacy_token_c_t1_should_be_ignored",
            equipCategory = "EItemCategory::kChest",
            itemGrade = "EItemGrade::kA",
            affectsCategoryLevel = "EBool::T",
            levelSelectId = "ItemGroup_T3",
            minLevel = 21,
            maxLevel = 50,
            availableRows = setOf("armor_a_S1"),
        )
        assertEquals("armor_a_S1", mapping.rowId)
        assertEquals("source-level-selector:ItemGroup_T3", mapping.evidence)
    }

    @Test
    fun componentsReadExtractedBaseAndSkipPotentialByDefault() {
        val json = JsonParser.parse(
            """{"BaseCombatPower":64,"ItemPotentialCombatPower":30,"ItemEnchantCombatPowerList":[{"CombatPower":0},{"CombatPower":8}]}""",
        )
        val parts = CombatPowerLookup.components(json)
        assertEquals(64L, parts.base)
        assertEquals(0L, parts.enchant)
        assertEquals(0L, parts.potential)
        assertEquals(64L, parts.total)
        assertEquals(8L, CombatPowerLookup.listPower(json, "ItemEnchantCombatPowerList", 1))
        val withPotential = CombatPowerLookup.components(json, enchantIndex = 1, includePotential = true)
        assertEquals(8L, withPotential.enchant)
        assertEquals(30L, withPotential.potential)
        assertEquals(102L, withPotential.total)
        assertEquals(1, CombatPowerLookup.enchantIndex(json, itemLevel = 1))
    }
}
