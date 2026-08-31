package com.solisium.core.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonParserTest {
    @Test
    fun parsesNestedObjectsArraysAndTypes() {
        val value = JsonParser.parse(
            """
            {
              "schema": "solisium.manual-character",
              "schemaVersion": 1,
              "flag": true,
              "missing": null,
              "nested": { "name": "Hero" },
              "items": [ { "slot": "head" }, { "slot": "chest" } ]
            }
            """.trimIndent(),
        )
        assertEquals("solisium.manual-character", value.str("schema"))
        assertEquals(1L, value.long("schemaVersion"))
        assertEquals(true, value.bool("flag"))
        assertEquals("Hero", value.obj("nested")?.str("name"))
        assertEquals(listOf("head", "chest"), value.arr("items").map { it.str("slot")!! })
    }

    @Test
    fun rejectsTrailingContent() {
        assertFailsWith<JsonParseException> {
            JsonParser.parse("""{"a":1}  true""")
        }
    }

    @Test
    fun unescapesStrings() {
        val value = JsonParser.parse("""{"notes":"line\nand \"quote\""}""")
        assertEquals("line\nand \"quote\"", value.str("notes"))
    }

    @Test
    fun stripsALeadingUtf8Bom() {
        val value = JsonParser.parse("\uFEFF{\"stage\":\"failed\",\"at\":\"decode\"}")
        assertEquals("failed", value.str("stage"))
        assertEquals("decode", value.str("at"))
    }

    @Test
    fun looksUpSnakeAndCamelKeys() {
        val value = JsonParser.parse("""{"combat_power": 12, "itemLevel": 9}""")
        assertEquals(12L, value.longAny("combatPower", "combat_power"))
        assertEquals(9L, value.longAny("item_level", "itemLevel"))
        assertTrue(value.strAny("missing", "also_missing") == null)
    }
}
