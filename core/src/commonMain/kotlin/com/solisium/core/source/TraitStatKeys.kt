package com.solisium.core.source

import com.solisium.core.domain.DisplayName

/** Maps warehouse trait ids to Questlog `itemStats.traits` keys. */
object TraitStatKeys {
    fun toQuestlogKey(traitId: String, traitStatTokens: List<String>): String {
        val token = traitStatTokens.firstOrNull()
            ?.substringAfter("::")
            ?.removePrefix("k")
            ?: traitId.removePrefix("Unique_").substringBefore('_').removePrefix("k")
        return camelToSnake(token)
    }

    fun camelToSnake(raw: String): String =
        raw.replace(Regex("([a-z0-9])([A-Z])")) { match ->
            "${match.groupValues[1]}_${match.groupValues[2]}"
        }.lowercase()
}

/** Trait slot count by item row id tier segment — mirrors in-game trait lines. */
object ItemTraitSlots {
    fun countFor(itemRowId: String): Int = when {
        "_t3_" in itemRowId || "_S1_" in itemRowId || "_aaa_" in itemRowId || "_aa3_" in itemRowId -> 3
        "_t2_" in itemRowId || "_aa2_" in itemRowId -> 2
        else -> 1
    }
}

/** Pretty label when [gameTraitNames] lacks a row (unique traits, etc.). */
fun fallbackTraitLabel(traitId: String): String {
    val stripped = traitId.removePrefix("Unique_")
    val core = stripped.substringBefore('_').removePrefix("k")
    return DisplayName.prettyEnum("k$core") ?: core.replaceFirstChar { it.uppercase() }
}
