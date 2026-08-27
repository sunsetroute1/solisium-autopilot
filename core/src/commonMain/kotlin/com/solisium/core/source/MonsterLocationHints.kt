package com.solisium.core.source

/**
 * Best-effort region / content labels from TL-Helper reward row ids and Questlog map ids.
 * Questlog does not expose a public map-name API; [mapId] is shown when no name is known.
 */
object MonsterLocationHints {
    private val LEVEL = Regex("""L(\d{2})""")

    fun label(rowId: String, mapId: Long?, category: String?): String {
        val parts = mutableListOf<String>()
        when {
            rowId.startsWith("GR_", ignoreCase = true) -> parts += "Guild raid"
            rowId.startsWith("WD_", ignoreCase = true) -> parts += "World boss"
            rowId.startsWith("FD_", ignoreCase = true) -> parts += "Open world field"
            rowId.startsWith("CCG_", ignoreCase = true) -> parts += "Solo challenge"
            rowId.startsWith("DE_", ignoreCase = true) -> parts += "Demilitarized zone"
            rowId.startsWith("GD_", ignoreCase = true) -> parts += "Guardian content"
            rowId.startsWith("FI_", ignoreCase = true) -> parts += "Field instance"
            rowId.startsWith("IS_", ignoreCase = true) -> parts += "Instance"
        }
        LEVEL.find(rowId)?.groupValues?.get(1)?.let { parts += "Region L$it" }
        when (category?.lowercase()) {
            "boss" -> if ("boss" !in parts.joinToString().lowercase()) parts += "Field boss"
            "solo" -> parts += "Solo"
        }
        mapId?.let { parts += mapLabel(it) }
        return parts.distinct().joinToString(" · ").ifBlank { mapId?.let { mapLabel(it) } ?: "Unknown area" }
    }

    fun mapLabel(mapId: Long): String = "Questlog map $mapId"
}
