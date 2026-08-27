package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertEquals

class RewardRowIdParserTest {
    @Test
    fun prettyNameFromFieldBossRowId() {
        assertEquals("Golem Talus", RewardRowIdParser.prettyName("FD_L03_M_Golem_Talus_001"))
    }

    @Test
    fun profileMarksFieldBossKind() {
        val profile = RewardRowIdParser.profile("FD_L03_M_Golem_Talus_001")
        assertEquals("field boss", profile.kindHint)
        assertEquals("L03", profile.levelHint)
    }
}
