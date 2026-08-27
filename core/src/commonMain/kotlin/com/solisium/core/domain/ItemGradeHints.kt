package com.solisium.core.domain

/**
 * Resolves item rarity when warehouse rows are missing or after a fresh import.
 * Row ids often encode tier (`_t5_`) and sometimes grade tokens (`_aa_`, `_aaa_`).
 */
object ItemGradeHints {
    private val tierPattern = Regex("""_t(\d+)_""", RegexOption.IGNORE_CASE)

    fun variantLabel(sourceRowId: String): String? =
        tierPattern.find(sourceRowId)?.groupValues?.getOrNull(1)?.let { "Tier $it" }

    /**
     * Best-effort grade from row-id tokens when [game_item.grade] is absent.
     * Longer tokens are checked first so `_aa_` does not match as `_a_`.
     */
    fun inferFromRowId(sourceRowId: String): String? {
        val lower = sourceRowId.lowercase()
        return when {
            "_aaa_" in lower || "_heroic_" in lower || "_legendary_" in lower -> "EItemGrade::kAAA"
            "_aa_" in lower || "_epic_" in lower -> "EItemGrade::kAA"
            Regex("""_a_\d""").containsMatchIn(lower) || "_rare_" in lower -> "EItemGrade::kA"
            "_b_" in lower || "_uncommon_" in lower -> "EItemGrade::kB"
            "_c_" in lower || "_common_" in lower -> "EItemGrade::kC"
            else -> null
        }
    }
}
