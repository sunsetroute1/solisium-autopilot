package com.solisium.core.query

import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.domain.CatalogHit
import com.solisium.core.domain.CatalogItemDetail
import com.solisium.core.domain.CharacterSheet
import com.solisium.core.domain.CharacterSlots
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.CombatSkillTotal
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.GameAccessory
import com.solisium.core.domain.GameArmor
import com.solisium.core.domain.GameClass
import com.solisium.core.domain.GameCurvePoint
import com.solisium.core.domain.GameItem
import com.solisium.core.domain.GameItemCurve
import com.solisium.core.domain.GameItemStat
import com.solisium.core.domain.GameItemPower
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
import com.solisium.core.domain.LoadoutKeys
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.ResolvedLoadoutLine
import com.solisium.core.domain.StatContribution
import com.solisium.core.domain.UserBuild
import com.solisium.core.domain.UserBuildLayer
import com.solisium.core.domain.UserCharacter
import com.solisium.core.domain.UserCurrency
import com.solisium.core.domain.UserEquipment
import com.solisium.core.domain.UserGoal
import com.solisium.core.domain.UserRune
import com.solisium.core.domain.UserSkill
import com.solisium.core.domain.UserStack
import com.solisium.core.domain.UserTrait
import com.solisium.core.domain.UserWeapon
import com.solisium.core.domain.UserWeaponMastery
import com.solisium.core.domain.BuildClassOption
import com.solisium.core.domain.BuildLayer
import com.solisium.core.domain.ClassSource
import com.solisium.core.domain.DiscoveredInfluence
import com.solisium.core.source.InfluenceDiscovery
import com.solisium.core.source.SkillFamilyLookup
import com.solisium.core.domain.WeaponClassMatch
import com.solisium.core.domain.WeaponTypeLabel
import com.solisium.core.meta.CommunityWeaponClasses
import com.solisium.core.meta.TextNorm
import com.solisium.core.snapshot.SnapshotService
import com.solisium.core.source.EquipCategory

class CatalogQuery(private val db: SolisiumDatabase) {
    private val snapshots = SnapshotService(db)

    fun activeSnapshotId(): String? = snapshots.active()?.id

    fun snapshots() = snapshots.list()

    fun discoveredInfluences(snapshotId: String): List<DiscoveredInfluence> {
        val current = skills(snapshotId)
        val previous = snapshots()
            .filter { it.source == "tl_helper" && it.id != snapshotId }
            .maxByOrNull { it.extractedAt }
            ?.let { skills(it.id) }
            .orEmpty()
        return InfluenceDiscovery.discover(current, previous)
    }

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
        classes = db.schemaQueries.countClasses(snapshotId).executeAsOne(),
        combatPowerRows = db.schemaQueries.countCombatPower(snapshotId).executeAsOne(),
        itemPowerLinks = db.schemaQueries.countItemPower(snapshotId).executeAsOne(),
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

    /** Warehouse stats, curves, and combat-power link for one catalog row. */
    fun itemDetail(snapshotId: String, sourceTable: String, sourceRowId: String): CatalogItemDetail {
        val item = db.schemaQueries.selectItemByKey(snapshotId, sourceTable, sourceRowId).executeAsOneOrNull()
        val hit = lookup(snapshotId, sourceTable, sourceRowId)
        val meta = when (hit?.kind) {
            "weapon" -> hit.detail
            "armor", "accessory" -> hit.detail
            "item" -> DisplayName.prettyEnum(item?.category ?: hit.detail)
            else -> hit?.detail
        }
        return CatalogItemDetail(
            sourceTable = sourceTable,
            sourceRowId = sourceRowId,
            name = item?.name ?: hit?.name,
            grade = item?.grade ?: when (hit?.kind) {
                "item", "rune" -> hit.detail
                else -> null
            },
            meta = meta,
            category = item?.category,
            warehouseStats = itemStats(snapshotId, sourceRowId),
            curves = itemCurves(snapshotId, sourceRowId),
            curvePoints = itemCurvePoints(snapshotId, sourceRowId),
            combatPower = itemPowerByRow(snapshotId)[sourceRowId],
        )
    }

