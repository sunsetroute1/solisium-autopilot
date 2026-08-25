package com.solisium.core.meta

import com.solisium.core.domain.CommunityHit
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import com.solisium.core.query.BuildGoal

object QuestlogParser {
    fun searchHits(json: String, source: String = "questlog"): List<CommunityHit> {
        return resultItems(json).mapNotNull { item ->
            val obj = item as? JsonValue.Obj ?: return@mapNotNull null
            val name = obj.str("name")?.let(TextNorm::stripMarkup)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val dbType = obj.str("dbType") ?: obj.str("skillType")
            val category = obj.str("mainCategory") ?: obj.str("subCategory")
            val id = obj.str("id")
            CommunityHit(
                source = source,
                name = name,
                detail = listOfNotNull(dbType, category, obj.str("grade")).joinToString(" · ").ifBlank { null },
                url = id?.let { "https://questlog.gg/throne-and-liberty/en/db/search?q=${it}" },
                catalogName = null,
            )
        }
    }

    fun skillHits(json: String, goal: BuildGoal, source: String = "questlog"): List<CommunityHit> {
        return resultItems(json).mapNotNull { item ->
            val obj = item as? JsonValue.Obj ?: return@mapNotNull null
            val name = obj.str("name")?.let(TextNorm::stripMarkup)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val category = (obj.str("mainCategory") ?: "").lowercase()
            if (goal.skillCategories.isNotEmpty() && category.isNotEmpty() && category !in goal.skillCategories) {
                return@mapNotNull null
            }
            CommunityHit(
                source = source,
                name = name,
                detail = listOfNotNull(obj.str("skillType"), obj.str("mainCategory"), obj.str("enchantCategory"))
                    .joinToString(" · ").ifBlank { null },
                url = null,
                catalogName = null,
            )
        }
    }

    /**
     * Public character payload from `characterBuilder.getCharacter`. A missing slug is
     * `{status: NOT_FOUND}`, not an HTTP error.
     */
    fun characterHits(json: String, slug: String, source: String = "questlog"): Pair<List<CommunityHit>, String?> {
        val root = JsonParser.parse(json)
        val data = root.child("result")?.child("data") ?: root.child("data") ?: root
        if (data.str("status")?.equals("NOT_FOUND", ignoreCase = true) == true) {
            return emptyList<CommunityHit>() to "Questlog has no public character at \"$slug\"."
        }
        val hits = mutableListOf<CommunityHit>()
        walkItems(data, path = "character") { obj, path ->
            val name = obj.strAny("name", "itemName", "title")?.let(TextNorm::stripMarkup)?.takeIf { it.length >= 3 }
                ?: return@walkItems
            if (!pathLooksLikeGear(path) && obj.str("slot") == null && obj.str("itemId") == null) return@walkItems
            val slot = obj.strAny("slot", "type", "mainCategory", "subCategory")
            hits += CommunityHit(
                source = source,
                name = name,
                detail = listOfNotNull("gear", slug, slot).joinToString(" · "),
                url = "https://questlog.gg/throne-and-liberty/en/character-builder/$slug",
                catalogName = null,
            )
        }
        val title = data.strAny("name", "title")?.let(TextNorm::stripMarkup)
        if (title != null && hits.none { it.name == title }) {
            hits.add(
                0,
                CommunityHit(
                    source = source,
                    name = title,
                    detail = "character · $slug",
                    url = "https://questlog.gg/throne-and-liberty/en/character-builder/$slug",
                    catalogName = null,
                ),
            )
        }
        return hits.distinctBy { TextNorm.fold(it.name) } to null
    }

    private fun pathLooksLikeGear(path: String): Boolean {
        val lower = path.lowercase()
        return listOf("equip", "weapon", "armor", "item", "gear", "loadout", "accessory").any { it in lower }
    }

    private fun walkItems(value: JsonValue, path: String, visit: (JsonValue.Obj, String) -> Unit) {
        when (value) {
            is JsonValue.Obj -> {
                visit(value, path)
                value.fields.forEach { (key, child) -> walkItems(child, "$path.$key", visit) }
            }
            is JsonValue.Arr -> value.items.forEachIndexed { i, child -> walkItems(child, "$path[$i]", visit) }
            else -> Unit
        }
    }

    internal fun resultItems(json: String): List<JsonValue> {
        val root = JsonParser.parse(json)
        val result = root.child("result") ?: root
        return when (val data = result.child("data")) {
            is JsonValue.Arr -> data.items
            is JsonValue.Obj -> {
                val nested = data.child("builds")
                if (nested is JsonValue.Arr) nested.items else listOf(data)
            }
            else -> emptyList()
        }
    }
}
