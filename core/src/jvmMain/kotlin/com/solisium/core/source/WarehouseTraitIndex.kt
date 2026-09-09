package com.solisium.core.source

import com.solisium.core.domain.CatalogTraitOption
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import com.solisium.core.meta.TextNorm
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Maps warehouse trait ids to item row ids via `TLItemTraitGroup.TraitCandidates`.
 * Built once per warehouse path so catalog search can filter "has Ranged Endurance".
 */
class WarehouseTraitIndex(
    val options: List<CatalogTraitOption>,
    private val itemIdsByTrait: Map<String, Set<String>>,
) {
    fun itemIds(traitId: String): Set<String> = itemIdsByTrait[traitId].orEmpty()

    fun resolve(query: String?): CatalogTraitOption? {
        exact(query)?.let { return it }
        val folded = query?.let(TextNorm::fold).orEmpty()
        if (folded.length < 4) return null
        return options.firstOrNull { option ->
            val label = TextNorm.fold(option.label)
            label.contains(folded) || (folded.length >= 8 && folded.contains(label) && label.length >= 6)
        }
    }

    /** Search-box auto-select: only a real trait name, not a substring of one. */
    fun exact(query: String?): CatalogTraitOption? {
        val folded = query?.let(TextNorm::fold).orEmpty()
        if (folded.length < 4) return null
        options.firstOrNull { TextNorm.fold(it.label) == folded }?.let { return it }
        options.firstOrNull { TextNorm.fold(it.statKey) == folded }?.let { return it }
        options.firstOrNull { TextNorm.fold(it.traitId) == folded }?.let { return it }
        return options.firstOrNull { TextNorm.likelySame(it.label, query) }
    }

    fun matchingOptions(query: String?): List<CatalogTraitOption> {
        val folded = query?.let(TextNorm::fold).orEmpty()
        if (folded.isEmpty()) return options
        return options.filter { option ->
            TextNorm.fold(option.label).contains(folded) ||
                TextNorm.fold(option.statKey).contains(folded) ||
                TextNorm.fold(option.traitId).contains(folded)
        }
    }

    companion object {
        fun load(warehousePath: Path, traitNames: Map<String, String>): WarehouseTraitIndex? {
            if (!Files.isRegularFile(warehousePath)) return null
            return runCatching {
                DriverManager.getConnection("jdbc:sqlite:${warehousePath.toAbsolutePath()}").use { connection ->
                    val items = linkedMapOf<String, MutableSet<String>>()
                    connection.prepareStatement(
                        "SELECT row_id, raw_json FROM records WHERE table_name = 'TLItemTraitGroup'",
                    ).use { stmt ->
                        val rs = stmt.executeQuery()
                        while (rs.next()) {
                            val itemId = rs.getString(1) ?: continue
                            val raw = rs.getString(2) ?: continue
                            val group = runCatching { JsonParser.parse(raw) }.getOrNull() ?: continue
                            group.arr("TraitCandidates").forEach { entry ->
                                val obj = entry as? JsonValue.Obj ?: return@forEach
                                val traitId = obj.str("TraitId") ?: return@forEach
                                items.getOrPut(traitId) { linkedSetOf() }.add(itemId)
                            }
                        }
                    }
                    if (items.isEmpty()) return@use null
                    val options = items.keys.map { traitId ->
                        val label = traitNames[traitId]?.trim()?.takeIf {
                            it.isNotEmpty() && !it.equals(traitId, ignoreCase = true)
                        } ?: fallbackTraitLabel(traitId)
                        CatalogTraitOption(
                            traitId = traitId,
                            label = label,
                            statKey = TraitStatKeys.toQuestlogKey(traitId, emptyList()),
                        )
                    }.sortedBy { it.label.lowercase() }
                    WarehouseTraitIndex(options, items)
                }
            }.getOrNull()
        }
    }
}

private fun JsonValue.Obj.str(key: String): String? = (fields[key] as? JsonValue.Str)?.value
