package com.solisium.core.talkingwall

import kotlin.test.Test
import kotlin.test.assertEquals

class TalkingWallWarehouseScannerTest {
    @Test
    fun countsParsedQuizRows() {
        val json = """{"Statement":"Nix is cold.","Answer":"O"}"""
        val report = TalkingWallWarehouseScanner.scan(
            listOf(
                TalkingWallWarehouseScanner.Row("TLTalkingWallQuestion", "q1", null, json),
                TalkingWallWarehouseScanner.Row("TLTalkingWallQuestion", "q2", null, """{"foo":1}"""),
                TalkingWallWarehouseScanner.Row("TLItemEquip", "x", null, json),
            ),
        )
        assertEquals(1, report.tables.size)
        assertEquals(2, report.tables.single().rowCount)
        assertEquals(1, report.parsedTotal)
        assertEquals(1, report.unparsedTotal)
    }
}
