package com.solisium.core.source

import com.solisium.core.domain.DisplayName

/**
 * Keeps currency, consumables, and misc loot out of the gear catalog browse list.
 * Typed weapon/armor/accessory rows always pass; [TLItemLooks] misc rows are dropped
 * unless they classify as equippable gear.
 */
object GearCatalogFilter {
    private val nonGearCategories = setOf("kAmmo", "kFishingBait", "kFishingRod", "kPotionLeaf")

    private val currencyRowPatterns = listOf(
        "package_gold",
        "dungeon_point",
        "purifying_token",
        "usable_sollant",
        "i_usable_sollant",
        "goldbar",
        "adventure_token",
        "contract_token",
        "lucent",
        "sollant",
        "adena",
        "_token",
        "token_point",
        "luckybag_event",
    )

    private val currencyNamePatterns = listOf(
        "sollant",
        "lucent",
        "token point",
        "contract token",
        "adventure coin",
        "gold bar",
        "dungeon point",
        "purifying token",
    )

    fun isGearListRow(
        sourceTable: String,
        sourceRowId: String,
        name: String?,
        category: String?,
    ): Boolean {
        if (isCurrency(sourceRowId, name)) return false
        val token = EquipCategory.token(category)
        if (token != null && token in nonGearCategories) return false
        if (EquipCategory.kind(category) != null) return true
        if (sourceTable == "TLItemLooks_Equip") return true
        if (DisplayName.isItemLooks(sourceTable)) return false
        return true
    }

    private fun isCurrency(sourceRowId: String, name: String?): Boolean {
        val row = sourceRowId.lowercase()
        if (currencyRowPatterns.any { it in row }) return true
        val label = name?.lowercase().orEmpty()
        return currencyNamePatterns.any { it in label }
    }
}
