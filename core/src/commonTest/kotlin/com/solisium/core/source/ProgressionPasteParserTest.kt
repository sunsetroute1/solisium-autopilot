package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressionPasteParserTest {
    @Test
    fun freeformCompleteDailyContracts() {
        val snap = ProgressionPasteParser.parse(
            """
            Daily Contract: Hunt wolves — Complete
            Weekly contract: Field boss 0/3
            """.trimIndent(),
        )
        assertTrue("daily_contracts" in snap.completedIds)
        assertTrue(snap.items.any { it.id == "weekly_contracts" && !it.completed })
    }

    @Test
    fun progressFractionMarksComplete() {
        val snap = ProgressionPasteParser.parse("Daily codex explore 5/5")
        assertTrue("daily_codex" in snap.completedIds)
    }

    @Test
    fun jsonPaste() {
        val snap = ProgressionPasteParser.parse("""{"completed":["daily_dynamic"],"open":[{"id":"weekly_codex","detail":"Chapter 2"}]}""")
        assertEquals(setOf("daily_dynamic"), snap.completedIds)
        assertTrue(snap.items.any { it.id == "weekly_codex" && !it.completed })
    }
}
