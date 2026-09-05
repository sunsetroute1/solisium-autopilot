package com.solisium.core.source

import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import java.nio.file.Files
import java.nio.file.Path

/** Manual progression completion flags under ~/.solisium/progression.json. */
class ProgressionStore(
    private val home: Path = Path.of(System.getProperty("user.home"), ".solisium"),
) {
    fun path(): Path = home.resolve("progression.json")

    fun loadAll(): Map<String, Set<String>> {
        val file = path()
        if (!Files.isRegularFile(file)) return emptyMap()
        return runCatching {
            val root = JsonParser.parse(Files.readString(file))
            val chars = root.obj("characters") ?: return emptyMap()
            chars.fields.mapNotNull { (id, value) ->
                parseCharacter(value)?.let { id to it }
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    fun load(characterId: String): Set<String> = loadAll()[characterId].orEmpty()

    fun save(characterId: String, completed: Set<String>) {
        val all = loadAll().toMutableMap()
        if (completed.isEmpty()) {
            all.remove(characterId)
        } else {
            all[characterId] = completed
        }
        persist(all)
    }

    private fun persist(characters: Map<String, Set<String>>) {
        Files.createDirectories(home)
        val body = buildString {
            append("{\n  \"version\": 1,\n  \"characters\": {\n")
            characters.entries.forEachIndexed { index, (id, tasks) ->
                append("    ")
                append(jsonString(id))
                append(": {\n      \"completed\": [")
                tasks.sorted().forEachIndexed { taskIndex, taskId ->
                    append(jsonString(taskId))
                    if (taskIndex < tasks.size - 1) append(", ")
                }
                append("]\n    }")
                if (index < characters.size - 1) append(',')
                append('\n')
            }
            append("  }\n}\n")
        }
        Files.writeString(path(), body)
    }

    private fun parseCharacter(value: JsonValue): Set<String>? {
        val obj = value as? JsonValue.Obj ?: return null
        return obj.arr("completed").mapNotNull { (it as? JsonValue.Str)?.value }.toSet()
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
}
