package com.solisium.core.source

import com.solisium.core.db.SchemaVersion
import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.DisplayName
import com.solisium.core.json.JsonParseException
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import com.solisium.core.platform.randomUuid
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * Maps a TL-Helper warehouse `records` table into Solisium entities.
 * Does not use the warehouse as the application schema.
 */
class TLHelperDataSource(
    private val locator: WarehouseLocator = WarehouseLocator(),
) : DataSource {
    override val id: String = "tl_helper"

    override fun probe(): SourceCapability {
        val found = locator.find()
        return SourceCapability(
            id = id,
            available = found != null,
            provides = listOf(
                "game_item",
                "game_weapon",
                "game_armor",
                "game_accessory",
                "game_trait",
                "game_rune",
                "game_rune_synergy",
                "game_skill",
                "game_skill_effect",
                "game_skill_formula",
                "game_recipe",
                "game_material",
                "game_stat",
                "game_item_stat",
                "game_stat_curve",
                "game_item_curve",
                "game_class",
                "game_combat_power",
                "game_boss",
                "game_skill",
                "dataset_snapshot",
            ),
            notes = locator.describe(),
        )
    }

    override fun importInto(db: SolisiumDatabase, request: ImportRequest): ImportReceipt {
        val path = request.path ?: locator.find()?.toString()
            ?: throw IllegalArgumentException(locator.describe())
        val warehouse = Path.of(path)
        if (!Files.isRegularFile(warehouse)) {
            throw IllegalArgumentException("warehouse not found: $path")
        }
        val sourceHash = sha256File(warehouse)

        reportProgress(request, "Reading warehouse", 0, 1)
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            assertRecordsTable(connection)
            val rows = loadRecords(connection)
            reportProgress(request, "Reading warehouse", 1, 1)
            if (rows.isEmpty()) {
                return ImportReceipt(
                    source = id,
                    recordsImported = 0,
                    recordsSkipped = 0,
                    warnings = listOf("warehouse records table is empty"),
                )
            }
            val builds = rows.map { it.gameBuild }.filter { it.isNotBlank() }.distinct()
            val versions = rows.map { it.gameVersion }.filter { it.isNotBlank() }.distinct()
            val decoders = rows.map { it.decoderVersion }.filter { it.isNotBlank() }.distinct()
            val warnings = mutableListOf<String>()
            if (builds.size > 1) warnings.add("mixed game_build values: ${builds.joinToString()}")
            val snapshotId = randomUuid()
            val nameIndex = buildNameIndex(rows)
            val equipByRowId = rows.filter { it.tableName == "TLItemEquip" }.associateBy { it.rowId }
            val itemsByRowId = rows.filter { it.recordType.equals("item", ignoreCase = true) }
                .groupBy { it.rowId }
            var imported = 0
            var skipped = 0
            // The snapshot row and its game rows must commit together. Activating first
            // and failing later would leave an empty snapshot active with the previous
            // one already deactivated.
            val skippedInfluenceTables = linkedSetOf<String>()
            db.transaction {
                if (request.activate) {
                    db.schemaQueries.clearActiveSnapshots()
                }
                db.schemaQueries.insertSnapshot(
                    id = snapshotId,
                    source = id,
                    extracted_at = Instant.now().toString(),
                    game_build = builds.firstOrNull() ?: "unknown",
                    game_version = versions.firstOrNull() ?: "unknown",
                    schema_version = SchemaVersion.CURRENT.toLong(),
                    source_path = warehouse.toAbsolutePath().toString(),
                    source_hash = sourceHash,
                    decoder_version = decoders.firstOrNull(),
                    active = if (request.activate) 1L else 0L,
                )
                val rowTotal = rows.size.toLong()
                rows.forEachIndexed { index, row ->
                    if (index == 0 || index == rows.lastIndex || index % 250 == 0) {
                        reportProgress(request, "Mapping game data", index + 1L, rowTotal)
                    }
                    if (mapRow(db, snapshotId, row, equipByRowId, nameIndex)) {
                        imported++
                    } else {
                        skipped++
                        if (looksLikeUnmappedBuildTable(row.tableName)) {
                            skippedInfluenceTables += row.tableName
                        }
                    }
                }
                reportProgress(request, "Linking materials", 0, 1)
                val materials = mapMaterials(db, snapshotId, rows, itemsByRowId, nameIndex)
                imported += materials.first
                if (materials.second > 0) {
                    warnings.add(
                        "${materials.second} ingredient reference(s) did not resolve to a known item row; skipped",
                    )
                }
                reportProgress(request, "Mapping stat curves", 0, 1)
                imported += mapStatCurves(db, snapshotId, rows)
                reportProgress(request, "Mapping item stats", 0, 1)
                val itemStats = mapItemStats(db, snapshotId, rows, itemsByRowId)
                imported += itemStats.imported
                if (itemStats.unlinkedStatRows > 0) {
                    warnings.add(
                        "${itemStats.unlinkedStatRows} TLItemStats row(s) have no matching item row; skipped",
                    )
                }
                if (itemStats.unresolvedPointers > 0) {
                    warnings.add(
                        "${itemStats.unresolvedPointers} item stat pointer(s) had no value row; skipped",
                    )
                }
                reportProgress(request, "Linking combat power", 0, 1)
                val powerLinks = mapItemPowerLinks(db, snapshotId, rows)
                reportProgress(request, "Mapping monster drops", 0, 1)
                val extractedDrops = mapExtractedDrops(db, snapshotId, rows, nameIndex)
                imported += extractedDrops.dropRows
                reportProgress(request, "Finishing import", 1, 1)
                imported += powerLinks.mapped
                if (powerLinks.tablePresent) {
                    warnings.add(
                        "${powerLinks.mapped} item(s) mapped to TLItemCombatPower weights " +
                            "(derived; not live character CP). ${powerLinks.unresolved} equip row(s) stayed unresolved.",
                    )
                }
                if (skippedInfluenceTables.isNotEmpty()) {
                    warnings.add(
                        "warehouse tables present but unmapped (candidate build influences, not CP): " +
                            skippedInfluenceTables.sorted().joinToString(),
                    )
                }
                val rewardProfiles = rows.count { it.tableName == "TLRewardNpcFoItem" }
                val hasLotteryUnits = rows.any { it.tableName == "TLItemLotteryUnit" }
                if (extractedDrops.dropRows > 0) {
                    warnings.add(
                        "${extractedDrops.dropRows} extracted drop row(s) from TLItemLotteryUnit " +
                            "for ${extractedDrops.profilesMapped} monster profile(s).",
                    )
                }
                if (extractedDrops.unresolvedGroups > 0) {
                    warnings.add(
                        "${extractedDrops.unresolvedGroups} lottery group pointer(s) did not resolve to a lottery unit.",
                    )
                }
                if (rewardProfiles > 0 && !hasLotteryUnits) {
                    warnings.add(
                        "$rewardProfiles monster reward profile(s) imported; exact drop weights need " +
                            "TLItemLotteryUnit (and TLItemLotteryPublicGroup when groups differ from unit ids) " +
                            "in the warehouse. Questlog sync fills community rates until then.",
                    )
                }
            }
            return ImportReceipt(
                source = id,
                snapshotId = snapshotId,
                recordsImported = imported,
                recordsSkipped = skipped,
                warnings = warnings,
            )
        }
    }

    private fun mapRow(
        db: SolisiumDatabase,
        snapshotId: String,
        row: WarehouseRecord,
        equipByRowId: Map<String, WarehouseRecord>,
        nameIndex: Map<String, String>,
    ): Boolean {
        val json = parseJson(row.rawJson)
        when (row.tableName) {
            "TLRuneSynergy" -> {
                db.schemaQueries.insertGameRuneSynergy(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    name = DisplayName.of(row.name, row.rowId) ?: nameIndex[row.rowId],
                )
                return true
            }
            "TLRuneGrowth" -> return false
            "TLStats" -> {
                db.schemaQueries.insertGameStat(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    name = DisplayName.of(row.name, row.rowId)
                        ?: DisplayName.prettyEnum(json.str("stat_enum")),
                )
                return true
            }
            "TLItemTraits" -> {
                db.schemaQueries.insertGameTrait(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    name = DisplayName.of(row.name, row.rowId) ?: nameIndex[row.rowId],
                )
                return true
            }
            "TLFormulaParameterNew" -> {
                db.schemaQueries.insertGameSkillFormula(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    skill_source_row_id = null,
                    expression = formulaTypes(json),
                    confidence = "extracted",
                )
                return true
            }
            "TLItemCombatPower" -> {
                db.schemaQueries.insertGameCombatPower(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    category = present(json.str("Category")),
                    base_power = json.long("BaseCombatPower") ?: 0L,
                    potential_power = json.long("ItemPotentialCombatPower"),
                    payload = row.rawJson,
                    confidence = "extracted",
                )
                return true
            }
            "TLRewardNpcFoItem" -> {
                db.schemaQueries.insertGameBoss(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    name = RewardRowIdParser.prettyName(row.rowId),
                )
                return true
            }
        }
        if (WeaponClassMapper.considers(row.tableName)) {
            val parsed = WeaponClassMapper.parse(row.tableName, row.rowId, row.name, json)
            if (parsed != null) {
                db.schemaQueries.insertGameClass(
                    snapshot_id = snapshotId,
                    source_table = parsed.sourceTable,
                    source_row_id = parsed.sourceRowId,
                    name = parsed.name,
                    weapon_a = parsed.weaponA,
                    weapon_b = parsed.weaponB,
                )
                return true
            }
            return false
        }
        return when (row.recordType.lowercase()) {
            "item" -> {
                val equipJson = if (row.tableName == "TLItemEquip") json else parseJson(equipByRowId[row.rowId]?.rawJson)
                val grade = present(json.strAny("item_grade", "grade"))
                    ?: present(equipJson.strAny("item_grade", "grade"))
                val category = present(json.str("equip_category"))
                    ?: present(equipJson.str("equip_category"))
                    ?: row.tableName
                val icon = present(json.obj("IconPath")?.str("assetPath"))
                    ?: present(json.strAny("HighResIconPath", "icon", "icon_asset_path"))
                val name = DisplayName.of(row.name, row.rowId) ?: nameIndex[row.rowId]
                db.schemaQueries.insertGameItem(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    name = name,
                    grade = grade,
                    category = category,
                    icon_path = icon,
                )
                if (row.tableName == "TLItemEquip") {
                    val token = EquipCategory.token(json.str("equip_category"))
                    when (EquipCategory.kind(json.str("equip_category"))) {
                        EquipCategory.Kind.WEAPON -> db.schemaQueries.insertGameWeapon(
                            snapshot_id = snapshotId,
                            source_table = row.tableName,
                            source_row_id = row.rowId,
                            item_id = null,
                            name = name,
                            weapon_type = token,
                        )
                        EquipCategory.Kind.ARMOR -> db.schemaQueries.insertGameArmor(
                            snapshot_id = snapshotId,
                            source_table = row.tableName,
                            source_row_id = row.rowId,
                            item_id = null,
                            name = name,
                            slot = token,
                            material = present(json.str("Material")),
                        )
                        EquipCategory.Kind.ACCESSORY -> db.schemaQueries.insertGameAccessory(
                            snapshot_id = snapshotId,
                            source_table = row.tableName,
                            source_row_id = row.rowId,
                            item_id = null,
                            name = name,
                            slot = token,
                        )
                        null -> Unit
                    }
                }
                true
            }
            "rune" -> {
                db.schemaQueries.insertGameRune(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    name = DisplayName.of(row.name, row.rowId)
                        ?: DisplayName.fromEnums(json.str("RuneType"), json.str("RuneTargetCategory")),
                    grade = present(json.strAny("grade")),
                )
                true
            }
            "skill" -> {
                val skillType = present(json.strAny("skill_category", "skillType", "skill_type"))
                val classified = SkillFamilyLookup.classify(row.rowId, skillType)
                db.schemaQueries.insertGameSkill(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    name = DisplayName.of(row.name, row.rowId) ?: nameIndex[row.rowId],
                    skill_type = skillType,
                    family = classified.family.id,
                    weapon_token = classified.weaponToken,
                    family_confidence = classified.confidence,
                )
                true
            }
            "recipe" -> {
                val resultId = present(json.str("ResultItem"))
                db.schemaQueries.insertGameRecipe(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    name = DisplayName.of(row.name, row.rowId)
                        ?: present(json.obj("RecipeName")?.str("text"))
                        ?: nameIndex[row.rowId]
                        ?: resultId?.let { nameIndex[it] },
                    recipe_kind = recipeKind(row.tableName),
                )
                true
            }
            "status_effect" -> {
                db.schemaQueries.insertGameSkillEffect(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    source_row_id = row.rowId,
                    skill_source_row_id = null,
                    name = DisplayName.of(row.name, row.rowId) ?: nameIndex[row.rowId],
                )
                true
            }
            else -> false
        }
    }

    /**
     * Fills `game_material` from items the client explicitly lists as ingredients:
     * `TLCraftingMaterialGroup.Materials[].Item` and `TLCookingRecipe.*IngredientList[].ItemID`.
     * References that do not resolve to a known item row are counted, not guessed.
     */
    private fun mapMaterials(
        db: SolisiumDatabase,
        snapshotId: String,
        rows: List<WarehouseRecord>,
        itemsByRowId: Map<String, List<WarehouseRecord>>,
        nameIndex: Map<String, String>,
    ): Pair<Int, Int> {
        val referenced = LinkedHashSet<String>()
        for (row in rows) {
            when (row.tableName) {
                "TLCraftingMaterialGroup" -> parseJson(row.rawJson).arr("Materials").forEach { entry ->
                    present(entry.str("Item"))?.let(referenced::add)
                }
                "TLCookingRecipe" -> {
                    val json = parseJson(row.rawJson)
                    for (list in listOf("MainIngredientList", "SubIngredientList")) {
                        json.arr(list).forEach { entry -> present(entry.str("ItemID"))?.let(referenced::add) }
                    }
                }
            }
        }
        var imported = 0
        var unresolved = 0
        for (rowId in referenced) {
            val item = resolveItem(itemsByRowId, rowId)
            if (item == null) {
                unresolved++
                continue
            }
            db.schemaQueries.insertGameMaterial(
                snapshot_id = snapshotId,
                source_table = item.tableName,
                source_row_id = item.rowId,
                name = DisplayName.of(item.name, item.rowId) ?: nameIndex[item.rowId],
            )
            imported++
        }
        return imported to unresolved
    }

    /**
     * `TLItemStats` rows carry item row ids too, so several warehouse tables answer to
     * the same key. Only the display-name tables count as a resolution, so stat values
     * and materials attach to the row a user would see in `query items` rather than to a
     * config row that happens to share the key.
     */
    private fun resolveItem(
        itemsByRowId: Map<String, List<WarehouseRecord>>,
        rowId: String,
    ): WarehouseRecord? {
        val candidates = itemsByRowId[rowId] ?: return null
        for (table in ITEM_TABLE_PREFERENCE) {
            candidates.firstOrNull { it.tableName == table }?.let { return it }
        }
        return null
    }

    private data class ItemStatResult(
        val imported: Int,
        val unlinkedStatRows: Int,
        val unresolvedPointers: Int,
    )

    /**
     * Fills `game_item_stat` by walking the pointer chain the client actually uses:
     * `TLItemStats.main_stat_base_id` + `main_stat_base_seed` selects one row of
     * `TLItemMainStatInit`, which is keyed by its `id` + `seed` fields rather than by
     * row id.
     *
     * `TLItemExtraStatInit` is deliberately not mapped: every one of the 1,837 item
     * stat rows points at the same `M8_Extra_Stat` group, so it is a shared roll table
     * describing what a rolled extra stat would be worth, not stats a given item has.
     *
     * Only non-zero values are stored. Enchant and item-level curves are separate
     * tables and are not folded in here.
     */
    private fun mapItemStats(
        db: SolisiumDatabase,
        snapshotId: String,
        rows: List<WarehouseRecord>,
        itemsByRowId: Map<String, List<WarehouseRecord>>,
    ): ItemStatResult {
        val mainValues = HashMap<String, JsonValue>()
        for (row in rows) {
            if (row.tableName != "TLItemMainStatInit") continue
            val json = parseJson(row.rawJson)
            pointerKey(json, "id", "seed")?.let { mainValues[it] = json }
        }
        val statNames = rows.filter { it.tableName == "TLStats" }.associate { it.rowId to it.name }

        var imported = 0
        var unlinked = 0
        var unresolved = 0
        for (row in rows) {
            if (row.tableName != "TLItemStats") continue
            val item = resolveItem(itemsByRowId, row.rowId)
            if (item == null) {
                unlinked++
                continue
            }
            val json = parseJson(row.rawJson)
            imported += mapItemCurveLinks(db, snapshotId, item, json)
            val key = pointerKey(json, "main_stat_base_id", "main_stat_base_seed") ?: continue
            val values = mainValues[key]
            if (values == null) {
                unresolved++
                continue
            }
            imported += insertStatValues(db, snapshotId, item, values, "main_base", statNames)
        }
        return ItemStatResult(imported, unlinked, unresolved)
    }

    private data class ItemPowerLinkResult(
        val tablePresent: Boolean,
        val mapped: Int,
        val unresolved: Int,
    )

    /**
     * Links `TLItemEquip` rows to `TLItemCombatPower` using the conservative derived
     * mapper. Unresolved A/AA families stay unmapped. The result is not live CP.
     */
    private fun mapItemPowerLinks(
        db: SolisiumDatabase,
        snapshotId: String,
        rows: List<WarehouseRecord>,
    ): ItemPowerLinkResult {
        val powerRows = rows.filter { it.tableName == "TLItemCombatPower" }
        if (powerRows.isEmpty()) return ItemPowerLinkResult(false, 0, 0)
        val available = powerRows.map { it.rowId }.toSet()
        var mapped = 0
        var unresolved = 0
        for (row in rows) {
            if (row.tableName != "TLItemEquip") continue
            val json = parseJson(row.rawJson)
            val mapping = CombatPowerLookup.infer(
                itemId = row.rowId,
                equipCategory = json.str("equip_category"),
                itemGrade = json.strAny("item_grade", "grade"),
                affectsCategoryLevel = json.strAny("affects_category_Level", "affects_category_level"),
                levelSelectId = json.str("level_select_id"),
                minLevel = json.long("limit_level_min"),
                maxLevel = json.long("limit_level_max"),
                availableRows = available,
            )
            val powerRowId = mapping.rowId
            if (powerRowId == null) {
                if (mapping.evidence != "unsupported-equipment-type") unresolved++
                continue
            }
            db.schemaQueries.insertGameItemPower(
                snapshot_id = snapshotId,
                item_source_table = row.tableName,
                item_source_row_id = row.rowId,
                power_source_row_id = powerRowId,
                evidence = mapping.evidence,
                confidence = "derived",
            )
            mapped++
        }
        return ItemPowerLinkResult(true, mapped, unresolved)
    }

    /**
     * Stores the shared enchant and item-level curves once each. `TLItemMainStatEnchant`
     * is keyed by `id` + `enchant_level`, `TLItemMainLevelStat` by `Id` + `item_level`,
     * and both hold the cumulative total at that level rather than a per-level delta.
     */
    private fun mapStatCurves(db: SolisiumDatabase, snapshotId: String, rows: List<WarehouseRecord>): Int {
        val statNames = rows.filter { it.tableName == "TLStats" }.associate { it.rowId to it.name }
        var written = 0
        for (row in rows) {
            val levelField = CURVE_LEVEL_FIELD[row.tableName] ?: continue
            val json = parseJson(row.rawJson)
            val curveId = present(json.strAny("id", "Id")) ?: continue
            val level = json.long(levelField) ?: continue
            for ((field, value) in json.numbers()) {
                if (field in STAT_KEY_FIELDS || value == 0.0) continue
                db.schemaQueries.insertGameStatCurve(
                    snapshot_id = snapshotId,
                    source_table = row.tableName,
                    curve_id = curveId,
                    level = level,
                    stat_key = field,
                    stat_name = statNames[field],
                    raw_value = value.toLong(),
                    confidence = "extracted",
                )
                written++
            }
        }
        return written
    }

    private fun mapItemCurveLinks(
        db: SolisiumDatabase,
        snapshotId: String,
        item: WarehouseRecord,
        json: JsonValue,
    ): Int {
        var written = 0
        val maxLevel = json.long("enchant_level_max")
        val links = listOf(
            Triple("enchant", "TLItemMainStatEnchant", json.str("main_stat_enchant_id")) to maxLevel,
            Triple("item_level", "TLItemMainLevelStat", json.str("main_level_stat_id")) to null,
        )
        for ((link, cap) in links) {
            val (kind, curveTable, rawId) = link
            val curveId = present(rawId) ?: continue
            db.schemaQueries.insertGameItemCurve(
                snapshot_id = snapshotId,
                source_table = item.tableName,
                source_row_id = item.rowId,
                curve_kind = kind,
                curve_source_table = curveTable,
                curve_id = curveId,
                max_level = cap,
            )
            written++
        }
        return written
    }

    private fun pointerKey(json: JsonValue, idField: String, seedField: String): String? {
        val id = present(json.str(idField)) ?: return null
        val seed = json.long(seedField) ?: return null
        return "$id#$seed"
    }

    private fun insertStatValues(
        db: SolisiumDatabase,
        snapshotId: String,
        item: WarehouseRecord,
        values: JsonValue,
        scope: String,
        statNames: Map<String, String?>,
    ): Int {
        var written = 0
        for ((field, value) in values.numbers()) {
            if (field in STAT_KEY_FIELDS || value == 0.0) continue
            db.schemaQueries.insertGameItemStat(
                snapshot_id = snapshotId,
                source_table = item.tableName,
                source_row_id = item.rowId,
                stat_key = field,
                stat_name = statNames[field],
                raw_value = value.toLong(),
                scope = scope,
                confidence = "extracted",
            )
            written++
        }
        return written
    }

    private fun formulaTypes(json: JsonValue): String? {
        val types = json.arr("FormulaParameter")
            .mapNotNull { present(it.str("formula_type")) }
            .distinct()
        return types.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    private fun assertRecordsTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='records'",
            ).use { rs ->
                if (!rs.next()) {
                    throw IllegalArgumentException("not a TL-Helper warehouse: missing records table")
                }
            }
        }
    }

    private fun loadRecords(connection: Connection): List<WarehouseRecord> {
        val sql = """
            SELECT row_id, record_type, table_name, name_loc, game_build, game_version,
                   decoder_version, raw_json
            FROM records
        """.trimIndent()
        val out = ArrayList<WarehouseRecord>()
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    out.add(
                        WarehouseRecord(
                            rowId = rs.getString("row_id") ?: "",
                            recordType = rs.getString("record_type") ?: "",
                            tableName = rs.getString("table_name") ?: "",
                            name = rs.getString("name_loc"),
                            gameBuild = rs.getString("game_build") ?: "",
                            gameVersion = rs.getString("game_version") ?: "",
                            decoderVersion = rs.getString("decoder_version") ?: "",
                            rawJson = rs.getString("raw_json"),
                        ),
                    )
                }
            }
        }
        return out
    }

    private fun looksLikeUnmappedBuildTable(tableName: String): Boolean {
        val name = tableName.lowercase()
        return name.contains("specialization") ||
            name.contains("materialstat") ||
            name.contains("skilloptional") ||
            name.contains("transcend") ||
            name.contains("weaponmastery") ||
            name.contains("skillcore") ||
            name.contains("guardian") && name.contains("pc")
    }

    private fun recipeKind(tableName: String): String? = when (tableName) {
        "TLCookingRecipe" -> "cooking"
        "TLCraftingRecipe" -> "crafting"
        else -> null
    }

    private fun mapExtractedDrops(
        db: SolisiumDatabase,
        snapshotId: String,
        rows: List<WarehouseRecord>,
        nameIndex: Map<String, String>,
    ): ExtractedDropResult {
        fun toJsonRow(row: WarehouseRecord): WarehouseJsonRow? {
            val json = row.rawJson ?: return null
            return WarehouseJsonRow(row.tableName, row.rowId, json)
        }
        val rewardRows = rows.filter { it.tableName == "TLRewardNpcFoItem" }.mapNotNull { toJsonRow(it) }
        val lotteryRows = rows.filter {
            it.tableName == "TLItemLotteryUnit" || it.tableName == "TLItemLotteryPublicGroup"
        }.mapNotNull { toJsonRow(it) }
        return ExtractedDropMapper.mapInto(db, snapshotId, rewardRows, lotteryRows, nameIndex)
    }

    private data class WarehouseRecord(
        val rowId: String,
        val recordType: String,
        val tableName: String,
        val name: String?,
        val gameBuild: String,
        val gameVersion: String,
        val decoderVersion: String,
        val rawJson: String?,
    )

    /**
     * Localized names keyed by row id. Looks tables win so an equip/config row inherits
     * the inventory name rather than staying blank.
     */
    private fun reportProgress(request: ImportRequest, phase: String, current: Long, total: Long) {
        request.onProgress?.invoke(ImportProgress(phase, current, total))
    }

    private fun buildNameIndex(rows: List<WarehouseRecord>): Map<String, String> {
        val preferred = listOf("TLItemLooks_Equip", "TLItemLooks", "TLPassiveSkillLooks", "TLStatAttrLooks")
        val out = LinkedHashMap<String, String>()
        for (table in preferred) {
            for (row in rows) {
                if (row.tableName != table) continue
                val name = DisplayName.of(row.name, row.rowId) ?: continue
                out.putIfAbsent(row.rowId, name)
            }
        }
        for (row in rows) {
            val name = DisplayName.of(row.name, row.rowId) ?: continue
            out.putIfAbsent(row.rowId, name)
        }
        return out
    }

    companion object {
        /** Numeric fields on the stat-value rows that identify the row rather than carry a stat. */
        private val STAT_KEY_FIELDS = setOf("seed", "stat_seed", "enchant_level", "item_level")

        private val ITEM_TABLE_PREFERENCE = listOf("TLItemLooks_Equip", "TLItemLooks", "TLItemEquip")

        /** Curve tables and the field that carries their level dimension. */
        private val CURVE_LEVEL_FIELD = mapOf(
            "TLItemMainStatEnchant" to "enchant_level",
            "TLItemMainLevelStat" to "item_level",
        )

        internal fun peekJsonString(json: String?, key: String): String? {
            return present(parseJson(json).str(key))
        }

        internal fun parseJson(raw: String?): JsonValue {
            if (raw.isNullOrBlank()) return JsonValue.Obj(emptyMap())
            return try {
                JsonParser.parse(raw)
            } catch (_: JsonParseException) {
                JsonValue.Obj(emptyMap())
            }
        }

        internal fun present(value: String?): String? {
            if (value.isNullOrBlank() || value == "None") return null
            return value
        }

        internal fun sha256File(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { b -> "%02x".format(b) }
        }
    }
}
