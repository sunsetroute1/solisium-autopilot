package com.solisium.core.source

import com.solisium.core.db.SolisiumDatabase
import java.time.Instant

/**
 * Maps warehouse lottery tables into [game_item_drop] with `confidence=extracted`.
 * Requires `TLItemLotteryUnit` (and optionally `TLItemLotteryPublicGroup`) in the warehouse.
 */
object ExtractedDropMapper {
    fun mapInto(
        db: SolisiumDatabase,
        snapshotId: String,
        rewardRows: List<WarehouseJsonRow>,
        lotteryRows: List<WarehouseJsonRow>,
        itemNames: Map<String, String>,
        syncedAt: String = Instant.now().toString(),
    ): ExtractedDropResult {
        val index = LotteryUnitIndex(lotteryRows)
        if (!index.hasUnits) {
            return ExtractedDropResult(0, 0, 0)
        }
        db.transaction {
            db.schemaQueries.clearExtractedItemDrops(snapshotId)
        }
        var profiles = 0
        var dropRows = 0
        var unresolvedGroups = 0
        db.transaction {
            rewardRows.forEach { reward ->
                val slots = RewardLotteryParser.lotterySlots(reward.rawJson)
                if (slots.isEmpty()) return@forEach
                val sourceName = RewardRowIdParser.prettyName(reward.rowId)
                val location = MonsterLocationHints.label(reward.rowId, mapId = null, category = null)
                var mappedAny = false
                slots.forEach { (condition, groupId) ->
                    val unitIds = index.resolveUnitIds(groupId)
                    if (unitIds.isEmpty()) {
                        unresolvedGroups++
                        return@forEach
                    }
                    unitIds.forEach { unitId ->
                        val unit = index.unit(unitId) ?: return@forEach
                        unit.entries.forEach { entry ->
                            db.schemaQueries.insertItemDrop(
                                snapshot_id = snapshotId,
                                item_source_table = "TLItemLooks",
                                item_source_row_id = entry.itemId,
                                item_name = itemNames[entry.itemId],
                                source_kind = "npc",
                                source_id = reward.rowId,
                                source_name = sourceName,
                                source_level = null,
                                source_category = null,
                                map_id = null,
                                location_label = location,
                                probability = entry.probability,
                                quantity = entry.quantity,
                                drop_type = null,
                                drop_condition = condition,
                                confidence = "extracted",
                                synced_at = syncedAt,
                            )
                            dropRows++
                            mappedAny = true
                        }
                    }
                }
                if (mappedAny) profiles++
            }
        }
        return ExtractedDropResult(profiles, dropRows, unresolvedGroups)
    }
}

data class ExtractedDropResult(
    val profilesMapped: Int,
    val dropRows: Int,
    val unresolvedGroups: Int,
)
