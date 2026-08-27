package com.solisium.core.domain

/** One drop line from Questlog (community mirror, not warehouse-extracted). */
data class QuestlogDropEntry(
    val id: String,
    val name: String,
    val dbType: String,
    val category: String?,
    val level: Long?,
    val probability: Double?,
    val quantity: Long?,
    val dropType: String?,
    val dropCondition: String?,
) {
    val probabilityLabel: String? = probability?.let { pct ->
        when {
            pct >= 0.999 -> "100%"
            pct <= 0.0 -> null
            else -> "%.2f%%".format(pct * 100.0)
        }
    }
}

/** NPC / boss detail with a full loot table from Questlog `database.getNpc`. */
data class QuestlogNpcDetail(
    val id: String,
    val name: String,
    val subtitle: String?,
    val level: Long?,
    val category: String?,
    val mapId: Long?,
    val drops: List<QuestlogDropEntry>,
)

/** Resource / chest detail from Questlog `database.getResource`. */
data class QuestlogResourceDetail(
    val id: String,
    val name: String,
    val level: Long?,
    val mapId: Long?,
    val drops: List<QuestlogDropEntry>,
)

/** Warehouse-backed monster reward profile (`TLRewardNpcFoItem`). */
data class MonsterProfile(
    val sourceTable: String,
    val sourceRowId: String,
    val displayName: String,
    val kindHint: String?,
    val levelHint: String?,
    val subtitle: String? = null,
    val level: Long? = null,
    val category: String? = null,
    val mapId: Long? = null,
    val locationLabel: String? = null,
    val dropCount: Int = 0,
    val synced: Boolean = false,
)

/** Cached drop edge stored locally after Questlog sync. */
data class ItemDropSource(
    val itemSourceTable: String,
    val itemSourceRowId: String,
    val itemName: String?,
    val sourceKind: String,
    val sourceId: String,
    val sourceName: String,
    val sourceLevel: Long?,
    val sourceCategory: String?,
    val mapId: Long?,
    val locationLabel: String?,
    val probability: Double?,
    val quantity: Long?,
    val dropType: String?,
    val dropCondition: String?,
    val confidence: String,
    /** Warehouse grade when known; otherwise inferred from row id. */
    val itemGrade: String? = null,
    /** Tier hint parsed from row id (`_t5_` → Tier 5). */
    val variantHint: String? = null,
) {
    val probabilityLabel: String? = probability?.let { pct ->
        when {
            pct >= 0.999 -> "100%"
            pct <= 0.0 -> null
            else -> "%.2f%%".format(pct * 100.0)
        }
    }
}

data class DropCacheStats(
    val monstersTotal: Long,
    val monstersSynced: Long,
    val dropRows: Long,
    val extractedDropRows: Long,
    val lastSyncedAt: String?,
)
