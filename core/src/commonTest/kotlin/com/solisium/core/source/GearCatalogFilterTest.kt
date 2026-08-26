package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GearCatalogFilterTest {
    @Test
    fun excludesCurrencyPackages() {
        assertFalse(
            GearCatalogFilter.isGearListRow(
                "TLItemLooks",
                "package_gold_1000qty",
                "Gold Package",
                null,
            ),
        )
        assertFalse(
            GearCatalogFilter.isGearListRow(
                "TLItemLooks",
                "I_Usable_Sollant_Box_001",
                "Bound Sollant Chest (10,000)",
                null,
            ),
        )
    }

    @Test
    fun keepsTypedWeaponsAndLooksEquip() {
        assertTrue(
            GearCatalogFilter.isGearListRow(
                "TLItemEquip",
                "bow_a_t1_normal_001",
                "Sparring Longbow",
                "EItemCategory::kBow",
            ),
        )
        assertTrue(
            GearCatalogFilter.isGearListRow(
                "TLItemLooks_Equip",
                "bow_aa_t2_raid_001",
                "Calanthia's Loom of Entropy",
                "EItemCategory::kBow",
            ),
        )
    }

    @Test
    fun dropsMiscLooksWithoutEquipCategory() {
        assertFalse(
            GearCatalogFilter.isGearListRow(
                "TLItemLooks",
                "dungeon_point_stone_001",
                "Dungeon Point Stone",
                null,
            ),
        )
    }

    @Test
    fun dropsAmmoAndBait() {
        assertFalse(
            GearCatalogFilter.isGearListRow(
                "TLItemLooks",
                "Ammo_kA_001",
                "Basic Arrows",
                "EItemCategory::kAmmo",
            ),
        )
    }
}
