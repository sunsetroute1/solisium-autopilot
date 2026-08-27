package com.solisium.core.query

import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.CombatSkillTotal
import com.solisium.core.domain.CombatTargetTotal
import com.solisium.core.domain.CombatTrend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombatAnalyzerTest {
    private fun session(
        id: String,
        dps: Double?,
        damage: Long,
        skills: List<CombatSkillTotal> = emptyList(),
        target: String? = "Practice Dummy",
        critRate: Double? = null,
    ) = CombatSessionSummary(
        sessionId = id,
        eventCount = 10,
        observedDamageSum = damage,
        logVersion = "4",
        startedAt = "2026-01-01T00:00:00",
        endedAt = "2026-01-01T00:01:00",
        observedDps = dps,
        skillTotals = skills,
        damageDoneHits = skills.sumOf { it.hits },
        primaryTarget = target,
        targets = target?.let {
            listOf(CombatTargetTotal(it, damage, skills.sumOf { s -> s.hits }, 1.0))
        } ?: emptyList(),
        critRate = critRate,
        durationSeconds = 60.0,
    )

    @Test
    fun portfolioAggregatesDpsAndCrit() {
        val skills = listOf(
            CombatSkillTotal("Strike", "1", 800, 8, critHits = 2, heavyHits = 1, damageShare = 0.8),
            CombatSkillTotal("Bolt", "2", 200, 4, critHits = 0, heavyHits = 0, damageShare = 0.2),
        )
        val sessions = listOf(
            session("a", 1000.0, 1000, skills, critRate = 0.25),
            session("b", 800.0, 800, skills, critRate = 0.20),
        )
        val portfolio = CombatAnalyzer.portfolio(sessions)
        assertEquals(2, portfolio.sessionCount)
        assertEquals(1800, portfolio.totalDamage)
        assertEquals(900.0, portfolio.avgDps)
        assertEquals(1000.0, portfolio.bestDps)
        assertEquals("a", portfolio.bestSessionId)
        assertEquals("Strike", portfolio.topSkillName)
        assertTrue(portfolio.overallCritRate!! > 0.15)
    }

    @Test
    fun compareReportsDpsDeltaAndSkillShifts() {
        val baselineSkills = listOf(
            CombatSkillTotal("Strike", "1", 700, 7, damageShare = 0.7),
            CombatSkillTotal("Bolt", "2", 300, 3, damageShare = 0.3),
        )
        val currentSkills = listOf(
            CombatSkillTotal("Strike", "1", 500, 5, damageShare = 0.5),
            CombatSkillTotal("Bolt", "2", 500, 5, damageShare = 0.5),
        )
        val baseline = session("old", 1000.0, 1000, baselineSkills)
        val current = session("new", 1100.0, 1100, currentSkills)
        val compare = CombatAnalyzer.compare(baseline, current)
        assertEquals(100.0, compare.dpsDelta)
        assertTrue(compare.dpsDeltaPct!! > 0.05)
        assertTrue(compare.skillShareShifts.any { it.skillName == "Bolt" && it.deltaPp > 0 })
        assertTrue(compare.headline.contains("up"))
    }

    @Test
    fun sessionHighlightsCallOutCritAndPortfolioGap() {
        val skills = listOf(
            CombatSkillTotal("Spike", "1", 900, 9, critHits = 4, heavyHits = 0, damageShare = 0.9),
        )
        val s = session("x", 500.0, 1000, skills, critRate = 0.4)
        val portfolio = CombatAnalyzer.portfolio(listOf(s, session("y", 700.0, 700, skills)))
        val highlights = CombatAnalyzer.sessionHighlights(s, portfolio)
        assertTrue(highlights.any { it.contains("Practice Dummy") })
        assertTrue(highlights.any { it.contains("Crit") || it.contains("crit") })
    }

    @Test
    fun emptyPortfolioIsUnknownTrend() {
        val p = CombatAnalyzer.portfolio(emptyList())
        assertEquals(CombatTrend.Unknown, p.dpsTrend)
        assertEquals(0, p.sessionCount)
    }
}
