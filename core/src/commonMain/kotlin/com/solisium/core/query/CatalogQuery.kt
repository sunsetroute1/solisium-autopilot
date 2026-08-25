package com.solisium.core.query

import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.domain.CatalogHit
import com.solisium.core.domain.CharacterSheet
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.CombatSkillTotal
import com.solisium.core.domain.GameAccessory
import com.solisium.core.domain.GameArmor
import com.solisium.core.domain.GameCurvePoint
import com.solisium.core.domain.GameItem
import com.solisium.core.domain.GameItemCurve
import com.solisium.core.domain.GameItemStat
import com.solisium.core.domain.GameMaterial
import com.solisium.core.domain.GameRecipe
import com.solisium.core.domain.GameRune
import com.solisium.core.domain.GameRuneSynergy
import com.solisium.core.domain.GameSkill
import com.solisium.core.domain.GameSkillEffect
import com.solisium.core.domain.GameSkillFormula
import com.solisium.core.domain.GameStat
import com.solisium.core.domain.GameTrait
import com.solisium.core.domain.GameWeapon
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.ResolvedLoadoutLine
import com.solisium.core.domain.UserBuild
import com.solisium.core.domain.UserCharacter
import com.solisium.core.domain.UserCurrency
import com.solisium.core.domain.UserEquipment
import com.solisium.core.domain.UserGoal
import com.solisium.core.domain.UserRune
import com.solisium.core.domain.UserSkill
import com.solisium.core.domain.UserStack
import com.solisium.core.domain.UserTrait
import com.solisium.core.domain.UserWeapon
import com.solisium.core.snapshot.SnapshotService

class CatalogQuery(private val db: SolisiumDatabase) {
    private val snapshots = SnapshotService(db)

    fun activeSnapshotId(): String? = snapshots.active()?.id

    fun snapshots() = snapshots.list()

    fun snapshotService(): SnapshotService = snapshots

    fun counts(snapshotId: String) = CatalogCounts(
        items = db.schemaQueries.countItems(snapshotId).executeAsOne(),
        runes = db.schemaQueries.countRunes(snapshotId).executeAsOne(),
        skills = db.schemaQueries.countSkills(snapshotId).executeAsOne(),
        recipes = db.schemaQueries.countRecipes(snapshotId).executeAsOne(),
        weapons = db.schemaQueries.countWeapons(snapshotId).executeAsOne(),
        armor = db.schemaQueries.countArmor(snapshotId).executeAsOne(),
        accessories = db.schemaQueries.countAccessories(snapshotId).executeAsOne(),
        effects = db.schemaQueries.countSkillEffects(snapshotId).executeAsOne(),
        synergies = db.schemaQueries.countRuneSynergies(snapshotId).executeAsOne(),
        stats = db.schemaQueries.countStats(snapshotId).executeAsOne(),
        traits = db.schemaQueries.countTraits(snapshotId).executeAsOne(),
        materials = db.schemaQueries.countMaterials(snapshotId).executeAsOne(),
        formulas = db.schemaQueries.countSkillFormulas(snapshotId).executeAsOne(),
        itemStats = db.schemaQueries.countItemStats(snapshotId).executeAsOne(),
        itemsWithStats = db.schemaQueries.countItemsWithStats(snapshotId).executeAsOne(),
        curvePoints = db.schemaQueries.countStatCurvePoints(snapshotId).executeAsOne(),
        itemCurveLinks = db.schemaQueries.countItemCurveLinks(snapshotId).executeAsOne(),
    )

    fun itemCurves(snapshotId: String, itemRowId: String): List<GameItemCurve> =
        db.schemaQueries.selectItemCurves(snapshotId, itemRowId).executeAsList().map {
            GameItemCurve(it.curve_kind, it.curve_source_table, it.curve_id, it.max_level)
        }

    /** Curve points for one item, already clipped to that item's own `max_level`. */
    fun itemCurvePoints(snapshotId: String, itemRowId: String): List<GameCurvePoint> =
        db.schemaQueries.selectItemCurvePoints(snapshotId, itemRowId).executeAsList().map {
            GameCurvePoint(
                it.curve_kind, it.curve_id, it.level, it.stat_key,
                it.stat_name, it.raw_value, it.confidence,
            )
        }

    fun itemStats(snapshotId: String, itemRowId: String): List<GameItemStat> =
        db.schemaQueries.selectItemStats(snapshotId, itemRowId).executeAsList().map {
            GameItemStat(
                snapshotId, it.source_table, it.source_row_id, it.stat_key,
                it.stat_name, it.raw_value, it.scope, it.confidence,
            )
        }

    fun allItemStats(snapshotId: String): List<GameItemStat> =
        db.schemaQueries.selectAllItemStats(snapshotId).executeAsList().map {
            GameItemStat(
                snapshotId, it.source_table, it.source_row_id, it.stat_key,
                it.stat_name, it.raw_value, it.scope, it.confidence,
            )
        }

