package com.solisium.core.source

import com.solisium.core.domain.BuildLayer
import com.solisium.core.domain.CharacterSheet
import com.solisium.core.domain.CharacterSlots
import com.solisium.core.domain.ClassSource
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.SkillFamily
import com.solisium.core.query.WeaponClassResolver

/**
 * Round-trip for the Character screen. Writes `solisium.manual-character` JSON from
 * typed fields; does not invent items the user did not enter.
 */
object CharacterSheetJson {
    data class NamedSlot(val slot: String, val name: String)

    data class NamedStack(val name: String, val quantity: String)

    data class NamedSkill(
        val name: String,
        val loadout: String = "",
        val level: String = "",
        val family: String = "",
    )

    data class NamedMastery(val weapon: String, val level: String)

    data class NamedLayer(
        val layer: String,
        val slot: String = "",
        val name: String = "",
        val level: String = "",
    )

    data class Draft(
        val id: String,
        val name: String,
        val level: String,
        val combatPower: String,
        val gearScore: String,
        val strength: String,
        val dexterity: String,
        val wisdom: String,
        val perception: String,
        val fortitude: String,
        val className: String = "",
        val classSource: String = "",
        val server: String,
        val weapons: List<NamedSlot>,
        val equipment: List<NamedSlot>,
        val inventory: List<NamedStack>,
        val skills: List<NamedSkill> = emptyList(),
        val weaponMastery: List<NamedMastery> = emptyList(),
        val buildLayers: List<NamedLayer> = emptyList(),
    )

    fun fromResolved(resolved: ResolvedCharacterSheet): Draft {
        val character = resolved.sheet.character
        val weaponNames = resolved.lines.filter { it.kind == "weapon" }.associate { line ->
            (line.label ?: "") to (DisplayName.of(line.hit?.name) ?: line.name.orEmpty())
        }
        val gearNames = resolved.lines.filter { it.kind == "equipment" }.associate { line ->
            (line.label ?: "") to (DisplayName.of(line.hit?.name) ?: line.name.orEmpty())
        }
        val match = WeaponClassResolver.applyStored(
            character.className,
            character.classSource,
            resolved.weaponClass ?: WeaponClassResolver.resolve(emptyList(), null, null),
        )
        return Draft(
            id = character.id,
            name = character.name,
            level = character.level?.toString().orEmpty(),
            combatPower = character.combatPower?.takeIf { it > 0 }?.toString().orEmpty(),
            gearScore = character.gearScore?.takeIf { it > 0 }?.toString().orEmpty(),
            strength = character.strength?.toString().orEmpty(),
            dexterity = character.dexterity?.toString().orEmpty(),
            wisdom = character.wisdom?.toString().orEmpty(),
            perception = character.perception?.toString().orEmpty(),
            fortitude = character.fortitude?.toString().orEmpty(),
            className = match.name.orEmpty(),
            classSource = match.source.orEmpty(),
            server = character.server.orEmpty(),
            weapons = CharacterSlots.mergeWeapons(resolved.sheet.weapons).map { row ->
                NamedSlot(row.slot, row.name ?: weaponNames[row.slot].orEmpty())
            },
            equipment = CharacterSlots.mergeEquipment(resolved.sheet.equipment).map { row ->
                NamedSlot(row.slot, row.name ?: gearNames[row.slot].orEmpty())
            },
            inventory = resolved.sheet.inventory.map { stack ->
                NamedStack(
                    name = DisplayName.of(stack.name) ?: stack.sourceRowId.orEmpty(),
                    quantity = stack.quantity.toString(),
                )
            }.ifEmpty { List(3) { NamedStack("", "1") } },
            skills = resolved.sheet.skills.map { skill ->
                NamedSkill(
                    name = DisplayName.of(skill.name) ?: skill.sourceRowId.orEmpty(),
                    loadout = skill.loadout.orEmpty(),
                    level = skill.skillLevel?.toString().orEmpty(),
                    family = skill.family.orEmpty(),
                )
            }.ifEmpty { listOf(NamedSkill("", "PvE Grind", "", SkillFamily.Weapon.id)) },
            weaponMastery = resolved.sheet.weaponMastery.map { row ->
                NamedMastery(row.weapon, row.level?.toString().orEmpty())
            }.ifEmpty { listOf(NamedMastery("", ""), NamedMastery("", "")) },
            buildLayers = BuildLayer.entries.filter { it != BuildLayer.WeaponSkill }.flatMap { layer ->
                val rows = resolved.sheet.buildLayers.filter { it.layer.equals(layer.id, ignoreCase = true) }
                if (rows.isEmpty()) {
                    listOf(NamedLayer(layer.id, "1", "", ""))
                } else {
                    rows.map { row ->
                        NamedLayer(
                            layer = layer.id,
                            slot = row.slot.orEmpty(),
                            name = DisplayName.of(row.name) ?: row.sourceRowId.orEmpty(),
                            level = row.level?.toString().orEmpty(),
                        )
                    }
                }
            },
        )
    }

