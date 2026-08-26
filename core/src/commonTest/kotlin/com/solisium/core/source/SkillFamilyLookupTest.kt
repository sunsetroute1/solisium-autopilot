package com.solisium.core.source

import com.solisium.core.domain.SkillFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillFamilyLookupTest {
    @Test
    fun weaponPrefixMapsGreatswordAndSpear() {
        val gs = SkillFamilyLookup.classify("WP_SW2_GauntletSlam")
        assertEquals(SkillFamily.Weapon, gs.family)
        assertEquals("kSword2h", gs.weaponToken)
        assertEquals("derived", gs.confidence)

        val spear = SkillFamilyLookup.classify("WP_SP_Pierce")
        assertEquals(SkillFamily.Weapon, spear.family)
        assertEquals("kSpear", spear.weaponToken)
    }

    @Test
    fun masteryPrefixMapsGauntletNodes() {
        val node = SkillFamilyLookup.classify("WM_GT_Unstoppable")
        assertEquals(SkillFamily.Mastery, node.family)
        assertEquals("kGauntlet", node.weaponToken)
        assertEquals("derived", node.confidence)
    }

    @Test
    fun equipmentGemstoneAndMorphPrefixes() {
        assertEquals(SkillFamily.Equipment, SkillFamilyLookup.classify("WP_Item_perk_core").family)
        assertEquals(SkillFamily.Gemstone, SkillFamilyLookup.classify("Gem_Attack_01").family)
        assertEquals(SkillFamily.Morph, SkillFamilyLookup.classify("WP_Polymorph_Owl").family)
    }

    @Test
    fun foodCategoryAndUnknownStayOther() {
        val food = SkillFamilyLookup.classify("fixture_skill", "ESkillCategory::kFo")
        assertEquals(SkillFamily.Other, food.family)
        assertEquals("extracted", food.confidence)

        val unknown = SkillFamilyLookup.classify("WP_ZZ_Mystery")
        assertEquals(SkillFamily.Other, unknown.family)
        assertEquals("unresolved", unknown.confidence)
    }

    @Test
    fun skillCoreItemsMatchPerkIdsAndNames() {
        assertTrue(SkillFamilyLookup.isSkillCoreItem("perk_orb_aa_t3_boss_001", null))
        assertTrue(SkillFamilyLookup.isSkillCoreItem("looks_row", "Skill Core: Talus's Transcendent Barrier"))
        assertFalse(SkillFamilyLookup.isSkillCoreItem("sword_aa_t1", "Fixture Greatsword"))
    }

    @Test
    fun parseWeaponTokenAcceptsScreenLabels() {
        assertEquals("kSword2h", SkillFamilyLookup.parseWeaponToken("Greatsword"))
        assertEquals("kSpear", SkillFamilyLookup.parseWeaponToken("spear"))
        assertEquals("kGauntlet", SkillFamilyLookup.parseWeaponToken("kGauntlet"))
    }

    @Test
    fun prefixGroupKeepsUnknownWeaponCodesTogether() {
        assertEquals("WP_SW2", SkillFamilyLookup.prefixGroup("WP_SW2_Slam"))
        assertEquals("WP_FL", SkillFamilyLookup.prefixGroup("WP_FL_Tune"))
        assertEquals("WM_GT", SkillFamilyLookup.prefixGroup("WM_GT_Unstoppable"))
        assertEquals("WP_Item", SkillFamilyLookup.prefixGroup("WP_Item_core"))
        assertTrue(SkillFamilyLookup.isCataloguedPrefix("WP_SW2"))
        assertFalse(SkillFamilyLookup.isCataloguedPrefix("WP_FL"))
        assertTrue(SkillFamilyLookup.isBlockedPrefix("Skill"))
    }
}
