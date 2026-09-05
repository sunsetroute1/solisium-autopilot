package com.solisium.core.query

import com.solisium.core.domain.DesiredBuildPlan
import com.solisium.core.domain.ProgressionCadence
import com.solisium.core.domain.ProgressionEase
import com.solisium.core.domain.ResolvedCharacterSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressionAnalyzerTest {
    private fun minimalAdvice() = com.solisium.core.domain.BuildAdvice(
        snapshotId = "snap",
        snapshotBuild = null,
        goalId = "ranged",
        goalLabel = "Ranged DPS",
        scoringNote = "",
        slots = emptyList(),
        axes = emptyList(),
        skillShares = emptyList(),
        community = null,
        briefing = emptyList(),
        characterName = null,
    )

    @Test
    fun dailiesRankAboveCompletedTasks() {
        val plan = ProgressionAnalyzer().analyze(
            sheet = null,
            buildPlan = null,
            buildGoal = BuildGoal.RangedDps,
            completedTaskIds = setOf("daily_contracts"),
        )
        val open = plan.recommendations.filter { !it.completed }
        assertTrue(open.isNotEmpty())
        assertTrue(open.first().cadence == ProgressionCadence.Daily || open.first().priorityScore >= 80)
        assertEquals(1, plan.completedCount)
    }

    @Test
    fun emptySkillCoreLayerBoostsPriority() {
        val buildPlan = DesiredBuildPlan(
            advice = minimalAdvice(),
            currentCombatPower = 9000L,
            desiredCombatPower = null,
            combatPowerGap = null,
            currentGearScore = null,
            desiredGearScore = null,
            gearScoreGap = null,
            axes = emptyList(),
            extraKeys = emptyList(),
            roadmap = emptyList(),
            skillCoverage = com.solisium.core.domain.SkillCoverage(0, 0, emptyList(), ""),
            influences = listOf(
                com.solisium.core.domain.LayerCoverage(
                    layer = "skill_core",
                    label = "Skill cores",
                    slotted = 0,
                    catalogNamed = 40,
                    resolved = 0,
                    note = "Fill cores",
                ),
            ),
            limits = emptyList(),
        )
        val rec = ProgressionAnalyzer().analyze(
            sheet = null,
            buildPlan = buildPlan,
            buildGoal = BuildGoal.RangedDps,
            completedTaskIds = emptySet(),
        ).recommendations.first { it.id == "always_skill_cores" }
        assertEquals(ProgressionEase.Easy, rec.ease)
        assertTrue(rec.reasons.any { it.contains("empty skill-core") })
    }

    @Test
    fun layerGapCreatesFillTask() {
        val buildPlan = minimalPlan(
            influences = listOf(
                com.solisium.core.domain.LayerCoverage(
                    layer = "gemstone",
                    label = "Gemstone skills",
                    slotted = 0,
                    catalogNamed = 10,
                    resolved = 0,
                    note = "Sidebar gems",
                ),
            ),
        )
        val tasks = ProgressionAnalyzer().analyze(
            sheet = null,
            buildPlan = buildPlan,
            buildGoal = BuildGoal.MeleeDps,
            completedTaskIds = emptySet(),
        ).recommendations.filter { it.id == "layer:gemstone" }
        assertEquals(1, tasks.size)
        assertEquals("Fill Gemstone skills", tasks.single().title)
    }

    private fun minimalPlan(
        influences: List<com.solisium.core.domain.LayerCoverage> = emptyList(),
    ): DesiredBuildPlan = DesiredBuildPlan(
        advice = minimalAdvice().copy(goalId = "melee", goalLabel = "Melee DPS"),
        currentCombatPower = null,
        desiredCombatPower = null,
        combatPowerGap = null,
        currentGearScore = null,
        desiredGearScore = null,
        gearScoreGap = null,
        axes = emptyList(),
        extraKeys = emptyList(),
        roadmap = emptyList(),
        skillCoverage = com.solisium.core.domain.SkillCoverage(0, 0, emptyList(), ""),
        influences = influences,
        limits = emptyList(),
    )
}
