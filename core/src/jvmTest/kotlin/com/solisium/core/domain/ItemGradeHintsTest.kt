package com.solisium.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemGradeHintsTest {
    @Test
    fun variantLabelFromTierToken() {
        assertEquals("Tier 5", ItemGradeHints.variantLabel("staff_aa_t5_boss_001"))
        assertEquals("Tier 1", ItemGradeHints.variantLabel("Ammo_kA_t1_001"))
        assertNull(ItemGradeHints.variantLabel("plain_item_id"))
    }

    @Test
    fun inferFromRowIdTokens() {
        assertEquals("EItemGrade::kAAA", ItemGradeHints.inferFromRowId("weapon_aaa_t5_001"))
        assertEquals("EItemGrade::kAA", ItemGradeHints.inferFromRowId("staff_aa_t5_boss_001"))
        assertEquals("EItemGrade::kAA", ItemGradeHints.inferFromRowId("bow_aa_t2_raid_001"))
        assertEquals("EItemGrade::kA", ItemGradeHints.inferFromRowId("ring_a_01"))
        assertEquals("EItemGrade::kC", ItemGradeHints.inferFromRowId("bow_c_t1_nomal_001"))
        assertEquals("EItemGrade::kA", ItemGradeHints.inferFromRowId("Ammo_kA_t1_001"))
        assertNull(ItemGradeHints.inferFromRowId("misc_material_001"))
    }

    @Test
    fun normalizeNamedGrades() {
        assertEquals("EItemGrade::kAA", ItemGradeHints.normalizeGrade("Epic"))
        assertEquals("EItemGrade::kAAA", ItemGradeHints.normalizeGrade("Heroic"))
        assertEquals("EItemGrade::kAA", ItemGradeHints.normalizeGrade("EItemGrade::kAA"))
    }

    @Test
    fun resolvePrefersExplicitThenRowId() {
        assertEquals("EItemGrade::kAA", ItemGradeHints.resolve("Epic", "bow_c_t1_nomal_001"))
        assertEquals("EItemGrade::kC", ItemGradeHints.resolve(null, "bow_c_t1_nomal_001"))
    }
}
