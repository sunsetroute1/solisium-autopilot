package com.solisium.core.source

import com.solisium.core.domain.LiveProgressionItem
import com.solisium.core.domain.LiveProgressionSnapshot
import com.solisium.core.domain.LiveProgressionSource
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Reads NCSoft local storage cache the game writes under Saved\Config. This is
 * not the hashed SaveGames blob — it is UI/sync state the client refreshes while
 * you play. Signals are inferred, not authoritative server progress.
 */
class TlLocalProgressReader(
    private val env: (String) -> String? = { System.getenv(it) },
) {
    fun read(now: Instant = Instant.now()): LiveProgressionSnapshot {
        val path = TlLocalPaths.ncStorageLocalData(env)
            ?: return empty(now, listOf("NCStorageLocalData.ini not found. Launch T&L once so Saved\\Config exists."))
        return readFile(path, now)
    }

    fun readFile(path: Path, now: Instant = Instant.now()): LiveProgressionSnapshot {
        val text = runCatching { Files.readString(path) }.getOrElse {
            return empty(now, listOf("Could not read ${path.fileName}: ${it.message}"))
        }
        val entries = NcStorageIni.parse(text)
        fun entry(vararg keys: String): JsonValue.Obj? =
            keys.firstNotNullOfOrNull { key -> entries.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value }

        val items = mutableListOf<LiveProgressionItem>()
        val completed = mutableSetOf<String>()
        val sources = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (entries.isEmpty()) {
            warnings += "NCStorageLocalData.ini was read but no entries parsed — file format may have changed."
        }

        entry("kWeeklyRewardPass")?.let { obj ->
            val season = obj.long("WeeklyRewardPass_NP_Season_Id")
            val pins = obj.arr("WeeklyRewardPass_NP_Pin_Info").size
            items += LiveProgressionItem(
                id = "monthly_season",
                title = "Weekly reward pass",
                detail = "Season ${season ?: "?"} · $pins pinned reward(s) in local cache.",
                completed = false,
                progress = pins.takeIf { it > 0 }?.let { "$it pinned" },
                source = LiveProgressionSource.LocalConfig,
                confidence = "derived",
            )
            sources += "kWeeklyRewardPass"
        }

        entry("KTIMEATTACKDUNGEON")?.let { obj ->
            val pending = obj.arr("TimeAttackNotifyList").size
            if (pending > 0) {
                items += LiveProgressionItem(
                    id = "live:time_attack",
                    title = "Time attack dungeons",
                    detail = "$pending dungeon notification(s) in local cache — likely unclaimed or newly available.",
                    completed = false,
                    progress = "$pending pending",
                    source = LiveProgressionSource.LocalConfig,
                    confidence = "derived",
                )
            }
            sources += "KTIMEATTACKDUNGEON"
        }

        entry("KCHALLENGEPARTYDUNGEON")?.let { obj ->
            val season = obj.str("LastCheckedWorldSeasonId") ?: obj.long("LastCheckedWorldSeasonId")?.toString()
            val period = obj.long("LastCheckedWorldPeriodId")
            items += LiveProgressionItem(
                id = "live:party_dungeon",
                title = "Challenge party dungeon",
                detail = "Last tracked season $season · period $period in local cache.",
                completed = false,
                source = LiveProgressionSource.LocalConfig,
                confidence = "derived",
            )
            sources += "KCHALLENGEPARTYDUNGEON"
        }

        entry("KCONTENTSALARM")?.let { obj ->
            val favorites = obj.arr("FavoriteGuid").size + obj.arr("FavoriteAdvGuid").size
            if (favorites > 0) {
                items += LiveProgressionItem(
                    id = "live:content_favorites",
                    title = "Pinned commission / content board entries",
                    detail = "$favorites favorite slot(s) on the in-game content alarm board.",
                    completed = false,
                    progress = "$favorites pinned",
                    source = LiveProgressionSource.LocalConfig,
                    confidence = "derived",
                )
            }
            sources += "KCONTENTSALARM"
        }

        entry("KCODEXALARM")?.let { obj ->
            val codexKeys = obj.fields.keys.filter { key ->
                key.startsWith("TEXT_CODEX") && obj.bool(key) == true
            }
            if (codexKeys.isNotEmpty()) {
                val labels = codexKeys.take(6).joinToString(", ") { it.removePrefix("TEXT_CODEX_").replace('_', ' ') }
                items += LiveProgressionItem(
                    id = "daily_codex",
                    title = "Codex categories you track",
                    detail = "Alarm enabled for: $labels${if (codexKeys.size > 6) "… (+${codexKeys.size - 6} more)" else ""}.",
                    completed = false,
                    progress = "${codexKeys.size} tracked",
                    source = LiveProgressionSource.LocalConfig,
                    confidence = "derived",
                )
            }
            sources += "KCODEXALARM"
        }

        entry("KCRAFTINGALARM")?.let { obj ->
            val crafts = obj.arr("CraftingAlarmList").size
            if (crafts > 0) {
                items += LiveProgressionItem(
                    id = "daily_materials",
                    title = "Crafting alarms",
                    detail = "$crafts crafting reminder(s) active in local cache.",
                    completed = false,
                    source = LiveProgressionSource.LocalConfig,
                    confidence = "derived",
                )
            }
        }

        if (sources.isEmpty() && entries.isNotEmpty()) {
            warnings += "NCStorageLocalData.ini parsed ${entries.size} keys but none mapped to progression tasks yet."
        } else if (sources.isEmpty()) {
            warnings += "NCStorageLocalData.ini parsed but no known progression keys were present."
        } else {
            warnings += "Local config shows client-side hints only — not verified server completion state."
            warnings += "Updated while the game runs; sync again after opening commission/codex screens."
        }

        return LiveProgressionSnapshot(
            items = items,
            completedIds = completed,
            sources = sources.distinct(),
            warnings = warnings,
            syncedAtEpochMs = now.toEpochMilli(),
        )
    }

    private fun empty(now: Instant, warnings: List<String>) = LiveProgressionSnapshot(
        items = emptyList(),
        completedIds = emptySet(),
        sources = emptyList(),
        warnings = warnings,
        syncedAtEpochMs = now.toEpochMilli(),
    )
}

