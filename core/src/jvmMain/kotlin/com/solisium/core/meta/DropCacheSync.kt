package com.solisium.core.meta

import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.QuestlogDropEntry
import com.solisium.core.source.MonsterLocationHints
import java.time.Instant

data class DropSyncProgress(
    val phase: String,
    val current: Int,
    val total: Int,
    val lastMonster: String? = null,
) {
    val fraction: Float? = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else null
}

data class DropSyncResult(
    val monstersSynced: Int,
    val dropRows: Int,
    val failures: Int,
    val warnings: List<String>,
)

/**
 * One-time (or repeat) pull of Questlog npc loot tables into [game_item_drop] for offline search.
 * Requires network; never runs on import unless the user asks.
 */
class DropCacheSync(
    private val client: CommunityMetaClient = CommunityMetaClient(),
    private val pauseMs: Long = DEFAULT_PAUSE_MS,
    private val clock: () -> String = { Instant.now().toString() },
) {
    suspend fun sync(
        db: SolisiumDatabase,
        snapshotId: String,
        monsterIds: List<String>,
        onProgress: (DropSyncProgress) -> Unit = {},
    ): DropSyncResult {
        val ids = monsterIds.distinct()
        if (ids.isEmpty()) {
            return DropSyncResult(0, 0, 0, listOf("No monster reward profiles in the active warehouse snapshot."))
        }
        val warnings = mutableListOf<String>()
        var failures = 0
        var dropRows = 0
        var synced = 0
        val syncedAt = clock()
        db.transaction {
            db.schemaQueries.clearCommunityItemDrops(snapshotId)
        }
        onProgress(DropSyncProgress("Fetching loot tables", 0, ids.size))
        ids.forEachIndexed { index, monsterId ->
            onProgress(DropSyncProgress("Fetching loot tables", index, ids.size, monsterId))
            val npc = runCatching { client.fetchNpc(monsterId) }.getOrNull()
            if (npc == null) {
                failures++
                if (failures <= 5) warnings += "No Questlog npc data for $monsterId"
                Thread.sleep(pauseMs)
                return@forEachIndexed
            }
            val location = MonsterLocationHints.label(monsterId, npc.mapId, npc.category)
            db.transaction {
                db.schemaQueries.updateGameBossCache(
                    name = npc.name,
                    subtitle = npc.subtitle,
                    level = npc.level,
                    category = npc.category,
                    map_id = npc.mapId,
                    location_label = location,
                    sync_source = "questlog",
                    synced_at = syncedAt,
                    snapshot_id = snapshotId,
                    source_table = "TLRewardNpcFoItem",
                    source_row_id = monsterId,
                )
                npc.drops.forEach { drop ->
                    insertDrop(db, snapshotId, monsterId, npc.name, npc.level, npc.category, npc.mapId, location, drop, syncedAt)
                    dropRows++
                }
            }
            synced++
            Thread.sleep(pauseMs)
        }
        onProgress(DropSyncProgress("Done", ids.size, ids.size))
        if (failures > 5) warnings += "$failures monsters had no Questlog loot table (first 5 listed above)."
        return DropSyncResult(synced, dropRows, failures, warnings)
    }

    private fun insertDrop(
        db: SolisiumDatabase,
        snapshotId: String,
        sourceId: String,
        sourceName: String,
        sourceLevel: Long?,
        sourceCategory: String?,
        mapId: Long?,
        locationLabel: String,
        drop: QuestlogDropEntry,
        syncedAt: String,
    ) {
        db.schemaQueries.insertItemDrop(
            snapshot_id = snapshotId,
            item_source_table = "TLItemLooks",
            item_source_row_id = drop.id,
            item_name = drop.name,
            source_kind = drop.dbType.ifBlank { "npc" },
            source_id = sourceId,
            source_name = sourceName,
            source_level = sourceLevel,
            source_category = sourceCategory,
            map_id = mapId,
            location_label = locationLabel,
            probability = drop.probability,
            quantity = drop.quantity,
            drop_type = drop.dropType,
            drop_condition = drop.dropCondition,
            confidence = "community",
            synced_at = syncedAt,
        )
    }

    companion object {
        const val DEFAULT_PAUSE_MS: Long = 100L
    }
}
