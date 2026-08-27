package com.solisium.core.talkingwall

import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue

data class TalkingWallImportSummary(
    val warehouseImported: Int = 0,
    val communityAdded: Int = 0,
    val communitySkipped: Int = 0,
) {
    val total: Int get() = warehouseImported + communityAdded
}

object TalkingWallImporter {
    fun insertWarehouse(
        db: SolisiumDatabase,
        snapshotId: String,
        sourceTable: String,
        sourceRowId: String,
        parsed: ParsedTalkingWallStatement,
    ) {
        db.schemaQueries.replaceGameTalkingWallStatement(
            snapshot_id = snapshotId,
            source_table = sourceTable,
            source_row_id = sourceRowId,
            statement_key = TalkingWallMapper.statementKey(parsed.statement),
            statement = parsed.statement,
            answer_true = if (parsed.answerTrue) 1L else 0L,
            category = parsed.category,
            notes = parsed.notes,
            source_kind = "warehouse",
        )
    }

    fun supplementCommunity(db: SolisiumDatabase, snapshotId: String, jsonText: String): TalkingWallImportSummary {
        val statements = parseCommunityStatements(jsonText)
        var added = 0
        var updated = 0
        var skipped = 0
        statements.forEach { entry ->
            val key = TalkingWallMapper.statementKey(entry.statement)
            val existed = db.schemaQueries.countTalkingWallStatementKey(snapshotId, key).executeAsOne() > 0
            db.schemaQueries.replaceGameTalkingWallStatement(
                snapshot_id = snapshotId,
                source_table = "community/talking-wall",
                source_row_id = entry.id,
                statement_key = key,
                statement = entry.statement,
                answer_true = if (entry.answerTrue) 1L else 0L,
                category = entry.category,
                notes = entry.notes,
                source_kind = "community",
            )
            if (existed) updated++ else added++
        }
        val warehouse = db.schemaQueries.countTalkingWallBySourceKind(snapshotId, "warehouse").executeAsOne()
        return TalkingWallImportSummary(
            warehouseImported = warehouse.toInt(),
            communityAdded = added,
            communitySkipped = skipped + updated,
        )
    }

    fun parseCommunityStatementCount(jsonText: String): Int =
        parseCommunityStatements(jsonText).size

    private fun parseCommunityStatements(jsonText: String): List<CommunityStatement> {
        val root = JsonParser.parse(jsonText) as? JsonValue.Obj ?: return emptyList()
        return root.arr("statements").mapNotNull { entry ->
            val obj = entry as? JsonValue.Obj ?: return@mapNotNull null
            val statement = obj.str("statement") ?: return@mapNotNull null
            CommunityStatement(
                id = obj.str("id") ?: TalkingWallMapper.statementKey(statement),
                statement = statement,
                answerTrue = obj.bool("answerTrue") ?: return@mapNotNull null,
                category = obj.str("category"),
                notes = obj.str("notes"),
            )
        }
    }

    private data class CommunityStatement(
        val id: String,
        val statement: String,
        val answerTrue: Boolean,
        val category: String?,
        val notes: String?,
    )

    private fun JsonValue.Obj.str(key: String): String? = (fields[key] as? JsonValue.Str)?.value?.takeIf { it.isNotBlank() }

    private fun JsonValue.Obj.bool(key: String): Boolean? = when (val v = fields[key]) {
        is JsonValue.Bool -> v.value
        is JsonValue.Str -> when (v.value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }

    private fun JsonValue.Obj.arr(key: String): List<JsonValue> =
        (fields[key] as? JsonValue.Arr)?.items ?: emptyList()
}
