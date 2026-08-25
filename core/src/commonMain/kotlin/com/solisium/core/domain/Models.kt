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
)

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
    val server: String?,
    val notes: String?,
)

data class UserEquipment(
    val slot: String,
    val sourceTable: String?,
    val sourceRowId: String?,
    val itemLevel: Long?,
)

data class UserWeapon(
    val slot: String,
    val sourceTable: String?,
    val sourceRowId: String?,
    val itemLevel: Long?,
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
)

data class UserStack(
    val sourceTable: String?,
    val sourceRowId: String?,
    val quantity: Long,
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
) {
    val unresolved: Boolean
        get() = !sourceTable.isNullOrBlank() && !sourceRowId.isNullOrBlank() && hit == null
}

data class ResolvedCharacterSheet(
    val sheet: CharacterSheet,
    val snapshotId: String?,
    val snapshotBuild: String?,
    val lines: List<ResolvedLoadoutLine>,
) {
    val unresolvedCount: Int get() = lines.count { it.unresolved }
}
