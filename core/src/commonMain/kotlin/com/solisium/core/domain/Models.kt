package com.solisium.core.domain

data class DatasetSnapshot(
    val id: String,
    val source: String,
    val extractedAt: String,
    val gameBuild: String,
    val gameVersion: String,
    val schemaVersion: Long,
    val sourcePath: String?,
    val sourceHash: String?,
    val decoderVersion: String?,
    val active: Boolean,
    val aliases: List<String> = emptyList(),
)

data class CatalogCounts(
    val items: Long,
    val runes: Long,
    val skills: Long,
    val recipes: Long,
    val weapons: Long = 0,
    val armor: Long = 0,
    val accessories: Long = 0,
    val effects: Long = 0,
    val synergies: Long = 0,
    val stats: Long = 0,
    val traits: Long = 0,
    val materials: Long = 0,
    val formulas: Long = 0,
    val itemStats: Long = 0,
    val itemsWithStats: Long = 0,
    val curvePoints: Long = 0,
    val itemCurveLinks: Long = 0,
    val classes: Long = 0,
    val combatPowerRows: Long = 0,
    val itemPowerLinks: Long = 0,
    val monsters: Long = 0,
    val talkingWallStatements: Long = 0,
)

data class TalkingWallStatement(
    val sourceTable: String,
    val sourceRowId: String,
    val statement: String,
    val answerTrue: Boolean,
    val category: String?,
    val notes: String?,
    val sourceKind: String,
)

/** Category bucket for Talking Wall true/false statements. */
data class TalkingWallCategoryCount(
    val category: String?,
    val count: Long,
)

data class TalkingWallCoverage(
    val total: Long,
    val warehouse: Long,
    val community: Long,
    val categories: List<TalkingWallCategoryCount>,
)

data class TalkingWallSnapshotDelta(
    val previousTotal: Long,
    val currentTotal: Long,
    val warehouseAdded: Long,
) {
    val added: Long get() = (currentTotal - previousTotal).coerceAtLeast(0)
}

data class GameItem(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val grade: String?,
    val category: String?,
)

data class GameRune(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val grade: String?,
)

data class GameSkill(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val skillType: String?,
    val family: String? = null,
    val weaponToken: String? = null,
    val familyConfidence: String? = null,
)

data class GameRecipe(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val recipeKind: String?,
)

data class GameWeapon(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val weaponType: String?,
)

data class GameArmor(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val slot: String?,
    val material: String?,
)

data class GameAccessory(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val slot: String?,
)

data class GameRuneSynergy(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
)

data class GameSkillEffect(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
    val skillSourceRowId: String?,
)

data class GameStat(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
)

/**
 * One stat value on one item. [rawValue] is the client integer with no scaling
 * applied; the client's per-stat scale factor has not been verified, so this is
 * not a percentage and not a display value.
 */
data class GameItemStat(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val statKey: String,
    val statName: String?,
    val rawValue: Long,
    val scope: String,
    val confidence: String,
)

/**
 * One point on a shared stat curve. [rawValue] is the client's cumulative total at
 * [level], not a per-level delta. Whether it combines additively with an item's base
 * stats is unverified, so callers must not silently sum the two.
 */
data class GameCurvePoint(
    val curveKind: String,
    val curveId: String,
    val level: Long,
    val statKey: String,
    val statName: String?,
    val rawValue: Long,
    val confidence: String,
)

data class GameItemCurve(
    val curveKind: String,
    val curveSourceTable: String,
    val curveId: String,
    val maxLevel: Long?,
)

data class GameTrait(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
)

data class GameMaterial(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val name: String?,
)

/**
 * A raw client formula row. `confidence` is `extracted` for imported rows:
 * the parameters are read from the table, not solved into damage output.
 */
data class GameSkillFormula(
    val snapshotId: String,
    val sourceTable: String,
    val sourceRowId: String,
    val skillSourceRowId: String?,
    val expression: String?,
    val confidence: String,
)

