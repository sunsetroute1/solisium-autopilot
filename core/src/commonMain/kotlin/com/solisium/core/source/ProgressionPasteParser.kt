package com.solisium.core.source

import com.solisium.core.domain.LiveProgressionItem
import com.solisium.core.domain.LiveProgressionSnapshot
import com.solisium.core.domain.LiveProgressionSource
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue

/**
 * Turns commission-board / codex UI text (or a small JSON blob) into progression
 * completion hints. Paste from the game with Ctrl+A, Ctrl+C on a list screen.
 */
object ProgressionPasteParser {
    fun parse(text: String, syncedAtEpochMs: Long = 0L): LiveProgressionSnapshot {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return emptySnapshot(syncedAtEpochMs, listOf("Paste was empty."))
        }
        if (trimmed.startsWith("{")) {
            return parseJson(trimmed, syncedAtEpochMs)
        }
        return parseFreeform(trimmed, syncedAtEpochMs)
    }

    private fun parseJson(text: String, syncedAtEpochMs: Long): LiveProgressionSnapshot {
        return runCatching {
            val root = JsonParser.parse(text)
            val completed = root.arr("completed").mapNotNull { (it as? JsonValue.Str)?.value }.toSet()
            val open = root.arr("open").mapNotNull { entry ->
                val obj = entry as? JsonValue.Obj ?: return@mapNotNull null
                val id = obj.str("id") ?: return@mapNotNull null
                LiveProgressionItem(
                    id = id,
                    title = obj.str("title") ?: id,
                    detail = obj.str("detail").orEmpty(),
                    completed = false,
                    progress = obj.str("progress"),
                    source = LiveProgressionSource.Paste,
                    confidence = "paste-json",
                )
            }
            val items = open + completed.map { id ->
                LiveProgressionItem(
                    id = id,
                    title = id,
                    detail = "Marked complete in pasted JSON.",
                    completed = true,
                    source = LiveProgressionSource.Paste,
                    confidence = "paste-json",
                )
            }
            LiveProgressionSnapshot(
                items = items,
                completedIds = completed,
                sources = listOf("Structured JSON paste"),
                warnings = emptyList(),
                syncedAtEpochMs = syncedAtEpochMs,
            )
        }.getOrElse {
            parseFreeform(text, syncedAtEpochMs)
        }
    }

    private fun parseFreeform(text: String, syncedAtEpochMs: Long): LiveProgressionSnapshot {
        val items = mutableListOf<LiveProgressionItem>()
        val completed = mutableSetOf<String>()
        val warnings = mutableListOf<String>()
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val mapped = mapLine(line) ?: return@forEach
                items += mapped
                if (mapped.completed) completed += mapped.id
            }
        if (items.isEmpty()) {
            warnings += "No recognizable contract, codex, or event lines. Try copying the commission or codex list from the game."
        }
        return LiveProgressionSnapshot(
            items = items.distinctBy { it.id },
            completedIds = completed,
            sources = listOf("Freeform UI paste"),
            warnings = warnings,
            syncedAtEpochMs = syncedAtEpochMs,
        )
    }

    private fun mapLine(line: String): LiveProgressionItem? {
        val lower = line.lowercase()
        val id = when {
            "talking wall" in lower || "muro parlante" in lower -> "nix_talking_wall"
            "weekly" in lower && "contract" in lower -> "weekly_contracts"
            "daily" in lower && "contract" in lower -> "daily_contracts"
            "weekly" in lower && "codex" in lower -> "weekly_codex"
            "daily" in lower && "codex" in lower -> "daily_codex"
            "codex" in lower || "adventure" in lower && "chapter" in lower -> "daily_codex"
            "dynamic" in lower || "world event" in lower -> "daily_dynamic"
            "field boss" in lower || "world boss" in lower -> "weekly_boss"
            "skill core" in lower || "skillcore" in lower -> "always_skill_cores"
            "mastery" in lower -> "always_mastery"
            "season" in lower || "reward pass" in lower -> "monthly_season"
            "contract" in lower || "commission" in lower -> "daily_contracts"
            else -> return null
        }
        val progress = PROGRESS.find(line)?.value
        val done = isComplete(line, progress)
        return LiveProgressionItem(
            id = id,
            title = line.take(120),
            detail = if (done) "Marked complete from pasted UI text." else "In progress from pasted UI text.",
            completed = done,
            progress = progress,
            source = LiveProgressionSource.Paste,
            confidence = if (done) "paste-complete" else "paste-open",
        )
    }

    private fun isComplete(line: String, progress: String?): Boolean {
        val lower = line.lowercase()
        if (progress != null) {
            val parts = progress.split('/')
            if (parts.size == 2) {
                val current = parts[0].toIntOrNull()
                val total = parts[1].toIntOrNull()
                if (current != null && total != null && total > 0 && current >= total) return true
            }
        }
        return COMPLETE_MARKERS.any { lower.contains(it) } ||
            line.contains("✓") ||
            line.contains("✔") ||
            Regex("""\[\s*x\s*]""", RegexOption.IGNORE_CASE).containsMatchIn(line)
    }

    private fun emptySnapshot(syncedAtEpochMs: Long, warnings: List<String>) = LiveProgressionSnapshot(
        items = emptyList(),
        completedIds = emptySet(),
        sources = emptyList(),
        warnings = warnings,
        syncedAtEpochMs = syncedAtEpochMs,
    )

    private val PROGRESS = Regex("""(\d+)\s*/\s*(\d+)""")
    private val COMPLETE_MARKERS = listOf(
        "complete", "completed", "finished", "done", "turn in", "claimed", "100%",
    )
}

private fun JsonValue.arr(key: String): List<JsonValue> = when (this) {
    is JsonValue.Obj -> (fields[key] as? JsonValue.Arr)?.items.orEmpty()
    else -> emptyList()
}

private fun JsonValue.Obj.str(key: String): String? = (fields[key] as? JsonValue.Str)?.value
