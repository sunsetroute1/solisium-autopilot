package com.solisium.core.talkingwall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TalkingWallLocresParserTest {
    @Test
    fun parsesOxMarkersFromCsvLine() {
        val csv = """
            TLDialogue_L13,D_L13_FE_Quiz_0010_O_001_DialogueStr,,The Snow Fiends are demons that appear in the Scar of Sacrifice. Correct?
            TLDialogue_L13,D_L13_FE_Quiz_0010_X_001_DialogueStr,,"Tumgir," who fell in Tumgir's Grave, is the name of a giant golem. Correct?"
            TLDialogue_L13,D_L13_FE_Quiz_0030_X_029_DialogueStr,,Roen only uses crossbows. Correct?
        """.trimIndent()
        val rows = TalkingWallLocresParser.parseEnCsv(csv)
        assertEquals(3, rows.size)
        val snow = rows.single { it.statement.contains("Snow Fiends") }
        assertTrue(snow.answerTrue)
        val tumgir = rows.single { it.statement.contains("Tumgir") }
        assertFalse(tumgir.answerTrue)
        assertFalse(rows.single { it.statement.contains("crossbows") }.answerTrue)
    }

    @Test
    fun normalizeStripsCorrectSuffix() {
        assertEquals(
            "Nix is cold",
            TalkingWallLocresParser.normalizeStatement("Nix is cold? Correct?"),
        )
    }
}
