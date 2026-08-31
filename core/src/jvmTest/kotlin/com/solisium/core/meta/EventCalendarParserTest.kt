package com.solisium.core.meta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventCalendarParserTest {
    @Test
    fun readsNamedEventsAndSkipsDisabledRows() {
        val json = """
            {"result":{"data":{"pageData":[
              {"id":"a","name":"Morokai Riftstone","mainCategory":"bossstone","isDisabled":false},
              {"id":"b","name":"Hidden","mainCategory":"bossstone","isDisabled":true},
              {"id":"c","name":"Carmine Forest Boonstone","mainCategory":"regionstone"}
            ],"pageCount":7,"currentPage":1}}}
        """.trimIndent()
        val page = EventCalendarParser.page(json)
        assertEquals(7, page.pageCount)
        assertEquals(listOf("Morokai Riftstone", "Carmine Forest Boonstone"), page.entries.map { it.name })
        assertEquals("bossstone", page.entries.first().category)
    }
}