    fun allItemStats(snapshotId: String): List<GameItemStat> =
        db.schemaQueries.selectAllItemStats(snapshotId).executeAsList().map {
            GameItemStat(
                snapshotId, it.source_table, it.source_row_id, it.stat_key,
                it.stat_name, it.raw_value, it.scope, it.confidence,
            )
        }

    fun statKeys(snapshotId: String): List<Pair<String, String?>> =
        db.schemaQueries.selectDistinctItemStatKeys(snapshotId).executeAsList().map {
            it.stat_key to it.stat_name
        }

    fun itemPowerByRow(snapshotId: String): Map<String, GameItemPower> {
        val rows = db.schemaQueries.selectCombatPower(snapshotId).executeAsList()
            .associateBy { it.source_row_id }
        return db.schemaQueries.selectItemPower(snapshotId).executeAsList().mapNotNull { link ->
            val power = rows[link.power_source_row_id] ?: return@mapNotNull null
            link.item_source_row_id to GameItemPower(
                snapshotId = snapshotId,
                itemSourceTable = link.item_source_table,
                itemSourceRowId = link.item_source_row_id,
                powerSourceRowId = link.power_source_row_id,
                evidence = link.evidence,
                confidence = link.confidence,
                basePower = power.base_power,
                potentialPower = power.potential_power,
                payload = power.payload,
            )
        }.toMap()
    }