    fun items(snapshotId: String, nameContains: String? = null): List<GameItem> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectItems(snapshotId).executeAsList().map {
                GameItem(snapshotId, it.source_table, it.source_row_id, it.name, it.grade, it.category)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchItems(snapshotId, pattern, pattern).executeAsList().map {
            GameItem(snapshotId, it.source_table, it.source_row_id, it.name, it.grade, it.category)
        }
    }

    fun runes(snapshotId: String, nameContains: String? = null): List<GameRune> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectRunes(snapshotId).executeAsList().map {
                GameRune(snapshotId, it.source_table, it.source_row_id, it.name, it.grade)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchRunes(snapshotId, pattern, pattern).executeAsList().map {
            GameRune(snapshotId, it.source_table, it.source_row_id, it.name, it.grade)
        }
    }

    fun skills(snapshotId: String, nameContains: String? = null): List<GameSkill> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectSkills(snapshotId).executeAsList().map {
                GameSkill(snapshotId, it.source_table, it.source_row_id, it.name, it.skill_type)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchSkills(snapshotId, pattern, pattern).executeAsList().map {
            GameSkill(snapshotId, it.source_table, it.source_row_id, it.name, it.skill_type)
        }
    }

    fun recipes(snapshotId: String, nameContains: String? = null): List<GameRecipe> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectRecipes(snapshotId).executeAsList().map {
                GameRecipe(snapshotId, it.source_table, it.source_row_id, it.name, it.recipe_kind)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchRecipes(snapshotId, pattern, pattern).executeAsList().map {
            GameRecipe(snapshotId, it.source_table, it.source_row_id, it.name, it.recipe_kind)
        }
    }

    fun weapons(snapshotId: String, nameContains: String? = null): List<GameWeapon> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectGameWeapons(snapshotId).executeAsList().map {
                GameWeapon(snapshotId, it.source_table, it.source_row_id, it.name, it.weapon_type)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchGameWeapons(snapshotId, pattern, pattern).executeAsList().map {
            GameWeapon(snapshotId, it.source_table, it.source_row_id, it.name, it.weapon_type)
        }
    }

    fun armor(snapshotId: String, nameContains: String? = null): List<GameArmor> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectArmor(snapshotId).executeAsList().map {
                GameArmor(snapshotId, it.source_table, it.source_row_id, it.name, it.slot, it.material)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchArmor(snapshotId, pattern, pattern).executeAsList().map {
            GameArmor(snapshotId, it.source_table, it.source_row_id, it.name, it.slot, it.material)
        }
    }

    fun accessories(snapshotId: String, nameContains: String? = null): List<GameAccessory> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectAccessories(snapshotId).executeAsList().map {
                GameAccessory(snapshotId, it.source_table, it.source_row_id, it.name, it.slot)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchAccessories(snapshotId, pattern, pattern).executeAsList().map {
            GameAccessory(snapshotId, it.source_table, it.source_row_id, it.name, it.slot)
        }
    }

    fun synergies(snapshotId: String, nameContains: String? = null): List<GameRuneSynergy> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectRuneSynergies(snapshotId).executeAsList().map {
                GameRuneSynergy(snapshotId, it.source_table, it.source_row_id, it.name)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchRuneSynergies(snapshotId, pattern, pattern).executeAsList().map {
            GameRuneSynergy(snapshotId, it.source_table, it.source_row_id, it.name)
        }
    }

    fun effects(snapshotId: String, nameContains: String? = null): List<GameSkillEffect> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectSkillEffects(snapshotId).executeAsList().map {
                GameSkillEffect(snapshotId, it.source_table, it.source_row_id, it.name, it.skill_source_row_id)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchSkillEffects(snapshotId, pattern, pattern).executeAsList().map {
            GameSkillEffect(snapshotId, it.source_table, it.source_row_id, it.name, it.skill_source_row_id)
        }
    }

    fun stats(snapshotId: String, nameContains: String? = null): List<GameStat> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectStats(snapshotId).executeAsList().map {
                GameStat(snapshotId, it.source_table, it.source_row_id, it.name)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchStats(snapshotId, pattern, pattern).executeAsList().map {
            GameStat(snapshotId, it.source_table, it.source_row_id, it.name)
        }
    }

    fun traits(snapshotId: String, nameContains: String? = null): List<GameTrait> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectGameTraits(snapshotId).executeAsList().map {
                GameTrait(snapshotId, it.source_table, it.source_row_id, it.name)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchGameTraits(snapshotId, pattern, pattern).executeAsList().map {
            GameTrait(snapshotId, it.source_table, it.source_row_id, it.name)
        }
    }

