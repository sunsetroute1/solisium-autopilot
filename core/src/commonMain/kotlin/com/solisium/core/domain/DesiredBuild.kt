package com.solisium.core.domain

/**
 * Extracted `TLItemCombatPower` row. [basePower] is a component weight, not live
 * character combat power.
 */
data class GameCombatPower(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val category: String?,
    val basePower: Long,
    val potentialPower: Long?,
    val payload: String?,
    val confidence: String,
)

/**
 * Derived item-id → combat-power-row link. [confidence] is `derived`.
 */
data class GameItemPower(
    val snapshotId: String,
    val itemSourceTable: String,
    val itemSourceRowId: String,
    val powerSourceRowId: String,
    val evidence: String,
    val confidence: String,
    val basePower: Long,
    val potentialPower: Long?,
    val payload: String? = null,
)

data class DesiredBuildPlan(
    val advice: BuildAdvice,
    val currentCombatPower: Long?,
    val desiredCombatPower: Long?,
    val combatPowerGap: Long?,
    val currentGearScore: Long?,
    val desiredGearScore: Long?,
    val gearScoreGap: Long?,
    val modeled: ModeledPowerBreakdown? = null,
    val modeledCombatPowerGap: Long? = null,
    val modeledGearScoreGap: Long? = null,
    val axes: List<String>,
    val extraKeys: List<String>,
    val roadmap: List<RoadmapStep>,
    val skillCoverage: SkillCoverage,
    val influences: List<LayerCoverage> = emptyList(),
    val selectedClass: BuildClassOption? = null,
    val characterClass: WeaponClassMatch? = null,
    val limits: List<String>,
)

/**
 * Questlog-shaped combat-power / gear-score estimate. Item numbers come from
 * warehouse `TLItemCombatPower` when mapped; skill, mastery, and the 250
 * equipment starting value are community constants. Not live window CP.
 */
data class ModeledPowerBreakdown(
    val current: Long,
    val potential: Long,
    val gearScore: Long,
    val potentialGearScore: Long,
    val equipmentBase: Long,
    val itemPower: Long,
    val itemPotentialPower: Long,
    val skillPower: Long,
    val masteryPower: Long,
    val masteryLevels: Long,
    val items: List<ModeledSlotPower>,
    val unresolvedCount: Int,
    val note: String,
)

data class ModeledSlotPower(
    val slot: String,
    val itemId: String,
    val name: String,
    val current: Long,
    val potential: Long,
    val source: String,
    val evidence: String?,
)

data class RoadmapStep(
    val kind: String,
    val title: String,
    val detail: String,
    val statGap: Long? = null,
    val itemPowerGap: Long? = null,
)

data class SkillCoverage(
    val catalogRelevant: Int,
    val slotted: Int,
    val missingNames: List<String>,
    val note: String,
)
