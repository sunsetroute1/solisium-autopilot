package com.solisium.core.source

import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue

/** Minimal warehouse row for lottery indexing (table name + decoded json). */
data class WarehouseJsonRow(
    val tableName: String,
    val rowId: String,
    val rawJson: String,
)

data class LotteryUnitEntry(
    val itemId: String,
    val probability: Double?,
    val quantity: Long?,
)

data class LotteryUnit(
    val rowId: String,
    val entries: List<LotteryUnitEntry>,
)

/**
 * Indexes lottery tables from warehouse rows. Resolves `public_lottery_group_id` targets
 * to [LotteryUnit] entries (`ItemLotteryUnitEntry`: item + prob, prob / 1e7).
 */
class LotteryUnitIndex(rows: List<WarehouseJsonRow>) {
    private val units: Map<String, LotteryUnit>
    private val publicGroups: Map<String, List<String>>

    init {
        val unitRows = rows.filter { it.tableName == "TLItemLotteryUnit" }
        units = unitRows.associate { row ->
            row.rowId to parseUnit(row.rowId, row.rawJson)
        }
        val unitIds = units.keys
        publicGroups = rows.filter { it.tableName == "TLItemLotteryPublicGroup" }
            .associate { row ->
                val refs = parsePublicGroupRefs(row.rawJson, unitIds)
                row.rowId to refs
            }
    }

    val hasUnits: Boolean get() = units.isNotEmpty()

    fun resolveUnitIds(groupId: String): List<String> {
        publicGroups[groupId]?.takeIf { it.isNotEmpty() }?.let { return it }
        if (units.containsKey(groupId)) return listOf(groupId)
        return emptyList()
    }

    fun unit(rowId: String): LotteryUnit? = units[rowId]

    companion object {
        private const val PROB_SCALE = 10_000_000.0

        fun parseUnit(rowId: String, rawJson: String): LotteryUnit {
            val json = runCatching { JsonParser.parse(rawJson) }.getOrNull()
                ?: return LotteryUnit(rowId, emptyList())
            val entries = json.arr("ItemLotteryUnitEntry").mapNotNull { entry ->
                val obj = entry as? JsonValue.Obj ?: return@mapNotNull null
                val itemId = obj.str("item")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val weight = obj.longAny("prob", "Prob", "probability")
                val probability = weight?.let { (it.toDouble() / PROB_SCALE).coerceIn(0.0, 1.0) }
                val quantity = obj.longAny("count", "Count", "quantity", "Quantity")
                LotteryUnitEntry(itemId = itemId, probability = probability, quantity = quantity)
            }
            return LotteryUnit(rowId, entries)
        }

        /** Walk group json for string refs that match known lottery unit row ids. */
        fun parsePublicGroupRefs(rawJson: String, unitIds: Set<String>): List<String> {
            val json = runCatching { JsonParser.parse(rawJson) }.getOrNull() ?: return emptyList()
            val found = linkedSetOf<String>()
            fun walk(value: JsonValue) {
                when (value) {
                    is JsonValue.Str -> if (value.value in unitIds) found += value.value
                    is JsonValue.Obj -> value.fields.values.forEach { walk(it) }
                    is JsonValue.Arr -> value.items.forEach { walk(it) }
                    else -> Unit
                }
            }
            walk(json)
            return found.toList()
        }
    }
}
