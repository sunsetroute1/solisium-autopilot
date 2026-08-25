package com.solisium.core.source

import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.json.JsonParseException
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import com.solisium.core.platform.randomUuid

/**
 * User-supplied character JSON. Missing fields stay null; nothing is inferred from game data.
 *
 * Reimport of the same `character.id` replaces loadout rows. Inventory, currency, and cooking
 * are accepted when present, with a warning that they have no verified local file source.
 */
class ManualImportDataSource : DataSource {
    override val id: String = "manual"

    override fun probe(): SourceCapability = SourceCapability(
        id = id,
        available = true,
        provides = listOf(
            "user_character",
            "user_equipment",
            "user_weapon",
            "user_traits",
            "user_runes",
            "user_skills",
            "user_inventory",
            "user_materials",
            "user_currency",
            "user_cooking",
            "user_goals",
            "user_build",
        ),
        notes = "User-supplied JSON only. Not a live character export from the game client. " +
            "Hashed .sav files under Saved\\SaveGames are not parsed.",
    )

    override fun importInto(db: SolisiumDatabase, request: ImportRequest): ImportReceipt {
        val text = request.content
            ?: throw IllegalArgumentException("manual import content is required")
        val parsed = parseDocument(text, request.characterId)
        var imported = 0
        db.transaction {
            val existing = db.schemaQueries.selectCharacter(parsed.id).executeAsOneOrNull()
            val createdAt = existing?.created_at ?: parsed.updatedAt
            db.schemaQueries.deleteEquipment(parsed.id)
            db.schemaQueries.deleteWeapons(parsed.id)
            db.schemaQueries.deleteTraits(parsed.id)
            db.schemaQueries.deleteCharacterRunes(parsed.id)
            db.schemaQueries.deleteCharacterSkills(parsed.id)
            db.schemaQueries.deleteInventory(parsed.id)
            db.schemaQueries.deleteMaterials(parsed.id)
            db.schemaQueries.deleteCurrency(parsed.id)
            db.schemaQueries.deleteCooking(parsed.id)
            db.schemaQueries.deleteGoals(parsed.id)
            db.schemaQueries.deleteBuilds(parsed.id)
            db.schemaQueries.insertCharacter(
                id = parsed.id,
                name = parsed.name,
                level = parsed.level,
                combat_power = parsed.combatPower,
                server = parsed.server,
                notes = parsed.notes,
                created_at = createdAt,
                updated_at = parsed.updatedAt,
            )
            imported += 1
            parsed.equipment.forEach { row ->
                db.schemaQueries.insertEquipment(parsed.id, row.slot, row.sourceTable, row.sourceRowId, row.itemLevel)
                imported += 1
            }
            parsed.weapons.forEach { row ->
                db.schemaQueries.insertWeapon(parsed.id, row.slot, row.sourceTable, row.sourceRowId, row.itemLevel)
                imported += 1
            }
            parsed.traits.forEach { row ->
                db.schemaQueries.insertTrait(parsed.id, row.sourceTable, row.sourceRowId, row.rank)
                imported += 1
            }
            parsed.runes.forEach { row ->
                db.schemaQueries.insertCharacterRune(
                    parsed.id,
                    row.slot,
                    row.sourceTable,
                    row.sourceRowId,
                    row.runeLevel,
                )
                imported += 1
            }
            parsed.skills.forEach { row ->
                db.schemaQueries.insertCharacterSkill(parsed.id, row.sourceTable, row.sourceRowId, row.loadout)
                imported += 1
            }
            parsed.inventory.forEach { row ->
                db.schemaQueries.insertInventory(parsed.id, row.sourceTable, row.sourceRowId, row.quantity)
                imported += 1
            }
            parsed.materials.forEach { row ->
                db.schemaQueries.insertMaterial(parsed.id, row.sourceTable, row.sourceRowId, row.quantity)
                imported += 1
            }
            parsed.currency.forEach { row ->
                db.schemaQueries.insertCurrency(parsed.id, row.currency, row.amount)
                imported += 1
            }
            parsed.cookingLevel?.let { level ->
                db.schemaQueries.insertCooking(parsed.id, level)
                imported += 1
            }
            parsed.goals.forEach { row ->
                db.schemaQueries.insertGoal(parsed.id, row.goalType, row.label, if (row.active) 1 else 0)
                imported += 1
            }
            parsed.builds.forEach { row ->
                db.schemaQueries.insertBuild(row.id, parsed.id, row.name, row.snapshotId, row.payloadJson)
                imported += 1
            }
        }
        return ImportReceipt(
            source = id,
            characterId = parsed.id,
            recordsImported = imported,
            recordsSkipped = parsed.skipped,
            warnings = parsed.warnings,
        )
    }

