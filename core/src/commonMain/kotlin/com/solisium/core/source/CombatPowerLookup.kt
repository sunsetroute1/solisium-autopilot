package com.solisium.core.source

import com.solisium.core.json.JsonValue

/**
 * Conservative adapter from TL-Helper `combat-power-table.mjs`.
 *
 * Row contents are extracted. Item-to-row matching is derived: there is no
 * foreign key from `TLItemEquip` to `TLItemCombatPower`. Unresolved families
 * stay unresolved rather than guessing A/AA fixed-level rows.
 */
object CombatPowerLookup {
    data class Mapping(
        val rowId: String?,
        val evidence: String,
    )

    data class Components(
        val base: Long,
        val enchant: Long,
        val traits: Long,
        val uniqueTrait: Long,
        val resonance: Long,
        val potential: Long,
    ) {
        val total: Long get() = base + enchant + traits + uniqueTrait + resonance + potential
    }

    fun infer(
        itemId: String?,
        equipCategory: String?,
        itemGrade: String?,
        affectsCategoryLevel: String?,
        levelSelectId: String?,
        minLevel: Long?,
        maxLevel: Long?,
        availableRows: Set<String>? = null,
    ): Mapping {
        val group = groupOf(equipCategory, itemId)
        if (group == null) return Mapping(null, "unsupported-equipment-type")
        fun accept(candidate: String): String? =
            if (availableRows == null || candidate in availableRows) candidate else null

        if (group == "talistone" || group == "gemstone") {
            val grade = gradeToken(itemGrade)
            val rowId = grade?.let { accept("${group}_${it}_t1") }
            return Mapping(rowId, if (rowId != null) "artifact-grade" else "unresolved")
        }

        val seasonalGroup = seasonalGroupOf(group)
        val selector = levelSelectId.orEmpty()
        if (seasonalGroup != null && isTrue(affectsCategoryLevel)) {
            val seasonalGrade = when {
                selector == "ItemGroup_T3" && minLevel == 21L && maxLevel == 50L -> "a"
                selector == "ItemGroup_Nix" && minLevel == 51L && maxLevel == 80L -> "aa"
                else -> null
            }
            if (seasonalGrade != null) {
                val rowId = accept("${seasonalGroup}_${seasonalGrade}_S1")
                if (rowId != null) return Mapping(rowId, "source-level-selector:$selector")
            }
        }

        val id = itemId.orEmpty()
        val seasonal = Regex("_(a|aa)_S1(?:_|$)", RegexOption.IGNORE_CASE).find(id)
        if (seasonal != null && seasonalGroup != null) {
            val rowId = accept("${seasonalGroup}_${seasonal.groupValues[1].lowercase()}_S1")
            return Mapping(rowId, if (rowId != null) "item-id-seasonal" else "unresolved")
        }
        val tier = Regex("_(aaa|aa3|aa2|aa|a|b|c)_(t[12])(?:_|$)", RegexOption.IGNORE_CASE).find(id)
        if (tier != null) {
            val rowId = accept("${group}_${tier.groupValues[1].lowercase()}_${tier.groupValues[2].lowercase()}")
            if (rowId != null) return Mapping(rowId, "item-id-tier")
        }

        val unambiguous = when (EquipCategory.token(itemGrade) ?: itemGrade) {
            "kC" -> "c"
            "kB" -> "b"
            "kAAA" -> "aaa"
            else -> null
        }
        if (unambiguous != null) {
            val prefix = "${group}_${unambiguous}_"
            val candidates = if (availableRows != null) {
                availableRows.filter { it.startsWith(prefix) && Regex("_t\\d+$", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            } else {
                listOf("${prefix}t1")
            }
            if (candidates.size == 1) return Mapping(candidates.single(), "source-unambiguous-grade")
        }
        return Mapping(null, "unresolved")
    }

    /**
     * Seasonal rows use item level as the enchant-list index (arrays longer than 20).
     * Older rows use a short enchant list; [itemLevel] is treated as that index when it fits.
     */
    fun enchantIndex(json: JsonValue, itemLevel: Long?): Int {
        val size = json.arr("ItemEnchantCombatPowerList").size
        if (size <= 0) return 0
        val level = (itemLevel ?: 0L).toInt().coerceAtLeast(0)
        return if (size > 20) {
            level.coerceAtMost(size - 1)
        } else {
            if (level < size) level else 0
        }
    }

    fun maxEnchantIndex(json: JsonValue): Int =
        (json.arr("ItemEnchantCombatPowerList").size - 1).coerceAtLeast(0)

    fun components(
        json: JsonValue,
        enchantIndex: Int = 0,
        traitIndex: Int = 0,
        uniqueIndex: Int = 0,
        resonanceIndex: Int = 0,
        includePotential: Boolean = false,
    ): Components {
        return Components(
            base = json.long("BaseCombatPower") ?: 0L,
            enchant = listPower(json, "ItemEnchantCombatPowerList", enchantIndex) ?: 0L,
            traits = listPower(json, "ItemTraitCombatPowerList", traitIndex) ?: 0L,
            uniqueTrait = listPower(json, "ItemUniqueTraitCombatPowerList", uniqueIndex) ?: 0L,
            resonance = listPower(json, "ItemTraitResonanceCombatPowerList", resonanceIndex) ?: 0L,
            potential = if (includePotential) json.long("ItemPotentialCombatPower") ?: 0L else 0L,
        )
    }

    fun listPower(json: JsonValue, key: String, index: Int): Long? {
        val items = json.arr(key)
        if (index < 0 || index >= items.size) return null
        val item = items[index]
        return item.long("CombatPower") ?: item.long("combat_power") ?: (item as? JsonValue.Num)?.value?.toLong()
    }

    fun groupOf(equipCategory: String?, itemId: String?): String? {
        val token = EquipCategory.token(equipCategory)
        if (token != null) {
            if (token in WEAPONS) return "weapon"
            if (token in ARMOR) return token.removePrefix("k").replaceFirstChar { it.lowercase() }
            ACCESSORY[token]?.let { return it }
        }
        val id = itemId.orEmpty().lowercase()
        if (id.contains("talistone")) return "talistone"
        if (id.contains("gemstone")) return "gemstone"
        return null
    }

    private fun seasonalGroupOf(group: String): String? = when (group) {
        "weapon" -> "weapon"
        in ARMOR_SLOTS -> "armor"
        in ACCESSORY.values -> "accessory"
        else -> null
    }

    private fun gradeToken(itemGrade: String?): String? = when (EquipCategory.token(itemGrade) ?: itemGrade) {
        "kC" -> "c"
        "kB" -> "b"
        "kA" -> "a"
        "kAA" -> "aa"
        "kAA2" -> "aa2"
        "kAA3" -> "aa3"
        "kAAA" -> "aaa"
        else -> null
    }

    private fun isTrue(raw: String?): Boolean =
        raw.equals("EBool::T", ignoreCase = true) || raw.equals("true", ignoreCase = true)

    private val WEAPONS = setOf(
        "kBow", "kSword2h", "kCrossbow", "kStaff", "kDagger", "kSword",
        "kWand", "kSpear", "kOrb", "kGauntlet",
    )
    private val ARMOR = setOf("kHead", "kChest", "kHands", "kLegs", "kFeet", "kCloak")
    private val ARMOR_SLOTS = setOf("head", "chest", "hands", "legs", "feet", "cloak")
    private val ACCESSORY = mapOf(
        "kNecklace" to "necklace",
        "kBracelet" to "bracelet",
        "kBelt" to "belt",
        "kRing" to "ring",
        "kBrooch" to "brooch",
        "kEarring" to "ear",
    )
}
