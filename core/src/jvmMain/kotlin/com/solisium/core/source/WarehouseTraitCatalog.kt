package com.solisium.core.source

import com.solisium.core.domain.CommunityTraitLine
import com.solisium.core.domain.ItemTraitCandidate
import com.solisium.core.domain.ItemTraitProfile
import com.solisium.core.domain.QuestlogItemOverlay
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Reads per-item trait pools from the TL-Helper warehouse (`TLItemTraitGroup` row id = item row id).
 * Questlog lists every candidate stat; the warehouse pool is the source of truth for what can roll.
 */
class WarehouseTraitCatalog(
    private val warehousePath: Path,
    private val traitNames: Map<String, String>,
) {
    private val statKeyLabels: Map<String, String> = traitNames.mapNotNull { (traitId, name) ->
        val key = TraitStatKeys.toQuestlogKey(traitId, emptyList())
        key to name
    }.toMap()

    fun profile(itemRowId: String, questlog: QuestlogItemOverlay?): ItemTraitProfile? {
        if (!Files.isRegularFile(warehousePath)) return questlogFallback(itemRowId, questlog)
        return runCatching {
            DriverManager.getConnection("jdbc:sqlite:${warehousePath.toAbsolutePath()}").use { connection ->
                val groupJson = connection.prepareStatement(
                    "SELECT raw_json FROM records WHERE table_name = 'TLItemTraitGroup' AND row_id = ? LIMIT 1",
                ).use { stmt ->
                    stmt.setString(1, itemRowId)
                    stmt.executeQuery().let { rs -> if (rs.next()) rs.getString(1) else null }
                } ?: return@use questlogFallback(itemRowId, questlog)

                val group = JsonParser.parse(groupJson)
                val questlogTiers = questlogTierMap(questlog)
                val enchantValues = loadEnchantValues(connection)
                val baseValues = loadBaseValues(connection)
                val candidates = group.arr("TraitCandidates").mapNotNull { entry ->
                    val obj = entry as? JsonValue.Obj ?: return@mapNotNull null
                    val traitId = obj.str("TraitId") ?: return@mapNotNull null
                    val baseSeed = obj.long("BaseSeed")?.toInt() ?: 3
                    val traitJson = loadTraitJson(connection, traitId) ?: return@mapNotNull null
                    val questlogKey = TraitStatKeys.toQuestlogKey(
                        traitId,
                        traitJson.arr("TraitStat").mapNotNull { (it as? JsonValue.Str)?.value },
                    )
                    val statKey = statFieldFor(traitJson, traitId)
                    ItemTraitCandidate(
                        traitId = traitId,
                        label = resolveLabel(traitId, statKey, questlog),
                        statKey = statKey,
                        tierValues = tierValues(
                            questlogKey = questlogKey,
                            questlogTiers = questlogTiers,
                            statKey = statKey,
                            baseSeed = baseSeed,
                            baseValues = baseValues,
                            enchantValues = enchantValues,
                        ),
                        baseSeed = baseSeed,
                    )
                }
                if (candidates.isEmpty()) return@use questlogFallback(itemRowId, questlog)

                val uniqueCandidates = group.arr("UniqueTraitCandidates").mapNotNull { entry ->
                    val obj = entry as? JsonValue.Obj ?: return@mapNotNull null
                    val traitId = obj.str("TraitId") ?: return@mapNotNull null
                    val baseSeed = obj.long("BaseSeed")?.toInt() ?: 1
                    val traitJson = loadTraitJson(connection, traitId)
                    val questlogKey = traitJson?.let {
                        TraitStatKeys.toQuestlogKey(
                            traitId,
                            it.arr("TraitStat").mapNotNull { token -> (token as? JsonValue.Str)?.value },
                        )
                    }
                    val statKey = traitJson?.let { statFieldFor(it, traitId) }
                    ItemTraitCandidate(
                        traitId = traitId,
                        label = resolveLabel(traitId, statKey ?: "", questlog),
                        statKey = statKey ?: "",
                        tierValues = tierValues(
                            questlogKey = questlogKey,
                            questlogTiers = questlogTiers,
                            statKey = statKey ?: "",
                            baseSeed = baseSeed,
                            baseValues = baseValues,
                            enchantValues = enchantValues,
                        ),
                        baseSeed = baseSeed,
                    )
                }

                ItemTraitProfile(
                    slotCount = ItemTraitSlots.countFor(itemRowId)
                        .coerceAtLeast(group.long("InitCount")?.toInt() ?: 1),
                    candidates = candidates,
                    uniqueCandidates = uniqueCandidates,
                    resonanceCandidates = buildResonanceCandidates(questlog),
                    source = ItemTraitProfile.SOURCE_WAREHOUSE,
                )
            }
        }.getOrElse { questlogFallback(itemRowId, questlog) }
    }

    private fun questlogFallback(itemRowId: String, questlog: QuestlogItemOverlay?): ItemTraitProfile? {
        val lines = questlog?.traitLines.orEmpty()
        if (lines.isEmpty()) return null
        return ItemTraitProfile(
            slotCount = ItemTraitSlots.countFor(itemRowId).coerceAtMost(lines.size).coerceAtLeast(1),
            candidates = lines.map { it.toCandidate() },
            resonanceCandidates = buildResonanceCandidates(questlog),
            source = ItemTraitProfile.SOURCE_QUESTLOG,
        )
    }

    private fun buildResonanceCandidates(questlog: QuestlogItemOverlay?): List<ItemTraitCandidate> =
        questlog?.resonanceLines.orEmpty().map { line ->
            val statKey = line.key.ifBlank { TraitStatKeys.camelToSnake(line.label.replace(' ', '_')) }
            ItemTraitCandidate(
                traitId = statKey,
                label = resolveLabel(statKey, statKey, questlog),
                statKey = statKey,
                tierValues = parseTierLine(line),
            )
        }

    private fun traitIdForStatKey(statKey: String): String =
        traitNames.keys.firstOrNull { traitId ->
            TraitStatKeys.toQuestlogKey(traitId, emptyList()) == statKey
        } ?: statKey

    private fun resolveLabel(traitId: String, statKey: String, questlog: QuestlogItemOverlay?): String {
        traitNames[traitId]?.trim()?.takeIf { it.isNotEmpty() && !it.equals(traitId, ignoreCase = true) }
            ?.let { return it }
        statKeyLabels[statKey]?.let { return it }
        questlog?.resonanceLines.orEmpty()
            .firstOrNull { it.key == statKey }
            ?.label
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        questlog?.traitLines.orEmpty()
            .firstOrNull { it.key == statKey }
            ?.label
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return fallbackTraitLabel(traitId)
    }

    private fun questlogTierMap(questlog: QuestlogItemOverlay?): Map<String, List<String>> =
        questlog?.traitLines.orEmpty()
            .filter { it.key.isNotBlank() }
            .associate { it.key to parseTierLine(it) }

    private fun CommunityTraitLine.toCandidate(): ItemTraitCandidate {
        val statKey = key.ifBlank { TraitStatKeys.camelToSnake(label.replace(' ', '_')) }
        val traitId = statKey
        return ItemTraitCandidate(
            traitId = traitId,
            label = resolveLabel(traitId, statKey, null).ifBlank { label },
            statKey = statKey,
            tierValues = parseTierLine(this),
        )
    }

    private fun parseTierLine(line: CommunityTraitLine): List<String> =
        line.tiers.split(Regex("""\s*(?:→|->|/|,)\s*"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun loadTraitJson(connection: java.sql.Connection, traitId: String): JsonValue.Obj? {
        val raw = connection.prepareStatement(
            "SELECT raw_json FROM records WHERE table_name = 'TLItemTraits' AND row_id = ? LIMIT 1",
        ).use { stmt ->
            stmt.setString(1, traitId)
            stmt.executeQuery().let { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: return null
        return JsonParser.parse(raw) as? JsonValue.Obj
    }

    private fun loadBaseValues(connection: java.sql.Connection): Map<Int, JsonValue.Obj> {
        val raw = connection.prepareStatement(
            "SELECT raw_json FROM records WHERE table_name = 'TLItemTraitsBaseValue' AND row_id = 'trait_base' LIMIT 1",
        ).use { stmt ->
            stmt.executeQuery().let { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: return emptyMap()
        return JsonParser.parse(raw).arr("Stats").mapNotNull { row ->
            val obj = row as? JsonValue.Obj ?: return@mapNotNull null
            val seed = obj.long("seed")?.toInt() ?: return@mapNotNull null
            seed to obj
        }.toMap()
    }

    private fun loadEnchantValues(connection: java.sql.Connection): List<JsonValue.Obj> {
        val raw = connection.prepareStatement(
            "SELECT raw_json FROM records WHERE table_name = 'TLItemTraitsEnchantValue' AND row_id = 'trait_enchant' LIMIT 1",
        ).use { stmt ->
            stmt.executeQuery().let { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: return emptyList()
        return JsonParser.parse(raw).arr("Stats").mapNotNull { it as? JsonValue.Obj }
    }

    private fun statFieldFor(traitJson: JsonValue.Obj, traitId: String): String {
        val token = traitJson.arr("TraitStat").firstOrNull()?.let { (it as? JsonValue.Str)?.value }
            ?.substringAfter("::")
            ?.removePrefix("k")
            ?: traitId.removePrefix("k")
        return TraitStatKeys.camelToSnake(token)
    }

    private fun tierValues(
        questlogKey: String?,
        questlogTiers: Map<String, List<String>>,
        statKey: String,
        baseSeed: Int,
        baseValues: Map<Int, JsonValue.Obj>,
        enchantValues: List<JsonValue.Obj>,
    ): List<String> {
        questlogKey?.let { questlogTiers[it]?.takeIf { tiers -> tiers.isNotEmpty() } }?.let { return it.take(4) }
        if (statKey.isNotBlank()) {
            questlogTiers[statKey]?.takeIf { it.isNotEmpty() }?.let { return it.take(4) }
        }
        if (statKey.isNotBlank()) {
            val baseRow = baseValues[baseSeed]
            return (1..4).map { tier ->
                val base = baseRow?.long(statKey) ?: baseRow?.double(statKey)?.toLong() ?: 0L
                val enchantRow = enchantValues.getOrNull(tier - 1)
                val enchant = enchantRow?.long(statKey) ?: enchantRow?.double(statKey)?.toLong() ?: 0L
                (base + enchant).takeIf { it != 0L }?.toString() ?: "—"
            }
        }
        return List(4) { "—" }
    }
}

private fun JsonValue.Obj.str(key: String): String? = (fields[key] as? JsonValue.Str)?.value

private fun JsonValue.Obj.long(key: String): Long? = when (val value = fields[key]) {
    is JsonValue.Num -> value.value.toLong()
    is JsonValue.Str -> value.value.toLongOrNull()
    else -> null
}

private fun JsonValue.Obj.double(key: String): Double? = when (val value = fields[key]) {
    is JsonValue.Num -> value.value
    is JsonValue.Str -> value.value.toDoubleOrNull()
    else -> null
}
