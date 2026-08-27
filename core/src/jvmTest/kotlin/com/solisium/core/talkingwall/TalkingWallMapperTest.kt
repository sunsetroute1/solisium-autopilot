package com.solisium.core.talkingwall

import com.solisium.core.db.JvmDatabase
import com.solisium.core.json.JsonValue
import com.solisium.core.query.CatalogQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TalkingWallMapperTest {
    @Test
    fun considersLikelyWarehouseTables() {
        assertTrue(TalkingWallMapper.considers("TLTalkingWallQuestion"))
        assertTrue(TalkingWallMapper.considers("TLWorldQuizOx"))
        assertFalse(TalkingWallMapper.considers("TLItemEquip"))
    }

    @Test
    fun parsesWarehouseJsonWithOxAnswer() {
        val json = JsonValue.Obj(
            mapOf(
                "Statement" to JsonValue.Str("Stonegard Castle has a passage to the port."),
                "Answer" to JsonValue.Str("X"),
                "Category" to JsonValue.Str("regions"),
            ),
        )
        val parsed = TalkingWallMapper.parseWarehouseRow("TLTalkingWall", "row_1", null, json)!!
        assertFalse(parsed.answerTrue)
        assertEquals("Stonegard Castle has a passage to the port.", parsed.statement)
    }

    @Test
    fun seedsCommunityStatementsIntoDatabase() {
        val db = JvmDatabase.inMemory()
        db.schemaQueries.insertSnapshot(
            id = "snap",
            source = "test",
            extracted_at = "2026-01-01T00:00:00Z",
            game_build = "test",
            game_version = "test",
            schema_version = 10L,
            source_path = null,
            source_hash = null,
            decoder_version = null,
            active = 1L,
        )
        val json = """
            {"statements":[{"id":"a","statement":"Nix is cold.","answerTrue":true,"category":"nix"}]}
        """.trimIndent()
        val summary = TalkingWallImporter.supplementCommunity(db, "snap", json)
        assertEquals(1, summary.communityAdded)
        assertEquals(1L, CatalogQuery(db).talkingWallCoverage("snap").total)
    }
}
