package com.solisium.core.query

import com.solisium.core.domain.SkillShare
import com.solisium.core.domain.UserSkill
import com.solisium.core.meta.TextNorm

/** Compares observed combat-log damage with the character sheet skill bar. */
data class CombatInsight(
    val skillName: String,
    val observedShare: Double?,
    val severity: InsightSeverity,
    val message: String,
)

enum class InsightSeverity { Info, Warn, Positive }

object CombatBuildFeedback {
    fun analyze(
        skillShares: List<SkillShare>,
        equippedSkills: List<UserSkill>,
    ): List<CombatInsight> {
        if (skillShares.isEmpty()) return emptyList()
        val insights = mutableListOf<CombatInsight>()
        val equippedNames = equippedSkills.mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotEmpty() } }
        val top = skillShares.take(5)

        top.forEach { share ->
            val onBar = equippedNames.any { TextNorm.likelySame(it, share.name) }
            if (!onBar && share.share >= 0.08) {
                insights += CombatInsight(
                    skillName = share.name,
                    observedShare = share.share,
                    severity = InsightSeverity.Warn,
                    message = "Dealt ${pct(share.share)} of your damage but is not on your character sheet — " +
                        "rename mismatch, proc, or pet/summon skill?",
                )
            }
        }

        val observedNames = skillShares.map { it.name }.toSet()
        equippedNames
            .filter { name -> observedNames.none { TextNorm.likelySame(it, name) } }
            .take(4)
            .forEach { name ->
                insights += CombatInsight(
                    skillName = name,
                    observedShare = null,
                    severity = InsightSeverity.Info,
                    message = "On your skill bar but dealt no recorded damage in imported logs — " +
                        "buff, mobility, or not used in those fights.",
                )
            }

        if (top.size >= 2) {
            val dominant = top.first()
            val runnerUp = top[1]
            if (dominant.share >= 0.45 && runnerUp.share <= 0.15) {
                insights += CombatInsight(
                    skillName = dominant.name,
                    observedShare = dominant.share,
                    severity = InsightSeverity.Positive,
                    message = "${dominant.name} carries ${pct(dominant.share)} of observed damage — " +
                        "rotation is heavily skewed toward one skill.",
                )
            }
        }

        return insights.distinctBy { it.skillName + it.message }.take(8)
    }

    private fun pct(share: Double): String = "${(share * 100).toInt()}%"
}