    fun materials(snapshotId: String, nameContains: String? = null): List<GameMaterial> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectGameMaterials(snapshotId).executeAsList().map {
                GameMaterial(snapshotId, it.source_table, it.source_row_id, it.name)
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchGameMaterials(snapshotId, pattern, pattern).executeAsList().map {
            GameMaterial(snapshotId, it.source_table, it.source_row_id, it.name)
        }
    }

    fun formulas(snapshotId: String, rowIdContains: String? = null): List<GameSkillFormula> {
        if (rowIdContains.isNullOrBlank()) {
            return db.schemaQueries.selectSkillFormulas(snapshotId).executeAsList().map {
                GameSkillFormula(
                    snapshotId, it.source_table, it.source_row_id,
                    it.skill_source_row_id, it.expression, it.confidence,
                )
            }
        }
        return db.schemaQueries.searchSkillFormulas(snapshotId, like(rowIdContains)).executeAsList().map {
            GameSkillFormula(
                snapshotId, it.source_table, it.source_row_id,
                it.skill_source_row_id, it.expression, it.confidence,
            )
        }
    }

    fun character(id: String): UserCharacter? {
        val row = db.schemaQueries.selectCharacter(id).executeAsOneOrNull() ?: return null
        return UserCharacter(
            id = row.id,
            name = row.name,
            level = row.level,
            combatPower = row.combat_power,
            server = row.server,
            notes = row.notes,
        )
    }

    fun characters(): List<UserCharacter> = db.schemaQueries.selectCharacters().executeAsList().map { row ->
        UserCharacter(
            id = row.id,
            name = row.name,
            level = row.level,
            combatPower = row.combat_power,
            server = row.server,
            notes = row.notes,
        )
    }

    fun characterSheet(id: String): CharacterSheet? {
        val character = character(id) ?: return null
        return CharacterSheet(
            character = character,
            equipment = db.schemaQueries.selectEquipment(id).executeAsList().map {
                UserEquipment(it.slot, it.source_table, it.source_row_id, it.item_level)
            },
            weapons = db.schemaQueries.selectWeapons(id).executeAsList().map {
                UserWeapon(it.slot, it.source_table, it.source_row_id, it.item_level)
            },
            traits = db.schemaQueries.selectTraits(id).executeAsList().map {
                UserTrait(it.source_table, it.source_row_id, it.rank)
            },
            runes = db.schemaQueries.selectCharacterRunes(id).executeAsList().map {
                UserRune(it.slot, it.source_table, it.source_row_id, it.rune_level)
            },
            skills = db.schemaQueries.selectCharacterSkills(id).executeAsList().map {
                UserSkill(it.source_table, it.source_row_id, it.loadout)
            },
            inventory = db.schemaQueries.selectInventory(id).executeAsList().map {
                UserStack(it.source_table, it.source_row_id, it.quantity)
            },
            materials = db.schemaQueries.selectMaterials(id).executeAsList().map {
                UserStack(it.source_table, it.source_row_id, it.quantity)
            },
            currency = db.schemaQueries.selectCurrency(id).executeAsList().map {
                UserCurrency(it.currency, it.amount)
            },
            cookingLevel = db.schemaQueries.selectCooking(id).executeAsOneOrNull()?.level,
            goals = db.schemaQueries.selectGoals(id).executeAsList().map {
                UserGoal(it.goal_type, it.label, it.active != 0L)
            },
            builds = db.schemaQueries.selectBuilds(id).executeAsList().map {
                UserBuild(it.id, it.name, it.snapshot_id)
            },
        )
    }

