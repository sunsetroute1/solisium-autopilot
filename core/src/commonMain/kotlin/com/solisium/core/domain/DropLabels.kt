package com.solisium.core.domain

/** Human-readable loot-table labels for cached and live drop rows. */
object DropLabels {
    fun conditionLabel(raw: String?): String? {
        val key = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (key.lowercase()) {
            "normaldrop" -> "Normal loot"
            "luckdrop", "luck" -> "Luck bonus"
            "firstdrop", "firstkill" -> "First kill"
            else -> when {
                key.startsWith("normal_", ignoreCase = true) -> {
                    val slot = key.removePrefix("normal_").removePrefix("Normal_")
                    if (slot.isBlank()) "Normal pool" else "Normal · $slot"
                }
                key.startsWith("luck_", ignoreCase = true) -> {
                    val slot = key.removePrefix("luck_").removePrefix("Luck_")
                    if (slot.isBlank()) "Luck bonus" else "Luck · $slot"
                }
                else -> DisplayName.prettyEnum(key)
                    ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
        }
    }

    fun typeLabel(raw: String?): String? {
        val key = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (key.lowercase()) {
            "normaldrop" -> "Field drop"
            "questreward" -> "Quest reward"
            else -> DisplayName.prettyEnum(key)
                ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    /** Explains why two rows with the same display name can show different rates. */
    fun rateContextNote(sources: List<ItemDropSource>): String? {
        if (sources.isEmpty()) return null
        val distinctItems = sources.map { it.itemSourceRowId }.distinct()
        val distinctConditions = sources.mapNotNull { it.dropCondition?.lowercase() }.distinct()
        return when {
            distinctItems.size > 1 ->
                "Each row is a separate item variant (tier/grade in the row id). Rates apply per variant, not per display name."
            distinctConditions.size > 1 ->
                "Rates are per loot-table slot (normal vs luck, etc.) on the same monster — they do not add up to one combined chance."
            else -> null
        }
    }

    fun questlogRateContextNote(entries: List<QuestlogDropEntry>): String? {
        if (entries.isEmpty()) return null
        val distinctItems = entries.map { it.id }.distinct()
        val distinctConditions = entries.mapNotNull { it.dropCondition?.lowercase() }.distinct()
        return when {
            distinctItems.size > 1 ->
                "Questlog lists separate item ids (often tier suffixes like t1/t5). Compare rates only within the same id."
            distinctConditions.size > 1 ->
                "Each line is its own loot slot on the source — rates are not summed into one drop chance."
            else -> null
        }
    }
}
