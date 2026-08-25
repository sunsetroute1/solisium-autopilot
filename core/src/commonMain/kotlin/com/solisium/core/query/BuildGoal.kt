package com.solisium.core.query

import com.solisium.core.source.EquipCategory

/**
 * Player-facing build intent. Weapon filters use `TLItemEquip` tokens; stat keys are
 * the extracted `game_item_stat.stat_key` values observed on live warehouses. Extra
 * keys are included only when they already exist on the snapshot.
 */
enum class BuildGoal(
    val id: String,
    val label: String,
    val blurb: String,
    val weaponTokens: Set<String>,
    val statKeys: Set<String>,
    val keyHints: Set<String>,
    val questlogQueries: List<String>,
    val skillCategories: Set<String>,
) {
    RangedDps(
        id = "ranged",
        label = "Ranged DPS",
        blurb = "Bows and crossbows. Ranked by extracted attack power, speed, and range.",
        weaponTokens = setOf("kBow", "kCrossbow"),
        statKeys = setOf(
            "attack_power_main_hand",
            "bonus_attack_power_main_hand",
            "attack_speed_main_hand",
            "attack_range_main_hand",
        ),
        keyHints = setOf("attack_range"),
        questlogQueries = listOf("longbow", "crossbow", "bow"),
        skillCategories = setOf("bow", "crossbow"),
    ),
    MeleeDps(
        id = "melee",
        label = "Melee DPS",
        blurb = "Swords, daggers, spears, gauntlets. Ranked by extracted attack power and speed.",
        weaponTokens = setOf("kSword", "kSword2h", "kDagger", "kSpear", "kGauntlet"),
        statKeys = setOf(
            "attack_power_main_hand",
            "bonus_attack_power_main_hand",
            "attack_speed_main_hand",
        ),
        keyHints = emptySet(),
        questlogQueries = listOf("greatsword", "dagger", "spear", "sword", "gauntlet"),
        skillCategories = setOf("sword", "sword2h", "dagger", "spear", "gauntlet"),
    ),
    MagicDps(
        id = "magic",
        label = "Magic DPS",
        blurb = "Staff, wand, orb. Ranked by extracted attack power plus any magic keys on the snapshot.",
        weaponTokens = setOf("kStaff", "kWand", "kOrb"),
        statKeys = setOf(
            "attack_power_main_hand",
            "bonus_attack_power_main_hand",
            "attack_speed_main_hand",
        ),
        keyHints = setOf("spell"),
        questlogQueries = listOf("staff", "wand", "orb"),
        skillCategories = setOf("staff", "wand", "orb"),
    ),
    Tank(
        id = "tank",
        label = "Tank",
        blurb = "Armor and block. Ranked by extracted armor, damage reduction, and block — not HP, which this warehouse barely stores.",
        weaponTokens = setOf("kSword", "kSword2h", "kSpear", "kGauntlet", "kStaff"),
        statKeys = setOf(
            "range_armor",
            "melee_armor",
            "magic_armor",
            "damage_reduction",
        ),
        keyHints = setOf("armor", "block", "reduction", "hp"),
        questlogQueries = listOf("guardian", "plate", "greatsword", "spear"),
        skillCategories = setOf("sword", "sword2h", "spear", "gauntlet"),
    ),
    Support(
        id = "support",
        label = "Support",
        blurb = "Wand and staff utility. Ranked by extracted speed plus cooldown/heal/mana keys when present.",
        weaponTokens = setOf("kWand", "kStaff", "kOrb"),
        statKeys = setOf("attack_speed_main_hand"),
        keyHints = setOf("cooldown", "heal", "mana", "skill"),
        questlogQueries = listOf("wand", "staff", "healing"),
        skillCategories = setOf("wand", "staff", "orb"),
    ),
    ;

    fun keysOn(available: Set<String>): Set<String> {
        val hinted = available.filter { key ->
            keyHints.any { hint -> key.contains(hint, ignoreCase = true) }
        }
        return statKeys + hinted
    }

    fun acceptsWeapon(token: String?): Boolean {
        val normalized = EquipCategory.token(token) ?: return false
        return normalized in weaponTokens
    }

    companion object {
        fun fromId(raw: String?): BuildGoal =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: RangedDps
    }
}
