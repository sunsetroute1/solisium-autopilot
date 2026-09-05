package com.solisium.core.source

import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.truncate

/**
 * Formats warehouse / Questlog trait stat values using the same scaling rules as
 * TL-Helper `formatStat()` (`vendor/tl-helper/web/tl-core.js`).
 */
object TraitDisplayFormat {
    /** In-game roll line, e.g. `Max Mana 150` or `Mana Cost Efficiency +3%`. */
    fun rollLabel(baseName: String, statKey: String, tierValues: List<String>, tier: Int = 1): String {
        val raw = tierValues.getOrNull(tier - 1)?.toRawLong() ?: return baseName.trim()
        val suffix = formatValue(statKey, raw)
        return "${baseName.trim()} $suffix".trim()
    }

    fun formatValue(statKey: String, raw: Long): String {
        val scaled = scale(statKey, raw)
        val key = statKey.lowercase()
        return when {
            key == "attack_speed" || key == "attack_speed_main_hand" || key == "attack_speed_off_hand" ->
                "${trim(scaled)}s"
            key == "attack_range" || key == "attack_range_main_hand" || key == "attack_range_off_hand" ->
                "${trim(scaled)}m"
            key == "shield_block_chance" || key == "block_chance" || key == "shield_block_chance_penetration" ->
                "${trim(scaled)}%"
            key.endsWith("_modifier") || key.contains("duration_modifier") ->
                "${trim(scaled)}%"
            else -> trim(scaled)
        }
    }

    /** Raw warehouse/Questlog number → in-game display magnitude (no unit suffix). */
    fun scale(statKey: String, raw: Long): Double {
        val key = statKey.lowercase()
        val numeric = raw.toDouble()
        return when {
            key == "attack_speed" || key == "attack_speed_main_hand" || key == "attack_speed_off_hand" ->
                numeric / 1000.0
            key == "attack_range" || key == "attack_range_main_hand" || key == "attack_range_off_hand" ->
                numeric / 100.0
            key == "shield_block_chance" || key == "block_chance" || key == "shield_block_chance_penetration" ->
                numeric / 100.0
            key == "cost_regen" || key == "hp_regen" || key == "stamina_regen" ->
                numeric / 1000.0
            key in STATE_CONTEST_STATS ->
                numeric / 40.0
            CONTEST_TENTH_REGEX.containsMatchIn(key) || key == "all_species_damage_amplification" ->
                numeric / 10.0
            key.endsWith("_modifier") || key.contains("duration_modifier") ->
                numeric / 100.0
            else -> numeric
        }
    }

    fun isPercentStat(statKey: String): Boolean {
        val key = statKey.lowercase()
        return key == "shield_block_chance" ||
            key == "block_chance" ||
            key == "shield_block_chance_penetration" ||
            key.endsWith("_modifier") ||
            key.contains("duration_modifier")
    }

    private val STATE_CONTEST_STATS = setOf(
        "all_state_accuracy",
        "all_state_tolerance",
        "bind_accuracy",
        "bind_tolerance",
        "blind_accuracy",
        "blind_tolerance",
        "collide_amplification",
        "collide_resistance",
        "collision_resistance",
        "petrification_accuracy",
        "petrification_tolerance",
        "silence_accuracy",
        "silence_tolerance",
        "sleep_accuracy",
        "sleep_tolerance",
        "stun_accuracy",
        "stun_tolerance",
        "weaken_accuracy",
        "weaken_tolerance",
    )

    private val CONTEST_TENTH_REGEX = Regex(
        """(?:^|_)(?:accuracy|critical_attack|critical_defense|double_attack|double_defense)$""",
    )

    private fun trim(value: Double): String {
        val truncated = truncate((value + EPSILON * sign(value)) * 100.0) / 100.0
        return if (truncated % 1.0 == 0.0) {
            truncated.roundToLong().toString()
        } else {
            truncated.toString().trimEnd('0').trimEnd('.')
        }
    }

    private fun String.toRawLong(): Long? {
        val digits = filter { it.isDigit() || it == '-' }
        if (digits.isEmpty() || digits == "-") return null
        return digits.toLongOrNull()
    }

    private const val EPSILON = 1e-10
}
