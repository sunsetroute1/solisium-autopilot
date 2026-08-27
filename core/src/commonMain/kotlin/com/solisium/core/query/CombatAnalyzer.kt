package com.solisium.core.query

import com.solisium.core.domain.CombatPortfolio
import com.solisium.core.domain.CombatSessionCompare
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.CombatSkillShareShift
import com.solisium.core.domain.CombatSkillTotal
import com.solisium.core.domain.CombatTargetTotal
import com.solisium.core.domain.CombatTrend
import kotlin.math.abs

/** Deterministic combat-log analytics — observed damage only, no modeled formulas. */
object CombatAnalyzer {
    fun portfolio(sessions: List<CombatSessionSummary>): CombatPortfolio {
        if (sessions.isEmpty()) {
            return CombatPortfolio(
                sessionCount = 0,
                totalDamage = 0,
                avgDps = null,
                bestDps = null,
                bestSessionId = null,
                overallCritRate = null,
                overallHeavyRate = null,
                topSkillName = null,
                topSkillShare = null,
                dpsTrend = CombatTrend.Unknown,
            )
        }

        val totalDamage = sessions.sumOf { it.observedDamageSum }
        val dpsValues = sessions.mapNotNull { it.observedDps }
        val avgDps = if (dpsValues.isNotEmpty()) dpsValues.average() else null
        val best = sessions.maxByOrNull { it.observedDps ?: 0.0 }
        val totalHits = sessions.sumOf { it.damageDoneHits }.coerceAtLeast(1L)
        val totalCrits = sessions.flatMap { it.skillTotals }.sumOf { it.critHits }
        val totalHeavy = sessions.flatMap { it.skillTotals }.sumOf { it.heavyHits }

        val skillAgg = sessions.flatMap { it.skillTotals }
            .groupBy { it.skillName ?: it.skillId ?: "unnamed" }
            .mapValues { (_, rows) -> rows.sumOf { it.observedDamageSum } }
        val topSkill = skillAgg.maxByOrNull { it.value }

        val trend = dpsTrend(sessions)
        val insights = buildList {
            best?.observedDps?.let { dps ->
                add("Best pull: ${fmtDps(dps)} on ${label(best)}.")
            }
            topSkill?.let { (name, dmg) ->
                val share = dmg.toDouble() / totalDamage.coerceAtLeast(1)
                if (share >= 0.35) {
                    add("$name accounts for ${pct(share)} of all logged damage — rotation is skill-heavy.")
                }
            }
            rotationSwing(sessions)?.let { add(it) }
            val crit = totalCrits.toDouble() / totalHits
            if (crit >= 0.25) add("Overall crit rate ${pct(crit)} across imported fights.")
        }

        return CombatPortfolio(
            sessionCount = sessions.size,
            totalDamage = totalDamage,
            avgDps = avgDps,
            bestDps = best?.observedDps,
            bestSessionId = best?.sessionId,
            overallCritRate = totalCrits.toDouble() / totalHits,
            overallHeavyRate = totalHeavy.toDouble() / totalHits,
            topSkillName = topSkill?.key,
            topSkillShare = topSkill?.let { it.value.toDouble() / totalDamage.coerceAtLeast(1) },
            dpsTrend = trend,
            insights = insights,
        )
    }

    fun compare(baseline: CombatSessionSummary, current: CombatSessionSummary): CombatSessionCompare {
        val baseDps = baseline.observedDps
        val curDps = current.observedDps
        val dpsDelta = if (baseDps != null && curDps != null) curDps - baseDps else null
        val dpsDeltaPct = if (baseDps != null && curDps != null && baseDps > 0) {
            (curDps - baseDps) / baseDps
        } else {
            null
        }
        val shifts = skillShareShifts(baseline.skillTotals, current.skillTotals)
        val headline = when {
            dpsDeltaPct == null -> "Compared ${label(current)} to ${label(baseline)}."
            dpsDeltaPct >= 0.05 -> "DPS up ${pct(dpsDeltaPct)} vs prior session."
            dpsDeltaPct <= -0.05 -> "DPS down ${pct(-dpsDeltaPct)} vs prior session."
            else -> "DPS roughly flat vs prior session."
        }
        return CombatSessionCompare(
            baselineLabel = label(baseline),
            currentLabel = label(current),
            dpsDelta = dpsDelta,
            dpsDeltaPct = dpsDeltaPct,
            damageDelta = current.observedDamageSum - baseline.observedDamageSum,
            skillShareShifts = shifts,
            headline = headline,
        )
    }

