package com.solisium.core.domain

/**
 * Resolves item rarity when warehouse rows are missing or after a fresh import.
 * Row ids often encode tier (`_t5_`) and sometimes grade tokens (`_aa_`, `_aaa_`).
 */
object ItemGradeHints {
    private val tierPattern = Regex("""_t(\d+)_""", RegexOption.IGNORE_CASE)
    private val kAaaToken = Regex("""_kaaa(?:_|$|\d)""", RegexOption.IGNORE_CASE)
    private val kAaToken = Regex("""_kaa(?:_|$|\d)""", RegexOption.IGNORE_CASE)
    private val kAToken = Regex("""_ka(?:_\d|\d|_)""", RegexOption.IGNORE_CASE)
    private val kBToken = Regex("""_kb(?:_|$|\d)""", RegexOption.IGNORE_CASE)
    private val kCToken = Regex("""_kc(?:_|$|\d)""", RegexOption.IGNORE_CASE)
    private val aTierToken = Regex("""_a_\d""", RegexOption.IGNORE_CASE)

    fun variantLabel(sourceRowId: String): String? =
        tierPattern.find(sourceRowId)?.groupValues?.getOrNull(1)?.let { "Tier $it" }

    /** Normalize any grade token to a canonical enum-ish string for display and color lookup. */
    fun normalizeGrade(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val pretty = DisplayName.prettyEnum(trimmed)?.uppercase() ?: trimmed.uppercase()
        return when {
            pretty in setOf("AAA", "LEGENDARY", "HEROIC", "MYTHIC") -> "EItemGrade::kAAA"
            pretty.startsWith("EPIC") || pretty == "AA" -> "EItemGrade::kAA"
            pretty.startsWith("RARE") || pretty == "A" -> "EItemGrade::kA"
            pretty == "B" || pretty == "UNCOMMON" -> "EItemGrade::kB"
            pretty == "C" || pretty == "COMMON" -> "EItemGrade::kC"
            trimmed.contains("ItemGrade", ignoreCase = true) -> trimmed
            pretty in setOf("EPIC", "RARE", "UNCOMMON", "COMMON", "HEROIC", "LEGENDARY") ->
                when (pretty) {
                    "HEROIC", "LEGENDARY" -> "EItemGrade::kAAA"
                    "EPIC" -> "EItemGrade::kAA"
                    "RARE" -> "EItemGrade::kA"
                    "UNCOMMON" -> "EItemGrade::kB"
                    "COMMON" -> "EItemGrade::kC"
                    else -> trimmed
                }
            else -> trimmed
        }
    }

    fun resolve(explicit: String?, sourceRowId: String? = null): String? {
        explicit?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizeGrade(it) ?: it }?.let { return it }
        return sourceRowId?.let { inferFromRowId(it) }
    }

    fun pickBestGrade(candidates: Iterable<String?>): String? =
        candidates.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .maxByOrNull { gradeRank(it) }

    fun gradeRank(raw: String): Int {
        val token = DisplayName.prettyEnum(normalizeGrade(raw) ?: raw)?.uppercase() ?: return 0
        return when {
            token in setOf("AAA", "LEGENDARY", "HEROIC", "MYTHIC") -> 50
            token.startsWith("EPIC") || token == "AA" -> 40
            token.startsWith("RARE") || token == "A" -> 30
            token == "B" || token == "UNCOMMON" -> 20
            token == "C" || token == "COMMON" -> 10
            else -> 1
        }
    }

    /**
     * Best-effort grade from row-id tokens when [game_item.grade] is absent.
     * Longer tokens are checked first so `_aa_` does not match as `_a_`.
     */
    fun inferFromRowId(sourceRowId: String): String? {
        val lower = sourceRowId.lowercase()
        return when {
            "_aaa_" in lower || "_heroic_" in lower || "_legendary_" in lower || kAaaToken.containsMatchIn(lower) ->
                "EItemGrade::kAAA"
            "_aa_" in lower || "_epic_" in lower || kAaToken.containsMatchIn(lower) ->
                "EItemGrade::kAA"
            aTierToken.containsMatchIn(lower) || "_rare_" in lower || kAToken.containsMatchIn(lower) ->
                "EItemGrade::kA"
            "_b_" in lower || "_uncommon_" in lower || kBToken.containsMatchIn(lower) ->
                "EItemGrade::kB"
            "_c_" in lower || "_common_" in lower || kCToken.containsMatchIn(lower) ->
                "EItemGrade::kC"
            else -> null
        }
    }
}
