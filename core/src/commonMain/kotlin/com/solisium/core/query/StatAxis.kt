package com.solisium.core.query

/**
 * Offense / defense methods from the character window. Keys are extracted
 * `game_item_stat.stat_key` names; missing keys on a snapshot are dropped.
 */
enum class StatAxis(
    val id: String,
    val label: String,
    val blurb: String,
    val keys: Set<String>,
) {
    HitChance(
        "hit",
        "Hit chance",
        "Melee / ranged / magic accuracy on gear.",
        setOf("melee_accuracy", "range_accuracy", "magic_accuracy", "all_accuracy", "boss_all_accuracy"),
    ),
    Evasion(
        "evasion",
        "Evasion",
        "Melee / ranged / magic evasion on gear.",
        setOf("melee_evasion", "range_evasion", "magic_evasion", "all_evasion", "boss_all_evasion"),
    ),
    Endurance(
        "endurance",
        "Endurance",
        "Critical defense (the window's Endurance) on gear.",
        setOf(
            "melee_critical_defense",
            "range_critical_defense",
            "magic_critical_defense",
            "all_critical_defense",
        ),
    ),
    CriticalHit(
        "crit",
        "Critical hit",
        "Critical attack chance on gear.",
        setOf(
            "melee_critical_attack",
            "range_critical_attack",
            "magic_critical_attack",
            "all_critical_attack",
        ),
    ),
    HeavyAttack(
        "heavy",
        "Heavy attack",
        "Double-attack (Heavy Attack Chance) on gear.",
        setOf("melee_double_attack", "range_double_attack", "magic_double_attack", "all_double_attack"),
    ),
    HeavyEvasion(
        "heavy-evasion",
        "Heavy attack evasion",
        "Double defense on gear.",
        setOf("melee_double_defense", "range_double_defense", "magic_double_defense", "all_double_defense"),
    ),
    Defense(
        "defense",
        "Defense",
        "Armor and damage reduction.",
        setOf("melee_armor", "range_armor", "magic_armor", "all_armor", "damage_reduction"),
    ),
    Health(
        "health",
        "Health",
        "Max health and regen when the warehouse stores them.",
        setOf("hp_max", "hp_regen", "stamina_max"),
    ),
    Cooldown(
        "cooldown",
        "Cooldown speed",
        "Skill cooldown modifiers.",
        setOf("skill_cooldown_modifier", "global_skill_cooldown_modifier"),
    ),
    AttackPower(
        "attack",
        "Attack power",
        "Main-hand attack power and speed.",
        setOf("attack_power_main_hand", "bonus_attack_power_main_hand", "attack_speed_main_hand"),
    ),
    ;

    fun keysOn(available: Set<String>): Set<String> = keys.filter { it in available }.toSet()

    companion object {
        fun fromIds(raw: String?): List<StatAxis> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(',', ' ', '|')
                .mapNotNull { token -> entries.firstOrNull { it.id.equals(token.trim(), ignoreCase = true) } }
                .distinct()
        }
    }
}