    fun sessionHighlights(session: CombatSessionSummary, portfolio: CombatPortfolio?): List<String> {
        if (session.skillTotals.isEmpty()) return emptyList()
        return buildList {
            session.primaryTarget?.let { add("Primary target: $it.") }
            session.durationSeconds?.let { add("Fight length: ${fmtSeconds(it)}.") }
            session.critRate?.takeIf { it >= 0.2 }?.let { add("Crit rate ${pct(it)} this session.") }
            val top = session.skillTotals.maxByOrNull { it.observedDamageSum }
            top?.critRate?.takeIf { it >= 0.35 && top.damageShare >= 0.15 }?.let {
                add("${top.skillName} crit ${pct(it)} of its hits — spike skill.")
            }
            top?.heavyRate?.takeIf { it >= 0.25 && top.damageShare >= 0.10 }?.let {
                add("${top.skillName} heavy ${pct(it)} of its hits.")
            }
            portfolio?.avgDps?.let { avg ->
                session.observedDps?.let { dps ->
                    val delta = (dps - avg) / avg
                    when {
                        delta >= 0.08 -> add("${pct(delta)} above your imported average DPS.")
                        delta <= -0.08 -> add("${pct(-delta)} below your imported average DPS.")
                    }
                }
            }
            val idleSkills = session.skillTotals.filter { it.hits <= 2 && it.observedDamageSum < session.observedDamageSum / 20 }
            if (idleSkills.isNotEmpty()) {
                val names = idleSkills.take(2).mapNotNull { it.skillName }.joinToString(", ")
                add("Low contribution: $names — filler, missed casts, or off-bar proc.")
            }
        }.take(5)
    }

    private fun dpsTrend(sessions: List<CombatSessionSummary>): CombatTrend {
        val recent = sessions.take(3).mapNotNull { it.observedDps }
        if (recent.size < 2) return CombatTrend.Unknown
        val newest = recent.first()
        val older = recent.drop(1).average()
        if (older <= 0) return CombatTrend.Unknown
        val delta = (newest - older) / older
        return when {
            delta >= 0.06 -> CombatTrend.Up
            delta <= -0.06 -> CombatTrend.Down
            else -> CombatTrend.Flat
        }
    }

    private fun rotationSwing(sessions: List<CombatSessionSummary>): String? {
        if (sessions.size < 2) return null
        val topNames = sessions.map { s ->
            s.skillTotals.maxByOrNull { it.observedDamageSum }?.skillName ?: return@map null
        }
        val distinct = topNames.filterNotNull().distinct()
        if (distinct.size >= 2 && sessions.size >= 2) {
            return "Top damage skill varies across sessions (${distinct.take(3).joinToString(", ")}) — rotation or build may be shifting."
        }
        return null
    }

    private fun skillShareShifts(
        baseline: List<CombatSkillTotal>,
        current: List<CombatSkillTotal>,
    ): List<CombatSkillShareShift> {
        val baseTotal = baseline.sumOf { it.observedDamageSum }.coerceAtLeast(1L)
        val curTotal = current.sumOf { it.observedDamageSum }.coerceAtLeast(1L)
        val names = (baseline.mapNotNull { it.skillName } + current.mapNotNull { it.skillName }).distinct()
        return names.mapNotNull { name ->
            val b = baseline.filter { it.skillName == name }.sumOf { it.observedDamageSum }
            val c = current.filter { it.skillName == name }.sumOf { it.observedDamageSum }
            val bShare = b.toDouble() / baseTotal
            val cShare = c.toDouble() / curTotal
            val delta = cShare - bShare
            if (abs(delta) < 0.03) return@mapNotNull null
            CombatSkillShareShift(name, bShare, cShare, delta)
        }.sortedByDescending { abs(it.deltaPp) }.take(6)
    }

    fun label(session: CombatSessionSummary): String =
        session.primaryTarget ?: session.startedAt?.substringBefore("T") ?: "session"

    private fun pct(v: Double): String = "${(v * 100).toInt()}%"

    private fun fmtDps(v: Double): String = "%.0f".format(v)

    private fun fmtSeconds(v: Double): String = when {
        v >= 120 -> "%.1f min".format(v / 60)
        else -> "%.0f s".format(v)
    }
}
