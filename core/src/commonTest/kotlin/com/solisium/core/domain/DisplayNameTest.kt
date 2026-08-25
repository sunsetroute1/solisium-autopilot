package com.solisium.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayNameTest {
    @Test
    fun aLocalizedStringIsAName() {
        assertEquals("Sparring Longbow", DisplayName.of("Sparring Longbow", "bow_c_t1_nomal_001"))
    }

    @Test
    fun aRowIdStoredAsNameIsNotAName() {
        assertNull(DisplayName.of("bow_c_t1_nomal_001", "bow_c_t1_nomal_001"))
        assertNull(DisplayName.of("None", "x"))
        assertNull(DisplayName.of("  ", "x"))
        assertNull(DisplayName.of(null, "x"))
    }

    @Test
    fun looksTablesAreTheOnesPlayersSee() {
        assertTrue(DisplayName.isItemLooks("TLItemLooks_Equip"))
        assertTrue(DisplayName.isItemLooks("TLItemLooks"))
        assertFalse(DisplayName.isItemLooks("TLItemEquip"))
        assertFalse(DisplayName.isItemLooks("TLItemStats"))
    }

    @Test
    fun enumTokensBecomeShortLabels() {
        assertEquals("AA", DisplayName.prettyEnum("EItemGrade::kAA"))
        assertEquals("Attack · Weapon", DisplayName.fromEnums("ETLRuneType::kAttack", "ETLRuneTargetCategory::kWeapon"))
        assertNull(DisplayName.fromEnums("None", null))
    }
}
