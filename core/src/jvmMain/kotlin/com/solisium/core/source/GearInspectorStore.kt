package com.solisium.core.source

import com.solisium.core.domain.GearInspectorState
import com.solisium.core.domain.GearTraitSlot
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import com.solisium.core.query.GearInspectorModel
import java.nio.file.Files
import java.nio.file.Path

/** Persists gear-inspector rolls under ~/.solisium/gear-inspector.json. */
class GearInspectorStore(
    private val home: Path = Path.of(System.getProperty("user.home"), ".solisium"),
) {
    fun path(): Path = home.resolve("gear-inspector.json")

    fun load(): Map<String, GearInspectorState> {
        val file = path()
        if (!Files.isRegularFile(file)) return emptyMap()
        return runCatching {
            val root = JsonParser.parse(Files.readString(file))
            val items = root.obj("items") ?: return emptyMap()
            items.fields.mapNotNull { (key, value) ->
                parseItem(value)?.let { key to it }
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    fun save(items: Map<String, GearInspectorState>) {
        Files.createDirectories(home)
        val body = buildString {
            append("{\n  \"version\": 5,\n  \"items\": {\n")
            items.entries.forEachIndexed { index, (key, state) ->
                append("    ")
                append(jsonString(key))
                append(": ")
                append(serializeItem(state))
                if (index < items.size - 1) append(',')
                append('\n')
            }
            append("  }\n}\n")
        }
        Files.writeString(path(), body)
    }

    private fun parseItem(value: JsonValue): GearInspectorState? {
        val obj = value as? JsonValue.Obj ?: return null
        val slots = parseSlots(obj).ifEmpty { migrateLegacySlots(obj) }
        return GearInspectorState(
            itemLevel = obj.str("itemLevel").orEmpty(),
            slots = slots,
            resonanceTraitId = GearInspectorModel.normalizeResonanceKey(
                obj.str("resonanceTraitId").orEmpty(),
            ),
            resonanceTier = obj.long("resonanceTier")?.toInt()?.coerceIn(0, 4) ?: 0,
            potentialTraitId = obj.str("potentialTraitId").orEmpty(),
        )
    }

    private fun parseSlots(obj: JsonValue.Obj): List<GearTraitSlot> =
        obj.arr("slots").mapNotNull { entry ->
            val row = entry as? JsonValue.Obj ?: return@mapNotNull null
            GearTraitSlot(
                traitId = row.str("traitId").orEmpty(),
                tier = row.long("tier")?.toInt()?.coerceIn(0, 4) ?: 0,
            ).let { slot ->
                // Heal trait+tier0 leftovers from the old "re-click clears tier" UI bug.
                if (slot.traitId.isNotBlank() && slot.tier <= 0) slot.copy(tier = 1) else slot
            }
        }

    private fun migrateLegacySlots(obj: JsonValue.Obj): List<GearTraitSlot> {
        val traitTiers = obj.arr("traitTiers").mapNotNull { (it as? JsonValue.Num)?.value?.toInt() }
        if (traitTiers.isNotEmpty()) {
            return traitTiers.map { GearTraitSlot(tier = it.coerceIn(0, 4)) }
        }
        val fromRollLines = obj.arr("rollLines").mapNotNull { line ->
            val row = line as? JsonValue.Obj ?: return@mapNotNull null
            GearTraitSlot(
                traitId = row.str("traitId").orEmpty(),
                tier = if (row.str("value").isNullOrBlank()) 0 else 1,
            )
        }
        if (fromRollLines.isNotEmpty()) return fromRollLines
        return obj.arr("traitSlots").mapNotNull { entry ->
            val row = entry as? JsonValue.Obj ?: return@mapNotNull null
            GearTraitSlot(
                traitId = row.str("traitId").orEmpty(),
                tier = row.long("tier")?.toInt()?.coerceIn(0, 4) ?: 0,
            ).let { parsed ->
                if (parsed.traitId.isNotBlank() && parsed.tier <= 0) parsed.copy(tier = 1) else parsed
            }
        }
    }

    private fun serializeItem(state: GearInspectorState): String = buildString {
        append("{\n")
        append("      \"itemLevel\": ").append(jsonString(state.itemLevel)).append(",\n")
        append("      \"resonanceTraitId\": ").append(jsonString(state.resonanceTraitId)).append(",\n")
        append("      \"resonanceTier\": ").append(state.resonanceTier).append(",\n")
        append("      \"potentialTraitId\": ").append(jsonString(state.potentialTraitId)).append(",\n")
        append("      \"slots\": [\n")
        state.slots.forEachIndexed { index, slot ->
            append("        {\"traitId\": ")
            append(jsonString(slot.traitId))
            append(", \"tier\": ")
            append(slot.tier)
            append('}')
            if (index < state.slots.size - 1) append(',')
            append('\n')
        }
        append("      ]\n    }")
    }

    private fun jsonString(raw: String): String =
        buildString {
            append('"')
            raw.forEach { ch ->
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

    companion object {
        fun keyFor(sourceTable: String, sourceRowId: String): String =
            GearInspectorModel.rowKey(sourceTable, sourceRowId)
    }
}
