package com.solisium.core.query

import com.solisium.core.domain.GearWatermarkCategory
import com.solisium.core.domain.GearWatermarkInput
import com.solisium.core.domain.ResolvedCharacterSheet

/**
 * Rough category highs from equipped item levels on a character sheet.
 * These are NOT drop watermarks — only what you wear right now.
 */
object GearWatermarkInferrer {
    private val weaponSlots = setOf(
        "bow", "crossbow", "sword", "sword2h", "dagger", "spear", "gauntlet",
        "staff", "wand", "orb", "weapon", "main", "offhand",
    )
    private val armorSlots = setOf("head", "chest", "hands", "legs", "feet", "cloak")
    private val accessorySlots = setOf(
        "necklace", "earring", "earring2", "ring", "ring2", "bracelet", "belt", "brooch",
    )

    fun fromSheet(sheet: ResolvedCharacterSheet): GearWatermarkInput? {
        val weapon = maxLevel(sheet.sheet.weapons.map { it.slot.lowercase() to it.itemLevel }) { weaponSlots.contains(it) }
            ?: return null
        val equipment = sheet.sheet.equipment.map { it.slot.lowercase() to it.itemLevel }
        val armor = maxLevel(equipment) { armorSlots.contains(it) } ?: return null
        val accessory = maxLevel(equipment) { accessorySlots.contains(it) } ?: return null
        return GearWatermarkInput(
            weapon = GearWatermarkCalculator.clamp(weapon.toInt()),
            armor = GearWatermarkCalculator.clamp(armor.toInt()),
            accessory = GearWatermarkCalculator.clamp(accessory.toInt()),
        )
    }

    fun farmLabel(categories: List<GearWatermarkCategory>): String =
        categories.joinToString(" + ") { it.label }

    private fun maxLevel(
        rows: List<Pair<String, Long?>>,
        slotFilter: (String) -> Boolean,
    ): Long? = rows.filter { slotFilter(it.first) }.mapNotNull { it.second }.maxOrNull()
}
