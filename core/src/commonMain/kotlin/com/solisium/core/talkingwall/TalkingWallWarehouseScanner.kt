package com.solisium.core.talkingwall

import com.solisium.core.json.JsonParser

/**
 * Inspects TL-Helper warehouse rows for quiz / Talking Wall tables and reports
 * how many rows [TalkingWallMapper] can actually parse (game-file truth vs community key).
 */
object TalkingWallWarehouseScanner {
    data class TableScan(
        val tableName: String,
        val rowCount: Int,
        val parsedCount: Int,
        val unparsedSampleRowIds: List<String>,
    ) {
        val unparsedCount: Int get() = rowCount - parsedCount
    }

    data class Report(
        val tables: List<TableScan>,
    ) {
        val parsedTotal: Int get() = tables.sumOf { it.parsedCount }
        val candidateRows: Int get() = tables.sumOf { it.rowCount }
        val unparsedTotal: Int get() = tables.sumOf { it.unparsedCount }

        fun summary(): String =
            tables.joinToString { "${it.tableName}: ${it.parsedCount}/${it.rowCount} parsed" }
    }

    data class Row(
        val tableName: String,
        val rowId: String,
        val nameLoc: String?,
        val rawJson: String?,
    )

    fun scan(rows: List<Row>): Report {
        val grouped = rows.filter { TalkingWallMapper.considers(it.tableName) }
            .groupBy { it.tableName }
        val tables = grouped.map { (tableName, tableRows) ->
            var parsed = 0
            val unparsedSamples = mutableListOf<String>()
            tableRows.forEach { row ->
                val json = runCatching { JsonParser.parse(row.rawJson ?: "{}") }.getOrNull()
                if (json != null &&
                    TalkingWallMapper.parseWarehouseRow(tableName, row.rowId, row.nameLoc, json) != null
                ) {
                    parsed++
                } else if (unparsedSamples.size < 5) {
                    unparsedSamples += row.rowId
                }
            }
            TableScan(
                tableName = tableName,
                rowCount = tableRows.size,
                parsedCount = parsed,
                unparsedSampleRowIds = unparsedSamples,
            )
        }.sortedByDescending { it.rowCount }
        return Report(tables)
    }
}
