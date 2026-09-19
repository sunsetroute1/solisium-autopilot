package com.solisium.core.talkingwall

/**
 * Parses Earth's Memory / Talking Wall true-false statements from TL-Helper's
 * English localization CSV (collector output). Keys use Korean ox-quiz markers:
 * `_O_` = correct answer is true (circle), `_X_` = correct answer is false (cross).
 */
object TalkingWallLocresParser {
    private val QUIZ_KEY = Regex("""FE_Quiz_(\d+)_(O|X)_(\d+)_DialogueStr""")

    fun parseEnCsv(csvText: String): List<ParsedTalkingWallStatement> {
        val byKey = linkedMapOf<String, ParsedTalkingWallStatement>()
        csvText.lineSequence().forEach { line ->
            if (!line.contains("FE_Quiz_") || !line.contains("_DialogueStr")) return@forEach
            val match = QUIZ_KEY.find(line) ?: return@forEach
            val quizSet = match.groupValues[1]
            val ox = match.groupValues[2]
            val answerTrue = ox == "O"
            val rawText = extractDialogueColumn(line) ?: return@forEach
            val statement = normalizeStatement(rawText) ?: return@forEach
            val statementKey = TalkingWallMapper.statementKey(statement)
            byKey[statementKey] = ParsedTalkingWallStatement(
                statement = statement,
                answerTrue = answerTrue,
                category = "quiz_$quizSet",
                notes = "Client locres (FE_Quiz_$quizSet)",
            )
        }
        return byKey.values.toList()
    }

    internal fun extractDialogueColumn(line: String): String? {
        val marker = ",,"
        val idx = line.indexOf(marker)
        if (idx < 0) return null
        return line.substring(idx + marker.length).trim().takeIf { it.isNotEmpty() }
    }

    internal fun normalizeStatement(raw: String): String? {
        var text = raw.trim()
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length - 1).replace("\"\"", "\"")
        }
        if (text.endsWith(" Correct?")) {
            text = text.removeSuffix(" Correct?").trim()
        }
        if (text.endsWith("?")) {
            text = text.dropLast(1).trim()
        }
        return text.takeIf { it.length >= 8 }
    }
}
