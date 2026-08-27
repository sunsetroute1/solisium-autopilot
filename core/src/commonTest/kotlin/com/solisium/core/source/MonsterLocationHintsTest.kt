package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonsterLocationHintsTest {
    @Test
    fun guildRaidRowIdGetsRegionAndMapLabel() {
        val label = MonsterLocationHints.label("GR_L05_BossName", 12345L, "boss")
        assertTrue(label.contains("Guild raid"))
        assertTrue(label.contains("Region L05"))
        assertTrue(label.contains("Questlog map 12345"))
    }

    @Test
    fun fieldRowIdUsesOpenWorldHint() {
        val label = MonsterLocationHints.label("FD_L12_Wolf", null, null)
        assertEquals("Open world field · Region L12", label)
    }

    @Test
    fun unknownRowIdFallsBackToMapOnly() {
        assertEquals("Questlog map 99", MonsterLocationHints.label("UNKNOWN", 99L, null))
    }
}
