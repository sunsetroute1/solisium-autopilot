package com.solisium.core.talkingwall

import com.solisium.core.json.JsonValue
import com.solisium.core.meta.TextNorm

data class ParsedTalkingWallStatement(
    val statement: String,
    val answerTrue: Boolean,
    val category: String? = null,
    val notes: String? = null,
)

object TalkingWallMapper {
    private val tableHints = listOf(
        "talkingwall",
        "talking_wall",
        "oxquiz",
        "quizquestion",
        "truefalse",
        "true_false",
        "adventurecodex",
        "worldquiz",
        "eventquiz",
    )

    fun considers(tableName: String): Boolean {
        val name = tableName.lowercase()
        if (tableHints.any { hint -> name.contains(hint) }) return true
        return name.contains("quiz") && (name.contains("question") || name.contains("ox") || name.contains("wall"))
    }

    fun parseWarehouseRow(
        tableName: String,
        rowId: String,
        nameLoc: String?,
        json: JsonValue,
    ): ParsedTalkingWallStatement? {
        val statement = statementText(json, nameLoc) ?: return null
        val answer = answerTrue(json) ?: return null
        return ParsedTalkingWallStatement(
            statement = statement,
            answerTrue = answer,
            category = category(json, tableName),
            notes = notes(json),
        )
    }

    fun statementKey(statement: String): String = TextNorm.fold(statement)

    private fun statementText(json: JsonValue, nameLoc: String?): String? {
        val candidates = listOf(
            json.str("Statement"),
            json.str("statement"),
            json.str("Question"),
            json.str("question"),
            json.str("Desc"),
            json.str("description"),
            json.str("Text"),
            json.str("text"),
            json.str("Message"),
            json.str("message"),
            json.str("Content"),
            json.str("content"),
            json.str("QuizText"),
            json.str("quiz_text"),
            nameLoc?.trim()?.takeIf { it.isNotEmpty() && it != "None" },
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun answerTrue(json: JsonValue): Boolean? =
        bool(json.str("AnswerTrue"))
            ?: bool(json.str("answer_true"))
            ?: bool(json.str("IsTrue"))
            ?: bool(json.str("is_true"))
            ?: bool(json.str("Correct"))
            ?: oxAnswer(json.str("OxAnswer"))
            ?: oxAnswer(json.str("ox_answer"))
            ?: oxAnswer(json.str("Answer"))
            ?: oxAnswer(json.str("answer"))
            ?: json.long("Answer")?.let { it != 0L }

    private fun category(json: JsonValue, tableName: String): String? =
        json.str("Category")
            ?: json.str("category")
            ?: json.str("Topic")
            ?: json.str("topic")
            ?: json.str("Chapter")
            ?: json.str("chapter")
            ?: tableName.substringAfter("TL", tableName).takeIf { considers(tableName) }

    private fun oxAnswer(raw: String?): Boolean? = when (raw?.trim()?.uppercase()) {
        "O", "TRUE", "T", "YES", "Y", "1" -> true
        "X", "FALSE", "F", "NO", "N", "0" -> false
        else -> null
    }

    private fun bool(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        "true", "t", "yes", "y", "1", "ebool::t" -> true
        "false", "f", "no", "n", "0", "ebool::f" -> false
        else -> null
    }

    private fun notes(json: JsonValue): String? {
        val dual = json.str("DualAnswer") ?: json.str("dual_answer")
        if (dual != null && dual.equals("true", ignoreCase = true)) {
            return "Patch notes: both true and false may be accepted in-game."
        }
        return json.str("Notes") ?: json.str("notes")
    }
}