    internal data class ParsedDocument(
        val id: String,
        val name: String,
        val level: Long?,
        val combatPower: Long?,
        val server: String?,
        val notes: String?,
        val updatedAt: String,
        val equipment: List<SlottedRef>,
        val weapons: List<SlottedRef>,
        val traits: List<TraitRef>,
        val runes: List<RuneRef>,
        val skills: List<SkillRef>,
        val inventory: List<StackRef>,
        val materials: List<StackRef>,
        val currency: List<CurrencyRef>,
        val cookingLevel: Long?,
        val goals: List<GoalRef>,
        val builds: List<BuildRef>,
        val skipped: Int,
        val warnings: List<String>,
    )

    internal data class SlottedRef(
        val slot: String,
        val sourceTable: String?,
        val sourceRowId: String?,
        val itemLevel: Long?,
    )

    internal data class TraitRef(
        val sourceTable: String?,
        val sourceRowId: String?,
        val rank: Long?,
    )

    internal data class RuneRef(
        val slot: String?,
        val sourceTable: String?,
        val sourceRowId: String?,
        val runeLevel: Long?,
    )

    internal data class SkillRef(
        val sourceTable: String?,
        val sourceRowId: String?,
        val loadout: String?,
    )

    internal data class StackRef(
        val sourceTable: String?,
        val sourceRowId: String?,
        val quantity: Long,
    )

    internal data class CurrencyRef(
        val currency: String,
        val amount: Long,
    )

    internal data class GoalRef(
        val goalType: String,
        val label: String,
        val active: Boolean,
    )

    internal data class BuildRef(
        val id: String,
        val name: String,
        val snapshotId: String?,
        val payloadJson: String?,
    )

