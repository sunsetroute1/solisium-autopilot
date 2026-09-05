package com.solisium.core.source

import com.solisium.core.domain.GearWatermarkInput
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import java.nio.file.Files
import java.nio.file.Path

/** Persisted highest-drop watermark inputs under ~/.solisium/watermark.json. */
class GearWatermarkStore(
    private val home: Path = Path.of(System.getProperty("user.home"), ".solisium"),
) {
    fun path(): Path = home.resolve("watermark.json")

    fun load(): GearWatermarkInput? {
        val file = path()
        if (!Files.isRegularFile(file)) return null
        return runCatching {
            val root = JsonParser.parse(Files.readString(file))
            val weapon = root.long("weapon") ?: return@runCatching null
            val armor = root.long("armor") ?: return@runCatching null
            val accessory = root.long("accessory") ?: return@runCatching null
            GearWatermarkInput(weapon.toInt(), armor.toInt(), accessory.toInt())
        }.getOrNull()
    }

    fun save(input: GearWatermarkInput) {
        Files.createDirectories(home)
        val body = buildString {
            append("{\n")
            append("  \"version\": 1,\n")
            append("  \"weapon\": ${input.weapon},\n")
            append("  \"armor\": ${input.armor},\n")
            append("  \"accessory\": ${input.accessory}\n")
            append("}\n")
        }
        Files.writeString(path(), body)
    }

    private fun JsonValue.long(key: String): Long? = (this as? JsonValue.Obj)?.fields?.get(key)?.let {
        when (it) {
            is JsonValue.Num -> it.value.toLong()
            is JsonValue.Str -> it.value.toLongOrNull()
            else -> null
        }
    }
}
