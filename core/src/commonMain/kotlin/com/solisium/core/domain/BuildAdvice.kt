package com.solisium.core.domain

/**
 * One ranked item for a build goal. [score] is the sum of extracted `game_item_stat`
 * raw values for that goal's keys. It is not DPS, not a display stat, and not a
 * modeled total.
 */
data class RankedGear(
    val slot: String,
    val name: String,
    val sourceTable: String,
    val sourceRowId: String,
    val score: Long,
    val grade: String?,
    val kind: String,
    val contributions: List<StatContribution>,
    val communityHits: Int = 0,
    val itemPower: Long? = null,
    val itemPowerEvidence: String? = null,
    val potentialPower: Long? = null,
)

data class StatContribution(
    val statKey: String,
    val rawValue: Long,
    val scope: String,
)

data class SlotAdvice(
    val slot: String,
    val equipped: RankedGear?,
    val recommended: List<RankedGear>,
    val gap: Long?,
)

data class AxisScore(
    val key: String,
    val label: String,
    val yours: Long,
    val recommended: Long,
)

data class SkillShare(
    val name: String,
    val observedDamage: Long,
    val hits: Long,
    val share: Double,
    val catalogName: String?,
    val questlogName: String?,
)

data class CommunityHit(
    val source: String,
    val name: String,
    val detail: String?,
    val url: String?,
    val catalogName: String?,
    val entityId: String? = null,
)

data class CommunitySnapshot(
    val fetchedAt: String,
    val sources: List<String>,
    val patchLabel: String?,
    val items: List<CommunityHit>,
    val skills: List<CommunityHit>,
    val builds: List<CommunityHit> = emptyList(),
    val notes: List<String>,
    val warnings: List<String>,
)

data class BuildAdvice(
    val snapshotId: String,
    val snapshotBuild: String?,
    val goalId: String,
    val goalLabel: String,
    val scoringNote: String,
    val slots: List<SlotAdvice>,
    val axes: List<AxisScore>,
    val skillShares: List<SkillShare>,
    val combatInsights: List<com.solisium.core.query.CombatInsight> = emptyList(),
    val community: CommunitySnapshot?,
    val briefing: List<String>,
    val characterName: String?,
    val className: String? = null,
    val classSource: String? = null,
    val classWeaponsLabel: String? = null,
    val weaponTokens: List<String> = emptyList(),
)