    fun lookup(snapshotId: String, sourceTable: String, sourceRowId: String): CatalogHit? {
        db.schemaQueries.selectWeaponByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("weapon", it.name, it.weapon_type, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectArmorByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("armor", it.name, it.slot, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectAccessoryByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("accessory", it.name, it.slot, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectItemByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("item", it.name, it.grade, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectRuneByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("rune", it.name, it.grade, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectRuneSynergyByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("synergy", it.name, null, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectSkillByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("skill", it.name, it.skill_type, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectSkillEffectByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("effect", it.name, it.skill_source_row_id, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectRecipeByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("recipe", it.name, it.recipe_kind, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectStatByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("stat", it.name, null, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectTraitByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("trait", it.name, null, it.source_table, it.source_row_id)
        }
        db.schemaQueries.selectSkillFormulaByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()?.let {
            return CatalogHit("formula", it.source_row_id, it.confidence, it.source_table, it.source_row_id)
        }
        return null
    }

    fun resolveCharacter(id: String, snapshotId: String?): ResolvedCharacterSheet? {
        val sheet = characterSheet(id) ?: return null
        val snapshot = snapshotId?.let { snapshots.get(it) }
        val lines = buildList {
            sheet.equipment.forEach {
                add(line("equipment", it.slot, it.sourceTable, it.sourceRowId, it.itemLevel?.let { level -> "ilvl=$level" }, snapshot?.id))
            }
            sheet.weapons.forEach {
                add(line("weapon", it.slot, it.sourceTable, it.sourceRowId, it.itemLevel?.let { level -> "ilvl=$level" }, snapshot?.id))
            }
            sheet.traits.forEach {
                add(line("trait", null, it.sourceTable, it.sourceRowId, it.rank?.let { rank -> "rank=$rank" }, snapshot?.id))
            }
            sheet.runes.forEach {
                add(line("rune", it.slot, it.sourceTable, it.sourceRowId, it.runeLevel?.let { level -> "level=$level" }, snapshot?.id))
            }
            sheet.skills.forEach {
                add(line("skill", it.loadout, it.sourceTable, it.sourceRowId, null, snapshot?.id))
            }
            sheet.inventory.forEach {
                add(line("inventory", null, it.sourceTable, it.sourceRowId, "qty=${it.quantity}", snapshot?.id))
            }
            sheet.materials.forEach {
                add(line("material", null, it.sourceTable, it.sourceRowId, "qty=${it.quantity}", snapshot?.id))
            }
        }
        return ResolvedCharacterSheet(
            sheet = sheet,
            snapshotId = snapshot?.id,
            snapshotBuild = snapshot?.gameBuild,
            lines = lines,
        )
    }

    private fun line(
        kind: String,
        label: String?,
        sourceTable: String?,
        sourceRowId: String?,
        extra: String?,
        snapshotId: String?,
    ): ResolvedLoadoutLine {
        val hit = if (snapshotId != null && !sourceTable.isNullOrBlank() && !sourceRowId.isNullOrBlank()) {
            lookup(snapshotId, sourceTable, sourceRowId)
        } else {
            null
        }
        return ResolvedLoadoutLine(kind, label, sourceTable, sourceRowId, extra, hit)
    }

    fun combatSessions(): List<CombatSessionSummary> =
        db.schemaQueries.selectCombatSessions().executeAsList().mapNotNull { combatSummary(it.id) }

    fun combatSummary(sessionId: String): CombatSessionSummary? {
        val session = db.schemaQueries.selectCombatSession(sessionId).executeAsOneOrNull() ?: return null
        val events = db.schemaQueries.countCombatEvents(sessionId).executeAsOne()
        val damage = db.schemaQueries.sumCombatDamage(sessionId).executeAsOne()
        val skillTotals = db.schemaQueries.selectCombatSkillTotals(sessionId).executeAsList().map {
            CombatSkillTotal(
                skillName = it.skill_name,
                skillId = it.skill_id,
                observedDamageSum = it.damage,
                hits = it.hits,
            )
        }
        return CombatSessionSummary(
            sessionId = sessionId,
            eventCount = events,
            observedDamageSum = damage,
            logVersion = session.log_version,
            startedAt = session.started_at,
            endedAt = session.ended_at,
            observedDps = observedDps(session.started_at, session.ended_at, damage),
            skillTotals = skillTotals,
        )
    }

    /**
     * `_` is kept because row ids such as `Common_Struggle_Duration` are mostly underscores;
     * it stays a single-character LIKE wildcard, which over-matches slightly but never drops hits.
     */
    private fun like(fragment: String): String = "%${fragment.replace("%", "")}%"

    companion object {
        internal fun observedDps(startedAt: String?, endedAt: String?, damage: Long): Double? {
            val start = parseEpochMillis(startedAt) ?: return null
            val end = parseEpochMillis(endedAt) ?: return null
            val seconds = (end - start) / 1000.0
            if (seconds <= 0.0 || !seconds.isFinite()) return null
            return damage / seconds
        }

        private fun parseEpochMillis(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            val normalized = value.replace(' ', 'T')
            val match = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?""").find(normalized)
                ?: return null
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            val hour = match.groupValues[4].toInt()
            val minute = match.groupValues[5].toInt()
            val second = match.groupValues[6].toInt()
            val fraction = match.groupValues[7]
            val millis = when {
                fraction.isEmpty() -> 0
                fraction.length >= 3 -> fraction.take(3).toInt()
                else -> fraction.padEnd(3, '0').toInt()
            }
            // Manual civil-from-days so commonMain does not need java.time.
            val days = daysFromCivil(year, month, day)
            return days * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1_000L + millis
        }

        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            var y = year.toLong()
            val m = month.toLong()
            val d = day.toLong()
            y -= if (m <= 2) 1 else 0
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = m + if (m > 2) -3 else 9
            val doy = (153 * mp + 2) / 5 + d - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146_097 + doe - 719_468
        }
    }
}
