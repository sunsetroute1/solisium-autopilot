package com.solisium.core.meta

import com.solisium.core.domain.CommunityHit
import com.solisium.core.domain.CommunityStatLine
import com.solisium.core.domain.CommunityTraitLine
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.QuestlogDropEntry
import com.solisium.core.domain.QuestlogItemOverlay
import com.solisium.core.domain.QuestlogNpcDetail
import com.solisium.core.domain.QuestlogResourceDetail
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
                entityId = id,
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

    fun classPairs(text: String) = CommunityWeaponClasses.parseFromText(text)

    /** Community item detail from `database.getItem`. Returns null when the slug is missing. */
    fun itemDetail(json: String): QuestlogItemOverlay? {
        val root = JsonParser.parse(json)
        val data = root.child("result")?.child("data") ?: root.child("data") ?: return null
        if (data.str("status")?.equals("NOT_FOUND", ignoreCase = true) == true) return null
        val obj = data as? JsonValue.Obj ?: return null
        val statsRoot = obj.obj("itemStats")
        return QuestlogItemOverlay(
            description = obj.str("description")?.let(TextNorm::stripMarkup)?.trim(),
            requiredLevel = obj.long("requiredLevel"),
            sellPrice = obj.long("sellPrice"),
            tradeCategory = obj.str("tradeCategory"),
            grade = obj.str("grade"),
            properties = itemProperties(obj),
            statLines = statLines(statsRoot),
            traitLines = traitLines(statsRoot),
            perkSummaries = obj.arr("itemAvailablePerks").mapNotNull { perk ->
                val perkObj = perk as? JsonValue.Obj ?: return@mapNotNull null
                val name = perkObj.str("name")?.let(TextNorm::stripMarkup) ?: return@mapNotNull null
                val passive = perkObj.obj("passive")?.str("text")?.let(TextNorm::stripMarkup)
                if (passive.isNullOrBlank()) name else "$name — $passive"
            },
            droppedFromNpcs = dropEntries(obj.arr("itemDroppedFromNpcs")),
            containerSources = dropEntries(obj.arr("itemIsContainedInItems")),
        )
    }

    fun npcDetail(json: String): QuestlogNpcDetail? {
        val root = JsonParser.parse(json)
        val data = root.child("result")?.child("data") ?: root.child("data") ?: return null
        if (data.str("status")?.equals("NOT_FOUND", ignoreCase = true) == true) return null
        val obj = data as? JsonValue.Obj ?: return null
        val name = obj.str("name")?.let(TextNorm::stripMarkup) ?: return null
        return QuestlogNpcDetail(
            id = obj.str("id") ?: return null,
            name = name,
            subtitle = obj.str("subtitle")?.let(TextNorm::stripMarkup),
            level = obj.long("level"),
            category = obj.str("mainCategory"),
            mapId = obj.long("mapId"),
            drops = dropEntries(obj.arr("npcDropsItems")),
        )
    }

    fun resourceDetail(json: String): QuestlogResourceDetail? {
        val root = JsonParser.parse(json)
        val data = root.child("result")?.child("data") ?: root.child("data") ?: return null
        if (data.str("status")?.equals("NOT_FOUND", ignoreCase = true) == true) return null
        val obj = data as? JsonValue.Obj ?: return null
        val name = obj.str("name")?.let(TextNorm::stripMarkup) ?: return null
        return QuestlogResourceDetail(
            id = obj.str("id") ?: return null,
            name = name,
            level = obj.long("level"),
            mapId = obj.long("mapId"),
            drops = dropEntries(obj.arr("resourceDropsItems")),
        )
    }

    private fun dropEntries(values: List<JsonValue>): List<QuestlogDropEntry> =
        values.mapNotNull { entry ->
            val obj = entry as? JsonValue.Obj ?: return@mapNotNull null
            val name = obj.str("name")?.let(TextNorm::stripMarkup)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            QuestlogDropEntry(
                id = id,
                name = name,
                dbType = obj.str("dbType") ?: "unknown",
                category = obj.str("mainCategory"),
                level = obj.long("level"),
                probability = obj.double("probability"),
                quantity = obj.long("quantity"),
                dropType = obj.str("dropType"),
                dropCondition = obj.str("dropCondition"),
            )
        }

    private fun itemProperties(item: JsonValue.Obj): List<String> = buildList {
        fun flag(label: String, key: String, positive: Boolean = true) {
            val value = item.bool(key) ?: return
            if (value == positive) add(label)
        }
        flag("Exchangeable", "isExchangeable")
        flag("Sellable", "isSellable")
        flag("Storable", "isStorable")
        flag("Decomposable", "isDecomposable")
        if (item.bool("isExchangeable") == false) add("Not exchangeable")
        if (item.bool("isSellable") == false) add("Not sellable")
    }

    private fun statLines(statsRoot: JsonValue.Obj?): List<CommunityStatLine> {
        if (statsRoot == null) return emptyList()
        val lines = mutableListOf<CommunityStatLine>()
        appendLevelBlock(lines, statsRoot.obj("main"), "Main")
        appendLevelBlock(lines, statsRoot.obj("extra"), "Extra")
        return lines
    }

    private fun appendLevelBlock(out: MutableList<CommunityStatLine>, block: JsonValue.Obj?, group: String) {
        if (block == null) return
        val level = maxLevelKey(block) ?: return
        val payload = block.fields[level] as? JsonValue.Obj ?: return
        payload.obj("mainhand")?.let { hand ->
            val statId = hand.str("statId") ?: "mainhand"
            val min = hand.long("min")
            val max = hand.long("max")
            val value = when {
                min != null && max != null -> "$min – $max"
                max != null -> max.toString()
                else -> null
            }
            if (value != null) out += CommunityStatLine(group, prettyStatId(statId), value)
        }
        payload.obj("extra")?.fields?.forEach { (key, value) ->
            formatStatValue(key, value)?.let { out += CommunityStatLine(group, prettyStatId(key), it) }
        }
        payload.fields.filter { (key, _) -> key !in setOf("mainhand", "extra", "armor", "shield", "offhand") }
            .forEach { (key, value) ->
                formatStatValue(key, value)?.let { out += CommunityStatLine(group, prettyStatId(key), it) }
            }
    }

    private fun traitLines(statsRoot: JsonValue.Obj?): List<CommunityTraitLine> {
        if (statsRoot == null) return emptyList()
        val traits = statsRoot.obj("traits") ?: return emptyList()
        return traits.fields.mapNotNull { (key, value) ->
            val tiers = (value as? JsonValue.Arr)?.items?.mapNotNull { (it as? JsonValue.Num)?.value?.toLong() }
                ?: return@mapNotNull null
            if (tiers.isEmpty()) return@mapNotNull null
            CommunityTraitLine(prettyStatId(key), tiers.joinToString(" → "))
        }
    }

    private fun maxLevelKey(block: JsonValue.Obj): String? =
        block.fields.keys.maxByOrNull { it.toIntOrNull() ?: -1 }

    private fun formatStatValue(key: String, value: JsonValue): String? = when (value) {
        is JsonValue.Num -> value.value.toLong().toString()
        is JsonValue.Str -> value.value
        is JsonValue.Obj -> {
            val min = value.long("min")
            val max = value.long("max")
            when {
                min != null && max != null -> "$min – $max"
                max != null -> max.toString()
                else -> null
            }
        }
        else -> null
    }

    private fun prettyStatId(raw: String): String {
        val token = DisplayName.prettyEnum(raw)
        return when {
            token != null && token != raw && !token.contains('_') -> token
            else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
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