    fun items(snapshotId: String, nameContains: String? = null): List<GameItem> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectItems(snapshotId).executeAsList().map {
                GameItem(snapshotId, it.source_table, it.source_row_id, it.name, it.grade, it.category)
            }
        }
        return namedHits(nameContains) { token ->
            db.schemaQueries.searchItems(snapshotId, token, token).executeAsList().map {
                GameItem(snapshotId, it.source_table, it.source_row_id, it.name, it.grade, it.category)
            }
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
            return db.schemaQueries.selectSkills(snapshotId).executeAsList().map { row ->
                GameSkill(
                    snapshotId, row.source_table, row.source_row_id, row.name, row.skill_type,
                    row.family, row.weapon_token, row.family_confidence,
                )
            }
        }
        val pattern = like(nameContains)
        return db.schemaQueries.searchSkills(snapshotId, pattern, pattern).executeAsList().map { row ->
            GameSkill(
                snapshotId, row.source_table, row.source_row_id, row.name, row.skill_type,
                row.family, row.weapon_token, row.family_confidence,
            )
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
        return namedHits(nameContains) { token ->
            db.schemaQueries.searchGameWeapons(snapshotId, token, token).executeAsList().map {
                GameWeapon(snapshotId, it.source_table, it.source_row_id, it.name, it.weapon_type)
            }
        }
    }

    fun armor(snapshotId: String, nameContains: String? = null): List<GameArmor> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectArmor(snapshotId).executeAsList().map {
                GameArmor(snapshotId, it.source_table, it.source_row_id, it.name, it.slot, it.material)
            }
        }
        return namedHits(nameContains) { token ->
            db.schemaQueries.searchArmor(snapshotId, token, token).executeAsList().map {
                GameArmor(snapshotId, it.source_table, it.source_row_id, it.name, it.slot, it.material)
            }
        }
    }

    fun accessories(snapshotId: String, nameContains: String? = null): List<GameAccessory> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectAccessories(snapshotId).executeAsList().map {
                GameAccessory(snapshotId, it.source_table, it.source_row_id, it.name, it.slot)
            }
        }
        return namedHits(nameContains) { token ->
            db.schemaQueries.searchAccessories(snapshotId, token, token).executeAsList().map {
                GameAccessory(snapshotId, it.source_table, it.source_row_id, it.name, it.slot)
            }
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
            gearScore = row.gear_score,
            server = row.server,
            notes = row.notes,
            strength = row.strength,
            dexterity = row.dexterity,
            wisdom = row.wisdom,
            perception = row.perception,
            fortitude = row.fortitude,
            className = row.class_name,
            classSource = row.class_source,
        )
    }

    fun characters(): List<UserCharacter> = db.schemaQueries.selectCharacters().executeAsList().map { row ->
        UserCharacter(
            id = row.id,
            name = row.name,
            level = row.level,
            combatPower = row.combat_power,
            gearScore = row.gear_score,
            server = row.server,
            notes = row.notes,
            strength = row.strength,
            dexterity = row.dexterity,
            wisdom = row.wisdom,
            perception = row.perception,
            fortitude = row.fortitude,
            className = row.class_name,
            classSource = row.class_source,
        )
    }

    fun characterSheet(id: String): CharacterSheet? {
        val character = character(id) ?: return null
        return CharacterSheet(
            character = character,
            equipment = db.schemaQueries.selectEquipment(id).executeAsList().map {
                UserEquipment(it.slot, it.source_table, it.source_row_id, it.item_level, it.name)
            },
            weapons = db.schemaQueries.selectWeapons(id).executeAsList().map {
                UserWeapon(it.slot, it.source_table, it.source_row_id, it.item_level, it.name)
            },
            traits = db.schemaQueries.selectTraits(id).executeAsList().map {
                UserTrait(it.source_table, it.source_row_id, it.rank)
            },
            runes = db.schemaQueries.selectCharacterRunes(id).executeAsList().map {
                UserRune(it.slot, it.source_table, it.source_row_id, it.rune_level)
            },
            skills = db.schemaQueries.selectCharacterSkills(id).executeAsList().map {
                UserSkill(it.source_table, it.source_row_id, it.loadout, it.name, it.skill_level, it.family)
            },
            inventory = db.schemaQueries.selectInventory(id).executeAsList().map {
                UserStack(it.source_table, it.source_row_id, it.quantity, it.name)
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
            weaponMastery = db.schemaQueries.selectWeaponMastery(id).executeAsList().map {
                UserWeaponMastery(it.weapon, it.level)
            },
            buildLayers = db.schemaQueries.selectBuildLayers(id).executeAsList().map {
                UserBuildLayer(it.layer, it.slot, it.source_table, it.source_row_id, it.name, it.level)
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
            return CatalogHit("skill", it.name, it.family ?: it.skill_type, it.source_table, it.source_row_id)
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
            CharacterSlots.mergeEquipment(sheet.equipment).forEach {
                add(line("equipment", it.slot, it.sourceTable, it.sourceRowId, it.itemLevel?.let { level -> "ilvl=$level" }, snapshot?.id, it.name))
            }
            CharacterSlots.mergeWeapons(sheet.weapons).forEach {
                add(line("weapon", it.slot, it.sourceTable, it.sourceRowId, it.itemLevel?.let { level -> "ilvl=$level" }, snapshot?.id, it.name))
            }
            sheet.traits.forEach {
                add(line("trait", null, it.sourceTable, it.sourceRowId, it.rank?.let { rank -> "rank=$rank" }, snapshot?.id, null))
            }
            sheet.runes.forEach {
                add(line("rune", it.slot, it.sourceTable, it.sourceRowId, it.runeLevel?.let { level -> "level=$level" }, snapshot?.id, null))
            }
            sheet.skills.forEach {
                val extra = listOfNotNull(it.family, it.skillLevel?.let { lv -> "lv=$lv" }).joinToString(" ").ifBlank { null }
                add(line("skill", it.loadout ?: it.family, it.sourceTable, it.sourceRowId, extra, snapshot?.id, it.name))
            }
            sheet.weaponMastery.forEach {
                add(line("weapon_mastery", it.weapon, null, null, it.level?.let { lv -> "level=$lv" }, snapshot?.id, it.weapon))
            }
            sheet.buildLayers.forEach {
                add(line(it.layer, it.slot, it.sourceTable, it.sourceRowId, it.level?.let { lv -> "lv=$lv" }, snapshot?.id, it.name))
            }
            sheet.inventory.forEach {
                add(line("inventory", null, it.sourceTable, it.sourceRowId, "qty=${it.quantity}", snapshot?.id, it.name))
            }
            sheet.materials.forEach {
                add(line("material", null, it.sourceTable, it.sourceRowId, "qty=${it.quantity}", snapshot?.id, null))
            }
        }
        val suggested = suggestClass(
            snapshot?.id,
            lines.firstOrNull { it.kind == "weapon" && it.label == "main" },
            lines.firstOrNull { it.kind == "weapon" && it.label == "offhand" },
        )
        return ResolvedCharacterSheet(
            sheet = sheet,
            snapshotId = snapshot?.id,
            snapshotBuild = snapshot?.gameBuild,
            lines = lines,
            weaponClass = WeaponClassResolver.applyStored(
                sheet.character.className,
                sheet.character.classSource,
                suggested,
            ),
        )
    }

    fun classes(snapshotId: String, nameContains: String? = null): List<GameClass> {
        if (nameContains.isNullOrBlank()) {
            return db.schemaQueries.selectClasses(snapshotId).executeAsList().map {
                GameClass(snapshotId, it.source_table, it.source_row_id, it.name, it.weapon_a, it.weapon_b)
            }
        }
        val token = like(nameContains)
        return db.schemaQueries.searchClasses(snapshotId, token, token).executeAsList().map {
            GameClass(snapshotId, it.source_table, it.source_row_id, it.name, it.weapon_a, it.weapon_b)
        }
    }

    fun suggestClass(snapshotId: String?, mainName: String?, offhandName: String?): WeaponClassMatch {
        val extracted = snapshotId?.let { classes(it) }.orEmpty()
        return WeaponClassResolver.resolve(
            extracted,
            weaponTypeOf(snapshotId, mainName),
            weaponTypeOf(snapshotId, offhandName),
        )
    }

    fun knownClassNames(snapshotId: String?): List<String> {
        return buildClassOptions(snapshotId).map { it.name }.distinct().sorted()
    }

    fun buildClassOptions(snapshotId: String?): List<BuildClassOption> {
        val byKey = linkedMapOf<String, BuildClassOption>()
        snapshotId?.let { id ->
            classes(id).forEach { row ->
                val name = DisplayName.of(row.name) ?: return@forEach
                val canonical = WeaponTypeLabel.canonical(row.weaponA, row.weaponB) ?: return@forEach
                val option = BuildClassOption(name, canonical.first, canonical.second, ClassSource.EXTRACTED)
                byKey[option.key] = option
            }
        }
        CommunityWeaponClasses.pairs().forEach { pair ->
            byKey.putIfAbsent(
                pair.key,
                BuildClassOption(pair.name, pair.weaponA, pair.weaponB, ClassSource.COMMUNITY),
            )
        }
        return byKey.values.sortedBy { it.name.lowercase() }
    }

    fun findBuildClass(
        snapshotId: String?,
        name: String? = null,
        key: String? = null,
        match: WeaponClassMatch? = null,
    ): BuildClassOption? {
        val options = buildClassOptions(snapshotId)
        if (!key.isNullOrBlank()) {
            options.firstOrNull { it.key == key }?.let { return it }
        }
        val pairKey = WeaponTypeLabel.pairKey(match?.weaponA, match?.weaponB)
        if (pairKey != null) {
            options.firstOrNull { it.key == pairKey }?.let { return it }
        }
        val title = name ?: match?.name
        if (!title.isNullOrBlank()) {
            options.firstOrNull { WeaponClassResolver.sameTitle(it.name, title) }?.let { return it }
        }
        return null
    }

    private fun suggestClass(
        snapshotId: String?,
        main: ResolvedLoadoutLine?,
        offhand: ResolvedLoadoutLine?,
    ): WeaponClassMatch {
        val extracted = snapshotId?.let { classes(it) }.orEmpty()
        return WeaponClassResolver.resolve(
            extracted,
            weaponTypeOf(main, snapshotId),
            weaponTypeOf(offhand, snapshotId),
        )
    }

    private fun weaponTypeOf(line: ResolvedLoadoutLine?, snapshotId: String?): String? {
        if (line == null || line.empty) return null
        line.hit?.takeIf { it.kind == "weapon" }?.detail?.let { return it }
        return weaponTypeOf(snapshotId, DisplayName.of(line.hit?.name) ?: line.name)
    }

    private fun weaponTypeOf(snapshotId: String?, name: String?): String? {
        if (snapshotId == null || name.isNullOrBlank()) return null
        val hit = findByName(snapshotId, name) ?: return null
        if (hit.kind == "weapon") return hit.detail
        return db.schemaQueries.selectWeaponByKey(snapshotId, "TLItemEquip", hit.sourceRowId)
            .executeAsOneOrNull()?.weapon_type
    }

    fun findByName(snapshotId: String, raw: String): CatalogHit? {
        val name = DisplayName.of(raw) ?: return null
        val hits = buildList {
            weapons(snapshotId, name).forEach {
                add(CatalogHit("weapon", it.name, it.weaponType, it.sourceTable, it.sourceRowId))
            }
            armor(snapshotId, name).forEach {
                add(CatalogHit("armor", it.name, it.slot, it.sourceTable, it.sourceRowId))
            }
            accessories(snapshotId, name).forEach {
                add(CatalogHit("accessory", it.name, it.slot, it.sourceTable, it.sourceRowId))
            }
            items(snapshotId, name).forEach { item ->
                if (DisplayName.isItemLooks(item.sourceTable)) {
                    add(CatalogHit("item", item.name, item.grade, item.sourceTable, item.sourceRowId))
                }
            }
        }
        val exact = hits.filter { TextNorm.fold(it.name ?: "") == TextNorm.fold(name) }
        val pool = exact.ifEmpty { hits.filter { TextNorm.nearMatch(it.name, name) } }
        return pool.minWithOrNull(
            compareBy<CatalogHit> { if (DisplayName.isItemLooks(it.sourceTable)) 0 else 1 }
                .thenBy { it.name ?: it.sourceRowId },
        )
    }

    private fun findSkillByName(snapshotId: String, raw: String): CatalogHit? {
        val name = DisplayName.of(raw) ?: return null
        val hits = skills(snapshotId, name).mapNotNull { skill ->
            val label = DisplayName.of(skill.name, skill.sourceRowId) ?: return@mapNotNull null
            CatalogHit("skill", label, skill.family ?: skill.skillType, skill.sourceTable, skill.sourceRowId)
        }
        val exact = hits.filter { TextNorm.fold(it.name ?: "") == TextNorm.fold(name) }
        return (exact.ifEmpty { hits.filter { TextNorm.nearMatch(it.name, name) } }).minByOrNull { it.name ?: it.sourceRowId }
    }

    /**
     * Named gear for the Character sheet typeahead. [slot] is a paper-doll slot
     * (`main`, `head`, …) or null for the bag. Queries shorter than two characters
     * return nothing so the catalog is not dumped into the picker.
     */
    fun suggestGear(snapshotId: String, query: String, slot: String? = null, limit: Int = 16): List<CatalogHit> {
        val raw = query.trim()
        if (raw.length < 2) return emptyList()
        val wanted = slot?.lowercase()
        val hits = buildList {
            val wantWeapons = wanted == null || CharacterSlots.isWeapon(wanted)
            val wantArmor = wanted == null || CharacterSlots.isBody(wanted)
            val wantAccessories = wanted == null || CharacterSlots.isAccessory(wanted)
            if (wantWeapons) {
                weapons(snapshotId, raw).forEach {
                    val name = DisplayName.of(it.name, it.sourceRowId) ?: return@forEach
                    add(CatalogHit("weapon", name, it.weaponType, it.sourceTable, it.sourceRowId))
                }
            }
            if (wantArmor) {
                armor(snapshotId, raw).forEach {
                    if (wanted != null && !slotTokenMatches(it.slot, wanted)) return@forEach
                    val name = DisplayName.of(it.name, it.sourceRowId) ?: return@forEach
                    add(CatalogHit("armor", name, it.slot, it.sourceTable, it.sourceRowId))
                }
            }
            if (wantAccessories) {
                accessories(snapshotId, raw).forEach {
                    if (wanted != null && !slotTokenMatches(it.slot, wanted)) return@forEach
                    val name = DisplayName.of(it.name, it.sourceRowId) ?: return@forEach
                    add(CatalogHit("accessory", name, it.slot, it.sourceTable, it.sourceRowId))
                }
            }
            items(snapshotId, raw).forEach { item ->
                if (!DisplayName.isItemLooks(item.sourceTable)) return@forEach
                if (wanted != null && !itemFitsSlot(item.category, wanted)) return@forEach
                val name = DisplayName.of(item.name, item.sourceRowId) ?: return@forEach
                add(CatalogHit("item", name, item.grade, item.sourceTable, item.sourceRowId))
            }
        }
        return hits
            .sortedWith(
                compareBy<CatalogHit> { suggestionRank(raw, it.name) }
                    .thenBy { suggestionPenalty(it) }
                    .thenBy { if (DisplayName.isItemLooks(it.sourceTable)) 0 else 1 }
                    .thenBy { it.name ?: it.sourceRowId },
            )
            .distinctBy { TextNorm.fold(it.name ?: it.sourceRowId) }
            .take(limit)
    }

    /**
     * Typeahead for skills-screen layers. Queries shorter than two characters
     * return nothing. Skill-core items are perk rows, not TLSkill.
     */
    fun suggestBuildLayer(snapshotId: String, query: String, layerId: String?, limit: Int = 16): List<CatalogHit> {
        val raw = query.trim()
        if (raw.length < 2) return emptyList()
        val layer = BuildLayer.fromId(layerId)
        val family = layer?.catalogFamily
        val prefix = layerId?.removePrefix("prefix:")?.takeIf { layerId.startsWith("prefix:") }
        val hits = buildList {
            if (layer == BuildLayer.SkillCore) {
                items(snapshotId, raw).forEach { item ->
                    if (!SkillFamilyLookup.isSkillCoreItem(item.sourceRowId, item.name)) return@forEach
                    val name = DisplayName.of(item.name, item.sourceRowId) ?: return@forEach
                    add(CatalogHit("item", name, item.grade, item.sourceTable, item.sourceRowId))
                }
            }
            skills(snapshotId, raw).forEach { skill ->
                if (prefix != null && SkillFamilyLookup.prefixGroup(skill.sourceRowId) != prefix) return@forEach
                if (prefix == null && family != null && skill.family != family.id) return@forEach
                val name = DisplayName.of(skill.name, skill.sourceRowId) ?: return@forEach
                add(CatalogHit("skill", name, skill.family ?: skill.skillType, skill.sourceTable, skill.sourceRowId))
            }
        }
        return hits
            .sortedWith(
                compareBy<CatalogHit> { suggestionRank(raw, it.name) }
                    .thenBy { it.name ?: it.sourceRowId },
            )
            .distinctBy { TextNorm.fold(it.name ?: it.sourceRowId) }
            .take(limit)
    }

    private fun suggestionRank(query: String, name: String?): Int {
        val foldedQuery = TextNorm.fold(query)
        val folded = TextNorm.fold(name ?: "")
        return when {
            folded == foldedQuery -> 0
            folded.startsWith(foldedQuery) -> 1
            folded.split(" ").any { it.startsWith(foldedQuery) } -> 2
            TextNorm.likelySame(name, query) -> 3
            firstWordsNear(foldedQuery, folded) -> 3
            else -> 4
        }
    }

    private fun firstWordsNear(foldedQuery: String, foldedName: String): Boolean {
        val queryWord = foldedQuery.split(" ").firstOrNull().orEmpty()
        val nameWord = foldedName.split(" ").firstOrNull().orEmpty()
        return queryWord.length >= 6 && nameWord.length >= 6 &&
            TextNorm.editDistanceAtMost(queryWord, nameWord, 1)
    }

    private fun suggestionPenalty(hit: CatalogHit): Int {
        val folded = TextNorm.fold(hit.name ?: "")
        val tokens = folded.split(" ").toSet()
        return if (tokens.any { it in setOf("chest", "shard", "package", "extract") }) 1 else 0
    }

    private fun itemFitsSlot(category: String?, wanted: String): Boolean {
        val kind = EquipCategory.kind(category)
        return when {
            CharacterSlots.isWeapon(wanted) -> kind == EquipCategory.Kind.WEAPON
            CharacterSlots.isBody(wanted) -> kind == EquipCategory.Kind.ARMOR && slotTokenMatches(category, wanted)
            CharacterSlots.isAccessory(wanted) -> kind == EquipCategory.Kind.ACCESSORY && slotTokenMatches(category, wanted)
            else -> false
        }
    }

    private fun slotTokenMatches(stored: String?, wanted: String): Boolean {
        val token = DisplayName.prettyEnum(stored)?.lowercase()
            ?: stored?.substringAfterLast("::")?.removePrefix("k")?.lowercase()
            ?: return false
        val need = wanted.lowercase().removeSuffix("2")
        return when (need) {
            "ring" -> token == "ring" || (token.contains("ring") && !token.contains("ear"))
            "earring" -> token.contains("earring") || token == "earring"
            else -> token == need || token.contains(need)
        }
    }

    private fun line(
        kind: String,
        label: String?,
        sourceTable: String?,
        sourceRowId: String?,
        extra: String?,
        snapshotId: String?,
        name: String?,
    ): ResolvedLoadoutLine {
        val rowId = sourceRowId.takeUnless { LoadoutKeys.isUnspecified(it) }
        val table = sourceTable.takeUnless { it.isNullOrBlank() }
        val skillLike = kind == "skill" || BuildLayer.fromId(kind) != null
        fun namedHit(raw: String?): CatalogHit? {
            if (raw.isNullOrBlank() || snapshotId == null) return null
            return if (skillLike) findSkillByName(snapshotId, raw) ?: findByName(snapshotId, raw)
            else findByName(snapshotId, raw)
        }
        val empty = rowId == null && name.isNullOrBlank()
        val hit = when {
            empty || snapshotId == null -> null
            table != null && rowId != null -> lookup(snapshotId, table, rowId)
                ?: namedHit(name)
                ?: namedHit(rowId)
            else -> namedHit(name) ?: namedHit(rowId)
        }
        val stats = if (snapshotId != null && hit != null) {
            itemStats(snapshotId, hit.sourceRowId)
                .filter { it.scope == "main_base" && it.rawValue != 0L }
                .sortedByDescending { it.rawValue }
                .take(4)
                .map { StatContribution(it.statKey, it.rawValue, it.scope) }
        } else {
            emptyList()
        }
        return ResolvedLoadoutLine(
            kind = kind,
            label = label,
            sourceTable = table ?: hit?.sourceTable,
            sourceRowId = rowId ?: hit?.sourceRowId,
            extra = extra,
            hit = hit,
            name = name,
            empty = empty,
            stats = stats,
        )
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

    /**
     * Exact contains plus a prefix/suffix pattern so a one-letter slip such as
     * `Calenthia` still reaches `Calanthia's Visage`.
     */
    private fun likePatterns(fragment: String): List<String> {
        val exact = like(fragment)
        val folded = TextNorm.fold(fragment)
        if (folded.length < 6) return listOf(exact)
        val fuzzy = "%${folded.take(3)}%${folded.takeLast(4)}%"
        return if (fuzzy == exact) listOf(exact) else listOf(exact, fuzzy)
    }

    private fun <T> namedHits(fragment: String, search: (String) -> List<T>): List<T> {
        val seen = mutableSetOf<T>()
        val out = mutableListOf<T>()
        for (pattern in likePatterns(fragment)) {
            for (row in search(pattern)) {
                if (seen.add(row)) out.add(row)
            }
        }
        return out
    }

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
