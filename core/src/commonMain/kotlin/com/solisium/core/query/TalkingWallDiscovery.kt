package com.solisium.core.query

import com.solisium.core.domain.TalkingWallSnapshotDelta
import com.solisium.core.db.SolisiumDatabase

object TalkingWallDiscovery {
    fun delta(db: SolisiumDatabase, currentSnapshotId: String, previousSnapshotId: String?): TalkingWallSnapshotDelta? {
        if (previousSnapshotId == null || previousSnapshotId == currentSnapshotId) return null
        val q = db.schemaQueries
        val currentTotal = q.countTalkingWallStatements(currentSnapshotId).executeAsOne()
        val previousTotal = q.countTalkingWallStatements(previousSnapshotId).executeAsOne()
        if (currentTotal <= previousTotal) return null
        val warehouseAdded = q.countTalkingWallBySourceKind(currentSnapshotId, "warehouse").executeAsOne() -
            q.countTalkingWallBySourceKind(previousSnapshotId, "warehouse").executeAsOne()
        return TalkingWallSnapshotDelta(
            previousTotal = previousTotal,
            currentTotal = currentTotal,
            warehouseAdded = warehouseAdded.coerceAtLeast(0),
        )
    }
}