    fun write(draft: Draft, sheet: CharacterSheet? = null, updatedAt: String = "1970-01-01T00:00:00Z"): String {
        return buildString {
            appendLine("{")
            appendLine("  \"schema\": \"solisium.manual-character\",")
            appendLine("  \"schemaVersion\": 1,")
            appendLine("  \"character\": {")
            appendLine("    \"id\": ${quote(draft.id.ifBlank { "replace-me" })},")
            appendLine("    \"name\": ${quote(draft.name.ifBlank { "Your character" })},")
            appendLine("    \"level\": ${numberOrNull(draft.level)},")
            appendLine("    \"combat_power\": ${numberOrNull(draft.combatPower)},")
            appendLine("    \"gear_score\": ${numberOrNull(draft.gearScore)},")
            appendLine("    \"stat_points\": {")
            appendLine("      \"strength\": ${numberOrNull(draft.strength)},")
            appendLine("      \"dexterity\": ${numberOrNull(draft.dexterity)},")
            appendLine("      \"wisdom\": ${numberOrNull(draft.wisdom)},")
            appendLine("      \"perception\": ${numberOrNull(draft.perception)},")
            appendLine("      \"fortitude\": ${numberOrNull(draft.fortitude)}")
            appendLine("    },")
            appendLine("    \"class_name\": ${quote(draft.className)},")
            appendLine("    \"class_source\": ${quote(writtenClassSource(draft))},")
            appendLine("    \"server\": ${quote(draft.server)},")
            appendLine("    \"updated_at\": ${quote(updatedAt)}")
            appendLine("  },")
            appendLine("  \"weapons\": ${slotArray(draft.weapons)},")
            appendLine("  \"equipment\": ${slotArray(draft.equipment)},")
            append("  \"inventory\": ")
            append(stackArray(draft.inventory))
            appendLine(",")
            appendLine("  \"traits\": ${keyArray(sheet?.traits?.map { Triple(it.sourceTable, it.sourceRowId, it.rank) } ?: emptyList(), "rank")},")
            appendLine("  \"runes\": [],")
            append("  \"skills\": ")
            append(skillArray(draft.skills))
            appendLine(",")
            append("  \"weapon_mastery\": ")
            append(masteryArray(draft.weaponMastery))
            appendLine(",")
            append("  \"build_layers\": ")
            append(layerArray(draft.buildLayers))
            appendLine(",")
            appendLine("  \"materials\": [],")
            appendLine("  \"currency\": [],")
            appendLine("  \"goals\": [],")
            appendLine("  \"builds\": []")
            appendLine("}")
        }
    }

    private fun slotArray(rows: List<NamedSlot>): String {
        if (rows.isEmpty()) return "[]"
        return rows.joinToString(prefix = "[\n", postfix = "\n  ]", separator = ",\n") { row ->
            "    { \"slot\": ${quote(row.slot)}, \"name\": ${quote(row.name)} }"
        }
    }

    private fun stackArray(rows: List<NamedStack>): String {
        val present = rows.filter { it.name.isNotBlank() }
        if (present.isEmpty()) return "[]"
        return present.joinToString(prefix = "[\n", postfix = "\n  ]", separator = ",\n") { row ->
            val qty = row.quantity.trim().toLongOrNull()?.takeIf { it > 0 } ?: 1L
            "    { \"name\": ${quote(row.name.trim())}, \"quantity\": $qty }"
        }
    }

    private fun skillArray(rows: List<NamedSkill>): String {
        val present = rows.filter { it.name.isNotBlank() }
        if (present.isEmpty()) return "[]"
        return present.joinToString(prefix = "[\n", postfix = "\n  ]", separator = ",\n") { row ->
            val extras = buildString {
                if (row.loadout.isNotBlank()) append(", \"loadout\": ${quote(row.loadout)}")
                numberOrNull(row.level).takeIf { it != "0" }?.let { append(", \"level\": $it") }
                if (row.family.isNotBlank()) append(", \"family\": ${quote(row.family)}")
            }
            "    { \"name\": ${quote(row.name.trim())}$extras }"
        }
    }

    private fun masteryArray(rows: List<NamedMastery>): String {
        val present = rows.filter { it.weapon.isNotBlank() }
        if (present.isEmpty()) return "[]"
        return present.joinToString(prefix = "[\n", postfix = "\n  ]", separator = ",\n") { row ->
            "    { \"weapon\": ${quote(row.weapon.trim())}, \"level\": ${numberOrNull(row.level)} }"
        }
    }

    private fun layerArray(rows: List<NamedLayer>): String {
        val present = rows.filter { it.name.isNotBlank() }
        if (present.isEmpty()) return "[]"
        return present.joinToString(prefix = "[\n", postfix = "\n  ]", separator = ",\n") { row ->
            val extras = buildString {
                if (row.slot.isNotBlank()) append(", \"slot\": ${quote(row.slot)}")
                numberOrNull(row.level).takeIf { it != "0" }?.let { append(", \"level\": $it") }
            }
            "    { \"layer\": ${quote(row.layer)}, \"name\": ${quote(row.name.trim())}$extras }"
        }
    }

    private fun keyArray(rows: List<Triple<String?, String?, Long?>>, extraKey: String): String {
        val present = rows.filter { !it.first.isNullOrBlank() || !it.second.isNullOrBlank() }
        if (present.isEmpty()) return "[]"
        return present.joinToString(prefix = "[\n", postfix = "\n  ]", separator = ",\n") { row ->
            val extra = row.third?.let { ", \"$extraKey\": $it" } ?: ""
            "    { \"source_table\": ${quote(row.first.orEmpty())}, \"source_row_id\": ${quote(row.second.orEmpty())}$extra }"
        }
    }

    private fun writtenClassSource(draft: Draft): String {
        if (draft.className.isBlank()) return ""
        return draft.classSource.ifBlank { ClassSource.MANUAL }
    }

    private fun numberOrNull(raw: String): String {
        val value = raw.trim().replace(",", "").toLongOrNull()
        return value?.toString() ?: "0"
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
