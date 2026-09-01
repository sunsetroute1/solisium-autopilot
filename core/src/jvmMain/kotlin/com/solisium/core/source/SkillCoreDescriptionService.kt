package com.solisium.core.source

import com.solisium.core.json.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Skill-core tooltips from extracted locres plus the perk's `TLItemEquip`
 * `unique_skill_complex_id`. Cache keys include path, mtime, size, and game
 * build so a patch extract or warehouse re-import is picked up.
 */
class SkillCoreDescriptionService(
    private val locresLocator: LocresLocator = LocresLocator(),
    private val loadTable: (Path) -> LocresTable = LocresTable::load,
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val fileMeta: (Path) -> Pair<Long, Long> = { path ->
        Files.getLastModifiedTime(path).toMillis() to Files.size(path)
    },
) {
    @Volatile
    private var tableCache: Pair<String, LocresTable>? = null

    @Volatile
    private var warehouseCache: Pair<String, WarehouseIndex>? = null

    fun invalidate() {
        tableCache = null
        warehouseCache = null
    }

    fun description(
        rowId: String,
        name: String?,
        warehousePath: String?,
        gameBuild: String? = null,
    ): String? {
        if (!SkillFamilyLookup.isSkillCoreItem(rowId, name)) return null
        val table = locresTable(gameBuild) ?: return null
        val warehouse = warehouseIndex(warehousePath)
        val complexId = warehouse?.complexId(rowId)
        return SkillCoreDescriptionLookup.description(table, name, complexId, warehouse ?: TooltipFieldLookup.Empty)
    }

    private fun locresTable(gameBuild: String?): LocresTable? {
        val path = locresLocator.find(gameBuild) ?: return null
        val key = cacheKey(path, gameBuild)
        tableCache?.let { if (it.first == key) return it.second }
        val loaded = runCatching { loadTable(path) }.getOrNull() ?: return null
        tableCache = key to loaded
        return loaded
    }

    private fun warehouseIndex(warehousePath: String?): WarehouseIndex? {
        val path = warehousePath?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it) } ?: return null
        if (!isFile(path)) return null
        val key = cacheKey(path)
        warehouseCache?.let { if (it.first == key) return it.second }
        val loaded = loadWarehouse(path)
        warehouseCache = key to loaded
        return loaded
    }

    private fun cacheKey(path: Path, extra: String? = null): String {
        val meta = runCatching { fileMeta(path) }.getOrDefault(0L to 0L)
        return "${path.toAbsolutePath()}|${extra.orEmpty()}|${meta.first}|${meta.second}"
    }

    private fun loadWarehouse(warehouse: Path): WarehouseIndex {
        val complexIds = LinkedHashMap<String, String>()
        val tooltips = LinkedHashMap<String, Map<String, Double>>()
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.prepareStatement(
                "SELECT row_id, table_name, raw_json FROM records WHERE table_name IN ('TLItemEquip','TLFormulaParameterNew')",
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        val rowId = rs.getString("row_id") ?: continue
                        val table = rs.getString("table_name") ?: continue
                        val json = rs.getString("raw_json") ?: continue
                        when (table) {
                            "TLItemEquip" -> {
                                val complex = runCatching {
                                    JsonParser.parse(json).str("unique_skill_complex_id")
                                }.getOrNull()?.trim().orEmpty()
                                if (complex.isNotEmpty() && !complex.equals("None", ignoreCase = true)) {
                                    complexIds[rowId.lowercase()] = complex
                                }
                            }
                            "TLFormulaParameterNew" -> {
                                parseFormulaFields(json)?.let { tooltips[rowId.lowercase()] = it }
                            }
                        }
                    }
                }
            }
        }
        return WarehouseIndex(complexIds, tooltips)
    }

    private fun parseFormulaFields(rawJson: String): Map<String, Double>? {
        val root = runCatching { JsonParser.parse(rawJson) }.getOrNull() ?: return null
        val rows = root.arr("FormulaParameter")
        val first = rows.firstOrNull { it.long("skill_level") == 1L } ?: rows.firstOrNull() ?: return null
        val fields = linkedMapOf<String, Double>()
        FORMULA_FIELDS.forEach { key ->
            first.double(key)?.let { fields[key.lowercase()] = it }
        }
        return fields.takeIf { it.isNotEmpty() }
    }

    private class WarehouseIndex(
        private val complexIds: Map<String, String>,
        private val tooltips: Map<String, Map<String, Double>>,
    ) : TooltipFieldLookup {
        fun complexId(perkRowId: String): String? =
            complexIds[perkRowId.lowercase()]
                ?: SkillCoreDescriptionLookup.equipRowId(perkRowId)?.let { complexIds[it.lowercase()] }

        override fun get(rowId: String, field: String): Double? =
            tooltips[rowId.lowercase()]?.get(field.lowercase())
    }

    companion object {
        private val FORMULA_FIELDS = listOf(
            "tooltip1", "tooltip2", "min", "max", "add", "mul", "mul2", "mul3",
        )
    }
}