data class UserCharacter(
    val id: String,
    val name: String,
    val level: Long?,
    val combatPower: Long?,
    val gearScore: Long?,
    val server: String?,
    val notes: String?,
    val strength: Long? = null,
    val dexterity: Long? = null,
    val wisdom: Long? = null,
    val perception: Long? = null,
    val fortitude: Long? = null,
    val className: String? = null,
    val classSource: String? = null,
) {
    val statPoints: CharacterAttributes.Points
        get() = CharacterAttributes.Points(strength, dexterity, wisdom, perception, fortitude)
}

data class UserEquipment(
    val slot: String,
    val sourceTable: String?,
    val sourceRowId: String?,
    val itemLevel: Long?,
    val name: String? = null,
)

data class UserWeapon(
    val slot: String,
    val sourceTable: String?,
    val sourceRowId: String?,
    val itemLevel: Long?,
    val name: String? = null,
)

data class UserTrait(
    val sourceTable: String?,
    val sourceRowId: String?,
    val rank: Long?,
)

data class UserRune(
    val slot: String?,
    val sourceTable: String?,
    val sourceRowId: String?,
    val runeLevel: Long?,
)

data class UserSkill(
    val sourceTable: String?,
    val sourceRowId: String?,
    val loadout: String?,
    val name: String? = null,
    val skillLevel: Long? = null,
    val family: String? = null,
)

data class UserStack(
    val sourceTable: String?,
    val sourceRowId: String?,
    val quantity: Long,
    val name: String? = null,
)

data class UserCurrency(
    val currency: String,
    val amount: Long,
)

data class UserGoal(
    val goalType: String,
    val label: String,
    val active: Boolean,
)

data class UserBuild(
    val id: String,
    val name: String,
    val snapshotId: String?,
)

data class CharacterSheet(
    val character: UserCharacter,
    val equipment: List<UserEquipment>,
    val weapons: List<UserWeapon>,
    val traits: List<UserTrait>,
    val runes: List<UserRune>,
    val skills: List<UserSkill>,
    val weaponMastery: List<UserWeaponMastery> = emptyList(),
    val buildLayers: List<UserBuildLayer> = emptyList(),
    val inventory: List<UserStack>,
    val materials: List<UserStack>,
    val currency: List<UserCurrency>,
    val cookingLevel: Long?,
    val goals: List<UserGoal>,
    val builds: List<UserBuild>,
)

data class CombatSkillTotal(
    val skillName: String?,
    val skillId: String?,
    val observedDamageSum: Long,
    val hits: Long,
    val critHits: Long = 0,
    val heavyHits: Long = 0,
    val damageShare: Double = 0.0,
) {
    val critRate: Double? get() = if (hits > 0) critHits.toDouble() / hits else null
    val heavyRate: Double? get() = if (hits > 0) heavyHits.toDouble() / hits else null
    val avgHit: Long? get() = if (hits > 0) observedDamageSum / hits else null
}

data class CombatTargetTotal(
    val targetName: String,
    val observedDamageSum: Long,
    val hits: Long,
    val damageShare: Double = 0.0,
)

data class CombatSessionSummary(
    val sessionId: String,
    val eventCount: Long,
    val observedDamageSum: Long,
    val logVersion: String?,
    val startedAt: String?,
    val endedAt: String?,
    val observedDps: Double?,
    val skillTotals: List<CombatSkillTotal> = emptyList(),
    val damageDoneHits: Long = 0,
    val primaryTarget: String? = null,
    val targets: List<CombatTargetTotal> = emptyList(),
    val critRate: Double? = null,
    val heavyRate: Double? = null,
    val durationSeconds: Double? = null,
    val sourcePath: String? = null,
)

/** Aggregate view across all imported sessions — observed only, no modeled DPS. */
data class CombatPortfolio(
    val sessionCount: Int,
    val totalDamage: Long,
    val avgDps: Double?,
    val bestDps: Double?,
    val bestSessionId: String?,
    val overallCritRate: Double?,
    val overallHeavyRate: Double?,
    val topSkillName: String?,
    val topSkillShare: Double?,
    val dpsTrend: CombatTrend,
    val insights: List<String> = emptyList(),
)

