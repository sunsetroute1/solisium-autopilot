package com.solisium.core.query

import com.solisium.core.domain.TalkingWallSnapshotDelta
import com.solisium.core.domain.TalkingWallStatement
import com.solisium.core.db.SolisiumDatabase

object TalkingWallDiscovery {
    fun delta(db: SolisiumDatabase, currentSnapshotId: String, previousSnapshotId: String?): TalkingWallSnapshotDelta? {
        if (previousSnapshotId == null || previousSnapshotId == currentSnapshotId) return null
        val q = db.schemaQueries
        val currentTotal = q.countTalkingWallStatements(currentSnapshotId).executeAsOne()
        val previousTotal = q.countTalkingWallStatements(previousSnapshotId).executeAsOne()
        val clientAdded = q.countTalkingWallBySourceKind(currentSnapshotId, "warehouse").executeAsOne() +
            q.countTalkingWallBySourceKind(currentSnapshotId, "locres").executeAsOne() -
            q.countTalkingWallBySourceKind(previousSnapshotId, "warehouse").executeAsOne() -
            q.countTalkingWallBySourceKind(previousSnapshotId, "locres").executeAsOne()
        val newFromGame = q.selectNewTalkingWallFromWarehouse(
            currentSnapshotId,
            previousSnapshotId,
            64L,
        ).executeAsList().map { row ->
            TalkingWallStatement(
                sourceTable = row.source_table,
                sourceRowId = row.source_row_id,
                statement = row.statement,
                answerTrue = row.answer_true != 0L,
                category = row.category,
                notes = row.notes,
                sourceKind = row.source_kind,
            )
        }
        if (currentTotal <= previousTotal && newFromGame.isEmpty()) return null
        return TalkingWallSnapshotDelta(
            previousTotal = previousTotal,
            currentTotal = currentTotal,
            warehouseAdded = clientAdded.coerceAtLeast(0),
            newFromGame = newFromGame,
        )
    }
}
