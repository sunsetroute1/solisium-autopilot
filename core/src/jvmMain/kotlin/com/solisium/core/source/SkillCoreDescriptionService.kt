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
    private var complexCache: Pair<String, Map<String, String>>? = null

    fun invalidate() {
        tableCache = null
        complexCache = null
    }

    fun description(
        rowId: String,
        name: String?,
        warehousePath: String?,
        gameBuild: String? = null,
    ): String? {
        if (!SkillFamilyLookup.isSkillCoreItem(rowId, name)) return null
        val table = locresTable(gameBuild) ?: return null
        val complexId = complexId(warehousePath, rowId)
        return SkillCoreDescriptionLookup.description(table, name, complexId)
    }

    private fun locresTable(gameBuild: String?): LocresTable? {
        val path = locresLocator.find(gameBuild) ?: return null
        val key = cacheKey(path, gameBuild)
        tableCache?.let { if (it.first == key) return it.second }
        val loaded = runCatching { loadTable(path) }.getOrNull() ?: return null
        tableCache = key to loaded
        return loaded
    }

    private fun complexId(warehousePath: String?, perkRowId: String): String? {
        val path = warehousePath?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it) } ?: return null
        if (!isFile(path)) return null
        val key = cacheKey(path)
        val cached = complexCache
        val index = if (cached != null && cached.first == key) {
            cached.second
        } else {
            val loaded = loadComplexIds(path)
            complexCache = key to loaded
            loaded
        }
        return index[perkRowId.lowercase()]
            ?: SkillCoreDescriptionLookup.equipRowId(perkRowId)?.let { index[it.lowercase()] }
    }

    private fun cacheKey(path: Path, extra: String? = null): String {
        val meta = runCatching { fileMeta(path) }.getOrDefault(0L to 0L)
        return "${path.toAbsolutePath()}|${extra.orEmpty()}|${meta.first}|${meta.second}"
    }

    private fun loadComplexIds(warehouse: Path): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.prepareStatement(
                "SELECT row_id, raw_json FROM records WHERE table_name = 'TLItemEquip'",
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        val rowId = rs.getString("row_id") ?: continue
                        val json = rs.getString("raw_json") ?: continue
                        val complex = runCatching {
                            JsonParser.parse(json).str("unique_skill_complex_id")
                        }.getOrNull()?.trim().orEmpty()
                        if (complex.isEmpty() || complex.equals("None", ignoreCase = true)) continue
                        out[rowId.lowercase()] = complex
                    }
                }
            }
        }
        return out
    }
}