    companion object {
        private const val SCHEMA_ID = "solisium.manual-character"
        private const val MANUAL_ONLY =
            "inventory/currency/cooking were imported from this JSON only; there is no verified local file source"

        internal fun parseDocument(text: String, characterIdOverride: String? = null): ParsedDocument {
            val root = try {
                JsonParser.parse(text).asObj()
            } catch (error: JsonParseException) {
                throw IllegalArgumentException("manual character JSON is invalid: ${error.message}", error)
            }
            val warnings = mutableListOf<String>()
            var skipped = 0
            val schema = root.str("schema")
            if (schema != SCHEMA_ID) {
                warnings.add("document is not labeled $SCHEMA_ID")
            }
            val schemaVersion = root.longAny("schemaVersion", "schema_version")
            if (schemaVersion != null && schemaVersion != 1L) {
                warnings.add("unsupported schemaVersion $schemaVersion; treating as 1")
            }
            val character = root.obj("character") ?: root
            val id = characterIdOverride
                ?: character.strAny("id")
                ?: randomUuid()
            val name = character.strAny("name") ?: "Unnamed"
            if (character.strAny("name") == null) warnings.add("character.name missing")
            val equipment = mutableListOf<SlottedRef>()
            skipped += readSlotted(root.arr("equipment"), "equipment", warnings, equipment)
            val weapons = mutableListOf<SlottedRef>()
            skipped += readSlotted(root.arr("weapons"), "weapon", warnings, weapons)
            val traits = mutableListOf<TraitRef>()
            skipped += readTraits(root.arr("traits"), warnings, traits)
            val runes = mutableListOf<RuneRef>()
            skipped += readRunes(root.arr("runes"), warnings, runes)
            val skills = mutableListOf<SkillRef>()
            skipped += readSkills(root.arr("skills"), warnings, skills)
            val inventory = mutableListOf<StackRef>()
            skipped += readStacks(root.arr("inventory"), "inventory", warnings, inventory)
            val materials = mutableListOf<StackRef>()
            skipped += readStacks(root.arr("materials"), "material", warnings, materials)
            val currency = mutableListOf<CurrencyRef>()
            skipped += readCurrency(root.arr("currency"), warnings, currency)
            val cookingObj = root.obj("cooking")
            val cookingLevel = cookingObj?.longAny("level") ?: root.longAny("cooking_level", "cookingLevel")
            val goals = mutableListOf<GoalRef>()
            skipped += readGoals(root.arr("goals"), warnings, goals)
            val builds = mutableListOf<BuildRef>()
            skipped += readBuilds(root.arr("builds"), warnings, builds)
            if (inventory.isNotEmpty() || currency.isNotEmpty() || cookingLevel != null) {
                warnings.add(MANUAL_ONLY)
            }
            return ParsedDocument(
                id = id,
                name = name,
                level = character.longAny("level"),
                combatPower = character.longAny("combat_power", "combatPower"),
                server = character.strAny("server"),
                notes = character.strAny("notes"),
                updatedAt = character.strAny("updated_at", "updatedAt") ?: "1970-01-01T00:00:00Z",
                equipment = equipment,
                weapons = weapons,
                traits = traits,
                runes = runes,
                skills = skills,
                inventory = inventory,
                materials = materials,
                currency = currency,
                cookingLevel = cookingLevel,
                goals = goals,
                builds = builds,
                skipped = skipped,
                warnings = warnings,
            )
        }

        private fun asObject(value: JsonValue, label: String, warnings: MutableList<String>): JsonValue.Obj? {
            val obj = value as? JsonValue.Obj
            if (obj == null) {
                warnings.add("$label row is not an object")
            }
            return obj
        }

        private fun sourceTable(obj: JsonValue): String? = obj.strAny("source_table", "sourceTable")

        private fun sourceRowId(obj: JsonValue): String? = obj.strAny("source_row_id", "sourceRowId")

        private fun readSlotted(
            items: List<JsonValue>,
            kind: String,
            warnings: MutableList<String>,
            out: MutableList<SlottedRef>,
        ): Int {
            var skipped = 0
            items.forEach { item ->
                val obj = asObject(item, kind, warnings) ?: run {
                    skipped += 1
                    return@forEach
                }
                val slot = obj.strAny("slot")
                if (slot.isNullOrBlank()) {
                    warnings.add("$kind row missing slot")
                    skipped += 1
                    return@forEach
                }
                out.add(
                    SlottedRef(
                        slot = slot,
                        sourceTable = sourceTable(obj),
                        sourceRowId = sourceRowId(obj),
                        itemLevel = obj.longAny("item_level", "itemLevel"),
                    ),
                )
            }
            return skipped
        }

        private fun readTraits(
            items: List<JsonValue>,
            warnings: MutableList<String>,
            out: MutableList<TraitRef>,
        ): Int {
            var skipped = 0
            items.forEach { item ->
                val obj = asObject(item, "trait", warnings) ?: run {
                    skipped += 1
                    return@forEach
                }
                if (obj.strAny("slot") != null) {
                    warnings.add("trait.slot is not stored in schema v1")
                }
                if (obj.strAny("name") != null && sourceRowId(obj) == null) {
                    warnings.add("trait.name is not a warehouse key; provide source_table and source_row_id")
                    skipped += 1
                    return@forEach
                }
                if (sourceTable(obj) == null && sourceRowId(obj) == null) {
                    warnings.add("trait row missing source_table/source_row_id")
                    skipped += 1
                    return@forEach
                }
                out.add(
                    TraitRef(
                        sourceTable = sourceTable(obj),
                        sourceRowId = sourceRowId(obj),
                        rank = obj.longAny("rank"),
                    ),
                )
            }
            return skipped
        }

        private fun readRunes(
            items: List<JsonValue>,
            warnings: MutableList<String>,
            out: MutableList<RuneRef>,
        ): Int {
            var skipped = 0
            items.forEach { item ->
                val obj = asObject(item, "rune", warnings) ?: run {
                    skipped += 1
                    return@forEach
                }
                if (sourceTable(obj) == null && sourceRowId(obj) == null) {
                    warnings.add("rune row missing source_table/source_row_id")
                    skipped += 1
                    return@forEach
                }
                out.add(
                    RuneRef(
                        slot = obj.strAny("slot"),
                        sourceTable = sourceTable(obj),
                        sourceRowId = sourceRowId(obj),
                        runeLevel = obj.longAny("rune_level", "runeLevel"),
                    ),
                )
            }
            return skipped
        }

        private fun readSkills(
            items: List<JsonValue>,
            warnings: MutableList<String>,
            out: MutableList<SkillRef>,
        ): Int {
            var skipped = 0
            items.forEach { item ->
                val obj = asObject(item, "skill", warnings) ?: run {
                    skipped += 1
                    return@forEach
                }
                if (sourceTable(obj) == null && sourceRowId(obj) == null) {
                    warnings.add("skill row missing source_table/source_row_id")
                    skipped += 1
                    return@forEach
                }
                out.add(
                    SkillRef(
                        sourceTable = sourceTable(obj),
                        sourceRowId = sourceRowId(obj),
                        loadout = obj.strAny("loadout"),
                    ),
                )
            }
            return skipped
        }

        private fun readStacks(
            items: List<JsonValue>,
            kind: String,
            warnings: MutableList<String>,
            out: MutableList<StackRef>,
        ): Int {
            var skipped = 0
            items.forEach { item ->
                val obj = asObject(item, kind, warnings) ?: run {
                    skipped += 1
                    return@forEach
                }
                val quantity = obj.longAny("quantity")
                if (quantity == null) {
                    warnings.add("$kind row missing quantity")
                    skipped += 1
                    return@forEach
                }
                out.add(
                    StackRef(
                        sourceTable = sourceTable(obj),
                        sourceRowId = sourceRowId(obj),
                        quantity = quantity,
                    ),
                )
            }
            return skipped
        }

        private fun readCurrency(
            items: List<JsonValue>,
            warnings: MutableList<String>,
            out: MutableList<CurrencyRef>,
        ): Int {
            var skipped = 0
            items.forEach { item ->
                val obj = asObject(item, "currency", warnings) ?: run {
                    skipped += 1
                    return@forEach
                }
                val name = obj.strAny("currency", "name")
                val amount = obj.longAny("amount")
                if (name.isNullOrBlank() || amount == null) {
                    warnings.add("currency row requires currency and amount")
                    skipped += 1
                    return@forEach
                }
                out.add(CurrencyRef(currency = name, amount = amount))
            }
            return skipped
        }

        private fun readGoals(
            items: List<JsonValue>,
            warnings: MutableList<String>,
            out: MutableList<GoalRef>,
        ): Int {
            var skipped = 0
            items.forEach { item ->
                val obj = asObject(item, "goal", warnings) ?: run {
                    skipped += 1
                    return@forEach
                }
                val goalType = obj.strAny("goal_type", "goalType")
                val label = obj.strAny("label")
                if (goalType.isNullOrBlank() || label.isNullOrBlank()) {
                    warnings.add("goal row requires goal_type and label")
                    skipped += 1
                    return@forEach
                }
                out.add(
                    GoalRef(
                        goalType = goalType,
                        label = label,
                        active = obj.boolAny("active") ?: true,
                    ),
                )
            }
            return skipped
        }

        private fun readBuilds(
            items: List<JsonValue>,
            warnings: MutableList<String>,
            out: MutableList<BuildRef>,
        ): Int {
            var skipped = 0
            items.forEach { item ->
                val obj = asObject(item, "build", warnings) ?: run {
                    skipped += 1
                    return@forEach
                }
                val name = obj.strAny("name")
                if (name.isNullOrBlank()) {
                    warnings.add("build row missing name")
                    skipped += 1
                    return@forEach
                }
                val payload = when (val value = obj.child("payload_json") ?: obj.child("payloadJson")) {
                    is JsonValue.Str -> value.value
                    null, JsonValue.Null -> null
                    else -> {
                        warnings.add("build.payload_json must be a string")
                        null
                    }
                }
                out.add(
                    BuildRef(
                        id = obj.strAny("id") ?: randomUuid(),
                        name = name,
                        snapshotId = obj.strAny("snapshot_id", "snapshotId"),
                        payloadJson = payload,
                    ),
                )
            }
            return skipped
        }
    }
}
