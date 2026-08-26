package com.solisium.core.query

import com.solisium.core.domain.GameItemPower
import com.solisium.core.domain.ModeledPowerBreakdown
import com.solisium.core.domain.ModeledSlotPower
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.UserEquipment
import com.solisium.core.domain.UserWeapon
import com.solisium.core.json.JsonParseException
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import com.solisium.core.source.CombatPowerLookup

/**
 * Questlog-shaped CP / gear-score estimate over a character loadout.
 *
 * Item weights come from warehouse `game_combat_power` when `game_item_power`
 * maps the item. Unresolved A/AA families contribute 0 rather than a guessed
 * row. Skill, mastery, and the 250 equipment starting value are community
 * Questlog constants. The live character-window aggregator is not this sum.
 */
class ModeledCombatPower(private val query: CatalogQuery) {
    fun estimate(snapshotId: String, sheet: ResolvedCharacterSheet?): ModeledPowerBreakdown {
        if (sheet == null) return EMPTY
        val links = query.itemPowerByRow(snapshotId)
        val names = query.items(snapshotId).associate { it.sourceRowId to (it.name ?: it.sourceRowId) }
        val slots = equippedSlots(sheet)
        val items = slots.map { slot ->
            val link = links[slot.itemId]
            val weights = warehouseWeights(link, slot.itemLevel)
            if (weights != null) {
                ModeledSlotPower(
                    slot = slot.slot,
                    itemId = slot.itemId,
                    name = names[slot.itemId] ?: slot.name ?: slot.itemId,
                    current = weights.current,
                    potential = weights.potential,
                    source = SOURCE_WAREHOUSE,
                    evidence = link?.evidence,
                )
            } else {
                ModeledSlotPower(
                    slot = slot.slot,
                    itemId = slot.itemId,
                    name = names[slot.itemId] ?: slot.name ?: slot.itemId,
                    current = 0L,
                    potential = 0L,
                    source = SOURCE_UNRESOLVED,
                    evidence = "unresolved",
                )
            }
        }
        val itemPower = items.sumOf { it.current }
        val itemPotential = items.sumOf { it.potential }
        val equipmentBase = QuestlogCombatPower.EQUIPMENT_BASE
        val skillPower = QuestlogCombatPower.skillPower(sheet.sheet.skills)
        val mastery = QuestlogCombatPower.masteryPower(sheet.sheet.weaponMastery)
        val gearScore = equipmentBase + itemPower
        val potentialGearScore = equipmentBase + itemPotential
        return ModeledPowerBreakdown(
            current = gearScore + skillPower + mastery.power,
            potential = potentialGearScore + skillPower + mastery.power,
            gearScore = gearScore,
            potentialGearScore = potentialGearScore,
            equipmentBase = equipmentBase,
            itemPower = itemPower,
            itemPotentialPower = itemPotential,
            skillPower = skillPower,
            masteryPower = mastery.power,
            masteryLevels = mastery.levels,
            items = items,
            unresolvedCount = items.count { it.source == SOURCE_UNRESOLVED },
            note = NOTE,
        )
    }

    companion object {
        const val SOURCE_WAREHOUSE = "warehouse"
        const val SOURCE_UNRESOLVED = "unresolved"

        const val NOTE =
            "Modeled CP follows Questlog's equipment + skills + mastery layout. " +
                "Mapped items use warehouse TLItemCombatPower (derived row map). " +
                "Unresolved A/AA families are omitted. Skill ×2, mastery ×3, and the 250 " +
                "equipment starting value are community heuristics. Not live window CP."

        val EMPTY = ModeledPowerBreakdown(
            current = 0L,
            potential = 0L,
            gearScore = 0L,
            potentialGearScore = 0L,
            equipmentBase = QuestlogCombatPower.EQUIPMENT_BASE,
            itemPower = 0L,
            itemPotentialPower = 0L,
            skillPower = 0L,
            masteryPower = 0L,
            masteryLevels = 0L,
            items = emptyList(),
            unresolvedCount = 0,
            note = NOTE,
        )

        fun warehouseWeights(link: GameItemPower?, itemLevel: Long?): ItemWeights? {
            if (link == null) return null
            val json = parsePayload(link.payload)
            if (json == null) {
                val potential = link.basePower + (link.potentialPower ?: 0L)
                return ItemWeights(current = link.basePower, potential = potential)
            }
            val enchant = CombatPowerLookup.enchantIndex(json, itemLevel)
            val current = CombatPowerLookup.components(json, enchantIndex = enchant, includePotential = false)
            val withPotential = CombatPowerLookup.components(json, enchantIndex = enchant, includePotential = true)
            return ItemWeights(current = current.total, potential = withPotential.total)
        }

        private fun parsePayload(payload: String?): JsonValue? {
            if (payload.isNullOrBlank()) return null
            return try {
                JsonParser.parse(payload)
            } catch (_: JsonParseException) {
                null
            }
        }

        private fun equippedSlots(sheet: ResolvedCharacterSheet): List<EquippedSlot> {
            val weapons = sheet.sheet.weapons.mapNotNull { it.toSlot() }
            val body = sheet.sheet.equipment.mapNotNull { it.toSlot() }
            return weapons + body
        }

        private fun UserWeapon.toSlot(): EquippedSlot? {
            val id = sourceRowId?.trim().orEmpty()
            if (id.isEmpty()) return null
            return EquippedSlot(slot = slot, itemId = id, itemLevel = itemLevel, name = name)
        }

        private fun UserEquipment.toSlot(): EquippedSlot? {
            val id = sourceRowId?.trim().orEmpty()
            if (id.isEmpty()) return null
            return EquippedSlot(slot = slot, itemId = id, itemLevel = itemLevel, name = name)
        }

        data class ItemWeights(val current: Long, val potential: Long)

        private data class EquippedSlot(
            val slot: String,
            val itemId: String,
            val itemLevel: Long?,
            val name: String?,
        )
    }
}
