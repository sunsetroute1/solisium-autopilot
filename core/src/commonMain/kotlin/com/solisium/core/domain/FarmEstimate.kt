package com.solisium.core.domain

/** Rough farming math from cached drop rates (not a guarantee). */
data class FarmEstimate(
    val sourceName: String,
    val probability: Double,
    val expectedKills: Long,
    val estimatedMinutes: Double?,
    val confidence: String,
    val note: String,
)

object FarmEstimator {
    fun estimate(
        sources: List<ItemDropSource>,
        observedDps: Double? = null,
        targetHp: Long? = null,
    ): FarmEstimate? {
        val best = sources
            .filter { (it.probability ?: 0.0) > 0.0 }
            .maxByOrNull { it.probability ?: 0.0 }
            ?: return null
        val p = best.probability ?: return null
        val kills = kotlin.math.ceil(1.0 / p).toLong().coerceAtLeast(1L)
        val minutes = when {
            observedDps != null && observedDps > 0 && targetHp != null && targetHp > 0 ->
                (kills * targetHp / observedDps) / 60.0
            else -> null
        }
        val note = buildString {
            append("~$kills kills at ${best.probabilityLabel ?: fmt(p)} per kill")
            if (best.variantHint != null) append(" (${best.variantHint})")
            if (best.dropCondition != null) append(" · ${DropLabels.conditionLabel(best.dropCondition)}")
            minutes?.let { append(" · ~${it.toInt()} min at your observed DPS") }
        }
        return FarmEstimate(
            sourceName = best.sourceName,
            probability = p,
            expectedKills = kills,
            estimatedMinutes = minutes,
            confidence = best.confidence,
            note = note,
        )
    }

    private fun fmt(p: Double): String = "%.2f%%".format(p * 100.0)
}
