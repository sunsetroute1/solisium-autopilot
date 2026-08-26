package com.solisium.core.domain

import com.solisium.core.source.EquipCategory

/**
 * T&L has no creation-screen class. The character window title is the pair of
 * equipped weapons. [source] is `extracted` (warehouse `game_class`), `community`
 * (Questlog / community table), or `manual` (typed override).
 */
data class WeaponClassMatch(
    val name: String? = null,
    val source: String? = null,
    val weaponA: String? = null,
    val weaponB: String? = null,
) {
    val weaponsLabel: String?
        get() {
            val labels = listOfNotNull(WeaponTypeLabel.of(weaponA), WeaponTypeLabel.of(weaponB))
            return labels.takeIf { it.size == 2 }?.sorted()?.joinToString(" · ")
        }

    val pairResolved: Boolean
        get() {
            val a = WeaponTypeLabel.combatToken(weaponA) ?: return false
            val b = WeaponTypeLabel.combatToken(weaponB) ?: return false
            return a != b
        }
}

data class GameClass(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val weaponA: String?,
    val weaponB: String?,
)

data class WeaponClassPair(
    val weaponA: String,
    val weaponB: String,
    val name: String,
) {
    val key: String get() = WeaponTypeLabel.pairKey(weaponA, weaponB) ?: error("invalid weapon pair")
}

/**
 * A selectable character-window class for the Build screen. Extracted
 * `game_class` rows win over the community weapon-pair table for the same pair.
 */
data class BuildClassOption(
    val name: String,
    val weaponA: String,
    val weaponB: String,
    val source: String,
) {
    val key: String get() = WeaponTypeLabel.pairKey(weaponA, weaponB) ?: "$weaponA|$weaponB"
    val tokens: Set<String> get() = setOf(weaponA, weaponB)
    val weaponsLabel: String
        get() = listOfNotNull(WeaponTypeLabel.of(weaponA), WeaponTypeLabel.of(weaponB)).joinToString(" · ")

    fun skillCategories(): Set<String> =
        tokens.mapNotNull { raw ->
            EquipCategory.token(raw)?.removePrefix("k")?.lowercase()
        }.toSet()
}

object ClassSource {
    const val EXTRACTED = "extracted"
    const val COMMUNITY = "community"
    const val MANUAL = "manual"

    fun badge(source: String?): String = when (source) {
        EXTRACTED -> "extracted"
        COMMUNITY -> "community"
        MANUAL -> "manually overridden character class"
        else -> "unknown"
    }

    fun isManual(source: String?): Boolean = source.equals(MANUAL, ignoreCase = true)
}

/**
 * Player-facing weapon type labels from `EItemCategory` tokens. These are the
 * names the character window uses for the two weapons, not class titles.
 */
object WeaponTypeLabel {
    private val combat = setOf(
        "kBow", "kSword2h", "kCrossbow", "kStaff", "kDagger", "kSword",
        "kWand", "kSpear", "kOrb", "kGauntlet",
    )

    private val labels = mapOf(
        "kBow" to "Longbow",
        "kSword2h" to "Greatsword",
        "kCrossbow" to "Crossbow",
        "kStaff" to "Staff",
        "kDagger" to "Daggers",
        "kSword" to "Sword and Shield",
        "kWand" to "Wand and Tome",
        "kSpear" to "Spear",
        "kOrb" to "Orb",
        "kGauntlet" to "Gauntlet",
    )

    fun combatToken(raw: String?): String? {
        val token = EquipCategory.token(raw) ?: return null
        return token.takeIf { it in combat }
    }

    fun of(raw: String?): String? {
        val token = combatToken(raw) ?: return DisplayName.prettyEnum(raw)
        return labels[token] ?: DisplayName.prettyEnum(token)
    }

    fun pairKey(left: String?, right: String?): String? {
        val a = combatToken(left) ?: return null
        val b = combatToken(right) ?: return null
        if (a == b) return null
        return listOf(a, b).sorted().joinToString("|")
    }

    fun canonical(left: String?, right: String?): Pair<String, String>? {
        val a = combatToken(left) ?: return null
        val b = combatToken(right) ?: return null
        if (a == b) return null
        val sorted = listOf(a, b).sorted()
        return sorted[0] to sorted[1]
    }
}
