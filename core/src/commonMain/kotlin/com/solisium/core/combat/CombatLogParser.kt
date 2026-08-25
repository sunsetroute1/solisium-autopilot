package com.solisium.core.combat

data class CombatLogParseResult(
    val version: String?,
    val events: List<ParsedCombatEvent>,
    val errors: List<String>,
)

data class ParsedCombatEvent(
    val timestamp: String?,
    val logType: String,
    val skillName: String?,
    val skillId: String?,
    val damage: Long?,
    val critical: Boolean?,
    val heavy: Boolean?,
    val hitType: String?,
    val actor: String?,
    val target: String?,
    val hit: Boolean?,
    val miss: Boolean?,
    val rawLine: String,
)

object CombatLogParser {
    fun parse(text: String): CombatLogParseResult {
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return CombatLogParseResult(version = null, events = emptyList(), errors = listOf("empty combat log"))
        }
        val header = lines.first()
        val headerParts = splitCsv(header)
        val version = when {
            headerParts.size >= 2 && headerParts[0].equals("CombatLogVersion", ignoreCase = true) -> headerParts[1]
            else -> null
        }
        val errors = mutableListOf<String>()
        if (version == null) {
            errors.add("missing CombatLogVersion header")
        }
        val events = mutableListOf<ParsedCombatEvent>()
        val dataLines = if (version != null) lines.drop(1) else lines
        dataLines.forEachIndexed { index, line ->
            val cols = splitCsv(line)
            if (cols.size < 2) {
                errors.add("line ${index + 2}: expected CSV fields, found ${cols.size}")
                events.add(
                    ParsedCombatEvent(
                        timestamp = null,
                        logType = "PARSE_ERROR",
                        skillName = null,
                        skillId = null,
                        damage = null,
                        critical = null,
                        heavy = null,
                        hitType = null,
                        actor = null,
                        target = null,
                        hit = null,
                        miss = null,
                        rawLine = line,
                    ),
                )
                return@forEachIndexed
            }
            val logType = cols.getOrNull(1) ?: "UNKNOWN"
            events.add(
                ParsedCombatEvent(
                    timestamp = cols.getOrNull(0),
                    logType = logType,
                    skillName = cols.getOrNull(2),
                    skillId = cols.getOrNull(3),
                    damage = cols.getOrNull(4)?.toLongOrNull(),
                    critical = parseFlag(cols.getOrNull(5)),
                    heavy = parseFlag(cols.getOrNull(6)),
                    hitType = cols.getOrNull(7),
                    actor = cols.getOrNull(8),
                    target = cols.getOrNull(9),
                    hit = if (logType.equals("DamageDone", ignoreCase = true)) true else null,
                    miss = if (logType.equals("DamageDone", ignoreCase = true)) false else null,
                    rawLine = line,
                ),
            )
        }
        return CombatLogParseResult(version = version, events = events, errors = errors)
    }

    private fun parseFlag(raw: String?): Boolean? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> null
        }
    }

    internal fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        out.add(current.toString().trim())
        return out
    }
}
