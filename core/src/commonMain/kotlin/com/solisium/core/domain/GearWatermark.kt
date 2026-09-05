package com.solisium.core.domain

/** Highest-ever dropped item level per category (Aragon & Kninebeat / MetaForge cadence). */
data class GearWatermarkInput(
    val weapon: Int,
    val armor: Int,
    val accessory: Int,
)

enum class GearWatermarkCategory(val id: String, val label: String) {
    WEAPON("weapon", "Weapon"),
    ARMOR("armor", "Armor"),
    ACCESSORY("accessory", "Accessory"),
    ;
}

data class WatermarkDropChance(
    /** Offset from watermark: -3 .. +1 */
    val delta: Int,
    val gearLevel: Int,
    val percent: Double,
)

/**
 * Drop-watermark plan from category highs. Not the typed character-window gear score
 * and not modeled equipment CP — this is NIX item-level drop progression.
 */
data class GearWatermarkPlan(
    val input: GearWatermarkInput,
    val average: Double,
    val watermark: Int,
    /** True when fractional average exceeds floor — game may round up for drops. */
    val mayRoundUp: Boolean,
    val dropChances: List<WatermarkDropChance>,
    /** Sum of positive-delta drop chances (chance next drop raises watermark). */
    val upgradeChancePercent: Double,
    val farmCategories: List<GearWatermarkCategory>,
    val expectedDropsToUpgrade: Double?,
    val expectedDropsPerCategoryTo80: Double,
    val expectedTotalDropsTo80: Double,
    val atCap: Boolean,
    val notes: List<String>,
)
