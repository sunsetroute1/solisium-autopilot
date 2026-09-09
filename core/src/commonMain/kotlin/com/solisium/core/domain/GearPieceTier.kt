package com.solisium.core.domain

/**
 * Piece generation encoded in warehouse row ids (`bow_aa_t2_raid_001` → T2).
 * Not rarity (Epic/Heroic) and not trait-upgrade T1–T4 on a roll.
 */
object GearPieceTier {
    private val token = Regex("""_t(\d+)_""", RegexOption.IGNORE_CASE)

    fun fromRowId(sourceRowId: String): Int? =
        token.find(sourceRowId)?.groupValues?.getOrNull(1)?.toIntOrNull()

    /** Same piece family across generations: `bow_aa_t2_raid_001` → `bow_aa_t*_raid_001`. */
    fun familyKey(sourceRowId: String): String =
        token.replace(sourceRowId, "_t*_")

    fun label(tier: Int): String = "T$tier"
}