enum class CombatTrend { Up, Down, Flat, Unknown }

data class CombatSessionCompare(
    val baselineLabel: String,
    val currentLabel: String,
    val dpsDelta: Double?,
    val dpsDeltaPct: Double?,
    val damageDelta: Long,
    val skillShareShifts: List<CombatSkillShareShift>,
    val headline: String,
)

data class CombatSkillShareShift(
    val skillName: String,
    val baselineShare: Double,
    val currentShare: Double,
    val deltaPp: Double,
)

data class CatalogHit(
    val kind: String,
    val name: String?,
    val detail: String?,
    val sourceTable: String,
    val sourceRowId: String,
)

data class ResolvedLoadoutLine(
    val kind: String,
    val label: String?,
    val sourceTable: String?,
    val sourceRowId: String?,
    val extra: String?,
    val hit: CatalogHit?,
    val name: String? = null,
    val empty: Boolean = false,
    val stats: List<StatContribution> = emptyList(),
) {
    val unresolved: Boolean
        get() = !empty && hit == null && (
            !LoadoutKeys.isUnspecified(sourceRowId) || !name.isNullOrBlank()
        )
}

data class ResolvedCharacterSheet(
    val sheet: CharacterSheet,
    val snapshotId: String?,
    val snapshotBuild: String?,
    val lines: List<ResolvedLoadoutLine>,
    val weaponClass: WeaponClassMatch? = null,
) {
    val unresolvedCount: Int get() = lines.count { it.unresolved }
}

object LoadoutKeys {
    fun isUnspecified(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return true
        return trimmed.equals("row-id-from-warehouse", ignoreCase = true) ||
            trimmed.equals("replace-me", ignoreCase = true) ||
            trimmed.equals("None", ignoreCase = true)
    }
}

/**
 * The five allocated primary attributes from the in-game character window.
 *
 * [Points.allocated] is the typed sum. It is not Combat Power: `TLItemCombatPower`
 * weights gear, not Strength–Fortitude, and no warehouse aggregator maps these
 * five numbers onto the CP the client shows.
 */
object CharacterAttributes {
    data class Points(
        val strength: Long? = null,
        val dexterity: Long? = null,
        val wisdom: Long? = null,
        val perception: Long? = null,
        val fortitude: Long? = null,
    ) {
        private val values = listOf(strength, dexterity, wisdom, perception, fortitude)

        val allocated: Long?
            get() = if (values.all { it == null }) null else values.sumOf { it ?: 0L }
    }
}

object CharacterSlots {
    val body = listOf("head", "chest", "hands", "legs", "feet", "cloak")
    val accessories = listOf(
        "necklace",
        "earring",
        "earring2",
        "ring",
        "ring2",
        "bracelet",
        "belt",
        "brooch",
    )
    val weapons = listOf("main", "offhand")

    fun isWeapon(slot: String): Boolean = slot.lowercase() in weapons

    fun isBody(slot: String): Boolean = slot.lowercase() in body

    fun isAccessory(slot: String): Boolean = slot.lowercase() in accessories

    fun mergeEquipment(rows: List<UserEquipment>): List<UserEquipment> {
        val bySlot = rows.associateBy { it.slot.lowercase() }
        val known = body + accessories
        return known.map { slot ->
            bySlot[slot] ?: UserEquipment(slot, null, null, null, null)
        } + rows.filter { it.slot.lowercase() !in known }
    }

    fun mergeWeapons(rows: List<UserWeapon>): List<UserWeapon> {
        val bySlot = rows.associateBy { it.slot.lowercase() }
        return weapons.map { slot ->
            bySlot[slot] ?: UserWeapon(slot, null, null, null, null)
        } + rows.filter { it.slot.lowercase() !in weapons }
    }
}
