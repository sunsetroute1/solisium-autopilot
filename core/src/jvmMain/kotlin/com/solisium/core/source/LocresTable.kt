package com.solisium.core.source

import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Decoded Unreal locres strings. Accepts TL-Helper's `Game.locres` or a JSON
 * dump keyed as `Namespace|Key` / `Namespace::Key`.
 */
class LocresTable(
    private val entries: Map<String, String>,
    private val descBySkillName: Map<String, String>,
) : LocresLookup {
    override fun get(namespace: String, key: String): String? {
        val ns = namespace.trim()
        val row = key.trim()
        if (ns.isEmpty() || row.isEmpty()) return null
        return entries["$ns|$row"] ?: entries["$ns::$row"] ?: entries[row]
    }

    override fun skillDescriptionByName(skillName: String): String? {
        val folded = skillName.trim().lowercase()
        if (folded.isEmpty()) return null
        return descBySkillName[folded]
    }

    companion object {
        fun load(path: Path): LocresTable {
            val name = path.fileName.toString().lowercase()
            val raw = when {
                name.endsWith(".json") -> loadJson(Files.readString(path, StandardCharsets.UTF_8))
                else -> parseLocres(Files.readAllBytes(path))
            }
            return fromMap(raw)
        }

        fun fromMap(raw: Map<String, String>): LocresTable {
            val entries = LinkedHashMap<String, String>(raw.size * 2)
            raw.forEach { (key, value) ->
                if (value.isBlank()) return@forEach
                entries[key] = value
                when {
                    key.contains("::") -> entries[key.replaceFirst("::", "|")] = value
                    key.contains("|") -> entries[key.replaceFirst("|", "::")] = value
                }
            }
            return LocresTable(entries, indexSkillNames(entries))
        }

        internal fun parseLocres(bytes: ByteArray): Map<String, String> {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var version = 0
            if (bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                buf.position(MAGIC.size)
                version = buf.get().toInt() and 0xFF
            }
            val strings = ArrayList<String>()
            if (version >= 1) {
                val stringOffset = buf.long
                if (version >= 3) buf.int
                val saved = buf.position()
                if (stringOffset < 0 || stringOffset > bytes.size.toLong()) {
                    throw IllegalArgumentException("locres string table offset is out of range")
                }
                buf.position(stringOffset.toInt())
                val count = unsignedInt(buf)
                repeat(count) {
                    strings += readFString(buf)
                    if (version >= 3) buf.int
                }
                buf.position(saved)
            }
            val namespaces = unsignedInt(buf)
            val out = LinkedHashMap<String, String>()
            repeat(namespaces) {
                if (version >= 2) buf.int
                val ns = readFString(buf)
                val keyCount = unsignedInt(buf)
                repeat(keyCount) {
                    if (version >= 2) buf.int
                    val key = readFString(buf)
                    buf.int
                    val value = if (version >= 1) {
                        val index = buf.int
                        if (index in strings.indices) strings[index] else ""
                    } else {
                        readFString(buf)
                    }
                    val compound = if (ns.isEmpty()) key else "$ns::$key"
                    out[compound] = value
                }
            }
            return out
        }

        private fun loadJson(text: String): Map<String, String> {
            val root = JsonParser.parse(text) as? JsonValue.Obj
                ?: throw IllegalArgumentException("locres json must be an object")
            val out = LinkedHashMap<String, String>()
            root.fields.forEach { (key, value) ->
                val textValue = (value as? JsonValue.Str)?.value ?: return@forEach
                if (textValue.isNotBlank()) out[key] = textValue
            }
            return out
        }

        private fun indexSkillNames(entries: Map<String, String>): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            entries.forEach { (key, value) ->
                val name = value.trim()
                if (name.isEmpty()) return@forEach
                val pipe = key.indexOf('|').takeIf { it >= 0 } ?: key.indexOf("::").takeIf { it >= 0 } ?: return@forEach
                val sepLen = if (key[pipe] == '|') 1 else 2
                val ns = key.substring(0, pipe)
                val row = key.substring(pipe + sepLen)
                val descKey = when {
                    row.startsWith("TEXT_SKILL_NAME_") ->
                        "$ns${key.substring(pipe, pipe + sepLen)}TEXT_SKILL_DESC_${row.removePrefix("TEXT_SKILL_NAME_")}"
                    row.endsWith("_UIName") ->
                        "$ns${key.substring(pipe, pipe + sepLen)}${row.removeSuffix("_UIName")}_RankDescription_ValueIndex0"
                    else -> return@forEach
                }
                val desc = entries[descKey]?.trim().orEmpty()
                val fallback = if (desc.isEmpty() && row.endsWith("_UIName")) {
                    entries["$ns${key.substring(pipe, pipe + sepLen)}${row.removeSuffix("_UIName")}_UIDescription"]?.trim()
                } else {
                    desc
                }
                if (!fallback.isNullOrEmpty()) {
                    out.putIfAbsent(name.lowercase(), fallback)
                }
            }
            return out
        }

        private fun readFString(buf: ByteBuffer): String {
            val length = buf.int
            if (length == 0) return ""
            return if (length > 0) {
                val bytes = ByteArray(length)
                buf.get(bytes)
                val end = (length - 1).coerceAtLeast(0)
                String(bytes, 0, end, StandardCharsets.UTF_8)
            } else {
                val chars = -length
                val bytes = ByteArray(chars * 2)
                buf.get(bytes)
                val end = (chars - 1).coerceAtLeast(0) * 2
                String(bytes, 0, end, StandardCharsets.UTF_16LE)
            }
        }

        private fun unsignedInt(buf: ByteBuffer): Int {
            val value = buf.int
            if (value < 0) throw IllegalArgumentException("locres count overflowed")
            return value
        }

        private val MAGIC = byteArrayOf(
            0x0E, 0x14, 0x74, 0x75, 0x67, 0x4A, 0x03, 0xFC.toByte(),
            0x4A, 0x15, 0x90.toByte(), 0x9D.toByte(), 0xC3.toByte(), 0x37, 0x7F, 0x1B,
        )
    }
}
