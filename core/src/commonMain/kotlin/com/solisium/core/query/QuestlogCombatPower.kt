package com.solisium.core.query

import com.solisium.core.domain.UserSkill
import com.solisium.core.domain.UserWeaponMastery

/**
 * Questlog character-builder combat-power constants captured in TL-Helper
 * `tl-questlog-rules.js`. These are fitted community heuristics, not extracted
 * client aggregation. Skill and mastery terms have no warehouse table.
 */
object QuestlogCombatPower {
    const val EQUIPMENT_BASE = 250L
    const val SKILL_PER_LEVEL = 2L
    const val MASTERY_PER_LEVEL = 3L
    const val MASTERY_THRESHOLD_BONUS = 20L
    val MASTERY_THRESHOLDS = listOf(130L, 260L, 390L, 520L)

    fun skillPower(skills: List<UserSkill>): Long =
        skills.sumOf { (it.skillLevel ?: 0L).coerceAtLeast(0L) } * SKILL_PER_LEVEL

    fun masteryPower(mastery: List<UserWeaponMastery>): MasteryTerm {
        val levels = mastery.sumOf { (it.level ?: 0L).coerceAtLeast(0L) }
        val thresholds = MASTERY_THRESHOLDS.count { levels >= it }.toLong() * MASTERY_THRESHOLD_BONUS
        return MasteryTerm(levels = levels, power = levels * MASTERY_PER_LEVEL + thresholds)
    }

    data class MasteryTerm(val levels: Long, val power: Long)
}
