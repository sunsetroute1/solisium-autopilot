package com.solisium.core.source

import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.WeaponTypeLabel
import com.solisium.core.json.JsonValue

/**
 * Maps warehouse class tables (`TLPcClass` and similarly named rows) into a
 * weapon pair + title. Rows that do not carry two distinct combat weapons are
 * skipped rather than guessed. Specialization / mastery looks tables are not
 * class titles and are ignored.
 */
object WeaponClassMapper {
    fun considers(tableName: String): Boolean {
        val folded = tableName.lowercase()
        return folded == "tlpcclass" ||
            folded.contains("pcclass") ||
            folded == "tlweaponclass" ||
            folded.contains("weaponclass")
    }

    fun parse(
        tableName: String,
        rowId: String,
        nameLoc: String?,
        json: JsonValue,
    ): Parsed? {
        if (!considers(tableName)) return null
        val named = namedWeapons(json)
        val tokens = linkedSetOf<String>()
        if (named.size == 2) {
            tokens.addAll(named)
        } else {
            collectWeapons(json, tokens, depth = 0)
        }
        if (tokens.size != 2) return null
        val canonical = WeaponTypeLabel.canonical(tokens.elementAt(0), tokens.elementAt(1)) ?: return null
        val name = DisplayName.of(
            json.strAny("name", "UIName", "class_name", "ClassName", "title", "Title") ?: nameLoc,
            rowId,
        )
        if (name.isNullOrBlank()) return null
        return Parsed(
            sourceTable = tableName,
            sourceRowId = rowId,
            name = name,
            weaponA = canonical.first,
            weaponB = canonical.second,
        )
    }

    data class Parsed(
        val sourceTable: String,
        val sourceRowId: String,
        val name: String,
        val weaponA: String,
        val weaponB: String,
    )

    private fun namedWeapons(json: JsonValue): List<String> {
        val keys = listOf(
            "weapon_a", "weapon_b", "WeaponA", "WeaponB",
            "weapon1", "weapon2", "Weapon1", "Weapon2",
            "FirstWeapon", "SecondWeapon", "MainWeapon", "SubWeapon",
            "weapon_type_1", "weapon_type_2",
        )
        return keys.mapNotNull { key ->
            WeaponTypeLabel.combatToken(json.str(key) ?: json.obj(key)?.strAny("enum", "name", "value"))
        }
    }

    private fun collectWeapons(value: JsonValue, into: MutableSet<String>, depth: Int) {
        if (depth > 3) return
        when (value) {
            is JsonValue.Str -> WeaponTypeLabel.combatToken(value.value)?.let(into::add)
            is JsonValue.Arr -> value.items.forEach { collectWeapons(it, into, depth + 1) }
            is JsonValue.Obj -> value.fields.values.forEach { collectWeapons(it, into, depth + 1) }
            else -> Unit
        }
    }
}
