package com.solisium.core.domain

/**
 * Warehouse-backed detail for one catalog row. Community overlays are kept separate
 * so Questlog numbers never masquerade as extracted client stats.
 */
data class CatalogItemDetail(
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val grade: String?,
    val meta: String?,
    val category: String?,
    val warehouseStats: List<GameItemStat>,
    val curves: List<GameItemCurve>,
    val curvePoints: List<GameCurvePoint>,
    val combatPower: GameItemPower?,
)

/** Parsed Questlog `database.getItem` payload — community, not warehouse truth. */
data class QuestlogItemOverlay(
    val description: String?,
    val requiredLevel: Long?,
    val sellPrice: Long?,
    val tradeCategory: String?,
    val properties: List<String>,
    val statLines: List<CommunityStatLine>,
    val traitLines: List<CommunityTraitLine>,
    val perkSummaries: List<String>,
    val droppedFromNpcs: List<QuestlogDropEntry> = emptyList(),
    val containerSources: List<QuestlogDropEntry> = emptyList(),
    /** @deprecated use [containerSources] names; kept for older UI paths */
    val dropSources: List<String> = containerSources.map { it.name }.distinct(),
)

data class CommunityStatLine(
    val group: String,
    val label: String,
    val value: String,
)

data class CommunityTraitLine(
    val label: String,
    val tiers: String,
)
