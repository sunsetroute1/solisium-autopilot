package com.solisium.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemGradeHintsTest {
    @Test
    fun tierLabelFromRowId() {
        assertEquals("Tier 5", ItemGradeHints.variantLabel("staff_aa_t5_boss_001"))
        assertEquals("Tier 1", ItemGradeHints.variantLabel("Ammo_kA_t1_001"))
        assertNull(ItemGradeHints.variantLabel("plain_item_id"))
    }

    @Test
    fun inferGradeFromRowIdTokens() {
        assertEquals("EItemGrade::kAAA", ItemGradeHints.inferFromRowId("weapon_aaa_t5_001"))
        assertEquals("EItemGrade::kAA", ItemGradeHints.inferFromRowId("staff_aa_t5_boss_001"))
        assertEquals("EItemGrade::kA", ItemGradeHints.inferFromRowId("ring_a_01"))
        assertNull(ItemGradeHints.inferFromRowId("misc_material_001"))
    }
}
