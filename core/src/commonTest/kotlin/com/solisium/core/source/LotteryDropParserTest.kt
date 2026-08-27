package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewardLotteryParserTest {
    @Test
    fun parsesNonNoneLotterySlots() {
        val json = """{"public_lottery_group_id":{"normal_01":"FD_L03_M_Golem_Talus_001","luck_01":"None"}}"""
        val slots = RewardLotteryParser.lotterySlots(json)
        assertEquals(mapOf("normal_01" to "FD_L03_M_Golem_Talus_001"), slots)
    }
}

class LotteryUnitIndexTest {
    @Test
    fun parsesUnitEntriesWithProbScale() {
        val unit = LotteryUnitIndex.parseUnit(
            "FD_L03_M_Golem_Talus_001",
            """{"ItemLotteryUnitEntry":[{"item":"fixture_bow","prob":2500000},{"item":"fixture_ore","prob":7500000}]}""",
        )
        assertEquals(2, unit.entries.size)
        assertEquals(0.25, unit.entries[0].probability)
        assertEquals(0.75, unit.entries[1].probability)
    }

    @Test
    fun resolvesGroupIdToDirectUnit() {
        val index = LotteryUnitIndex(
            listOf(
                WarehouseJsonRow(
                    "TLItemLotteryUnit",
                    "FD_L03_M_Golem_Talus_001",
                    """{"ItemLotteryUnitEntry":[{"item":"fixture_bow","prob":1000000}]}""",
                ),
            ),
        )
        assertEquals(listOf("FD_L03_M_Golem_Talus_001"), index.resolveUnitIds("FD_L03_M_Golem_Talus_001"))
        assertTrue(index.unit("FD_L03_M_Golem_Talus_001")!!.entries.single().itemId == "fixture_bow")
    }
}