/** Parses KEY="escaped json wrapper" lines from NCStorageLocalData.ini. */
internal object NcStorageIni {
    fun parse(text: String): Map<String, JsonValue.Obj> {
        val out = linkedMapOf<String, JsonValue.Obj>()
        val linePattern = Regex("""^([Kk][A-Za-z0-9_]+)="(.*)"\s*$""")
        text.lineSequence().forEach { raw ->
            val match = linePattern.matchEntire(raw.trim()) ?: return@forEach
            val key = match.groupValues[1]
            val wrapped = unescapeIniValue(match.groupValues[2])
            val obj = runCatching { JsonParser.parse(wrapped) as? JsonValue.Obj }.getOrNull() ?: return@forEach
            val payload = obj.str("OBJ") ?: return@forEach
            val inner = parseInnerObj(payload) ?: return@forEach
            out[key] = inner
        }
        return out
    }

    private fun parseInnerObj(payload: String): JsonValue.Obj? {
        val trimmed = payload.trim()
        return runCatching { JsonParser.parse(trimmed) as? JsonValue.Obj }.getOrNull()
            ?: runCatching { JsonParser.parse(unescapePayload(trimmed)) as? JsonValue.Obj }.getOrNull()
    }

    /** T&L stores JSON inside ini quotes with doubled backslashes (\\r\\n, \\t, \\"). */
    private fun unescapeIniValue(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'r' -> if (i + 3 < raw.length && raw[i + 2] == '\\' && raw[i + 3] == 'n') {
                        out.append('\n')
                        i += 4
                        continue
                    }
                    'n' -> {
                        out.append('\n')
                        i += 2
                        continue
                    }
                    't' -> {
                        out.append('\t')
                        i += 2
                        continue
                    }
                    '"' -> {
                        out.append('"')
                        i += 2
                        continue
                    }
                    '\\' -> {
                        out.append('\\')
                        i += 2
                        continue
                    }
                }
            }
            out.append(raw[i])
            i++
        }
        return out.toString()
    }

    private fun unescapePayload(raw: String): String = unescapeIniValue(raw)
}

private fun JsonValue.Obj.str(key: String): String? = (fields[key] as? JsonValue.Str)?.value

private fun JsonValue.Obj.long(key: String): Long? = when (val value = fields[key]) {
    is JsonValue.Num -> value.value.toLong()
    is JsonValue.Str -> value.value.toLongOrNull()
    else -> null
}

private fun JsonValue.Obj.bool(key: String): Boolean? = when (val value = fields[key]) {
    is JsonValue.Bool -> value.value
    is JsonValue.Str -> value.value.equals("true", ignoreCase = true)
    else -> null
}

private fun JsonValue.Obj.arr(key: String): List<JsonValue> =
    (fields[key] as? JsonValue.Arr)?.items.orEmpty()
