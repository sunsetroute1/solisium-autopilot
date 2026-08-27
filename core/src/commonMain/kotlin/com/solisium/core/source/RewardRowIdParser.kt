package com.solisium.core.source

import com.solisium.core.domain.MonsterProfile

/**
 * Turns TL-Helper `TLRewardNpcFoItem.row_id` tokens into a readable label until Questlog
 * fills in the localized name. These ids align with Questlog npc ids (e.g. `FD_L03_M_Golem_Talus_001`).
 */
object RewardRowIdParser {
    private val LEVEL = Regex("""L(\d{2})""")
    private val MONSTER_BODY = Regex("""_M_(.+?)_(?:S\d+[A-Z]_)?\d+$""", RegexOption.IGNORE_CASE)
    private val MONSTER_BODY_ALT = Regex("""_M_(.+)$""", RegexOption.IGNORE_CASE)

    fun profile(rowId: String, sourceTable: String = "TLRewardNpcFoItem"): MonsterProfile {
        val level = LEVEL.find(rowId)?.groupValues?.get(1)?.let { "L$it" }
        val kind = when {
            rowId.contains("_Treasure_", ignoreCase = true) -> "treasure"
            rowId.startsWith("FD_", ignoreCase = true) -> "field boss"
            rowId.startsWith("GR_", ignoreCase = true) -> "guild raid"
            rowId.contains("Dungeon", ignoreCase = true) -> "dungeon"
            rowId.contains("Raid", ignoreCase = true) -> "raid"
            else -> "mob"
        }
        return MonsterProfile(
            sourceTable = sourceTable,
            sourceRowId = rowId,
            displayName = prettyName(rowId),
            kindHint = kind,
            levelHint = level,
        )
    }

    fun prettyName(rowId: String): String {
        val body = MONSTER_BODY.find(rowId)?.groupValues?.get(1)
            ?: MONSTER_BODY_ALT.find(rowId)?.groupValues?.get(1)
            ?: rowId.substringAfterLast('_')
        return body.split('_')
            .filter { it.isNotBlank() && !it.matches(Regex("""\d+""")) }
            .joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecaseChar() else ch
                }
            }
            .ifBlank { rowId.replace('_', ' ') }
    }
}
