package com.solisium.core.query

import com.solisium.core.domain.LiveProgressionItem
import com.solisium.core.domain.LiveProgressionSnapshot
import com.solisium.core.domain.LiveProgressionSource
import com.solisium.core.domain.ProgressionCadence
import com.solisium.core.domain.ProgressionDifficulty
import com.solisium.core.domain.ProgressionEase
import com.solisium.core.domain.ProgressionRecommendation

object ProgressionSync {
    fun mergedCompleted(manual: Set<String>, live: LiveProgressionSnapshot?): Set<String> =
        manual + live?.completedIds.orEmpty()

    fun liveRecommendations(live: LiveProgressionSnapshot?): List<ProgressionRecommendation> {
        if (live == null || live.items.isEmpty()) return emptyList()
        return live.items.map { item -> item.toRecommendation() }
    }

    private fun LiveProgressionItem.toRecommendation(): ProgressionRecommendation {
        val cadence = when {
            id.startsWith("live:") -> ProgressionCadence.Always
            else -> ProgressionCatalog.byId(id)?.cadence ?: ProgressionCadence.Always
        }
        val template = ProgressionCatalog.byId(id)
        val ease = when (source) {
            LiveProgressionSource.Paste ->
                if (completed) ProgressionEase.Easy else ProgressionEase.Moderate
            LiveProgressionSource.LocalConfig -> ProgressionEase.Moderate
            LiveProgressionSource.CombatLog -> ProgressionEase.Moderate
        }
        return ProgressionRecommendation(
            id = id,
            title = template?.title ?: title,
            detail = listOfNotNull(detail, progress?.let { "Progress $it" }).joinToString(" "),
            cadence = cadence,
            category = template?.category ?: source.label,
            ease = ease,
            difficulty = template?.difficulty ?: ProgressionDifficulty.Standard,
            progressionValue = template?.progressionValue ?: 70,
            priorityScore = (template?.progressionValue ?: 70) + if (completed) 0 else 10,
            completed = completed,
            source = source.name.lowercase(),
            reasons = listOf("Synced from ${source.label}."),
        )
    }
}
