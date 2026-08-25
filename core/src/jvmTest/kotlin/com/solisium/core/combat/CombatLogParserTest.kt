package com.solisium.core.combat

import com.solisium.core.db.JvmDatabase
import com.solisium.core.source.CombatLogDataSource
import com.solisium.core.source.ImportRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatLogParserTest {
    @Test
    fun parsesVersion4DamageDoneAndKeepsUnknownLogTypes() {
        val text = javaClass.getResource("/combat-log-v4-fixture.txt")!!.readText()
        val parsed = CombatLogParser.parse(text)
        assertEquals("4", parsed.version)
        assertEquals(4, parsed.events.size)
        assertTrue(parsed.errors.isEmpty())

        val first = parsed.events[0]
        assertEquals("DamageDone", first.logType)
        assertEquals(100L, first.damage)
        assertEquals(false, first.critical)
        assertEquals(false, first.heavy)

        val heavy = parsed.events[1]
        assertEquals(true, heavy.heavy)
        assertEquals(228L, heavy.damage)

        val crit = parsed.events[2]
        assertEquals(true, crit.critical)
        assertEquals("kMaxDamageByCriticalDecision", crit.hitType)

        val unknown = parsed.events[3]
        assertEquals("UnknownFutureType", unknown.logType)
        assertEquals("ExampleSkill", unknown.skillName)
    }

    @Test
    fun importStoresEventsWithoutClaimingModeledDps() {
        val text = javaClass.getResource("/combat-log-v4-fixture.txt")!!.readText()
        val db = JvmDatabase.inMemory()
        val receipt = CombatLogDataSource().importInto(db, ImportRequest(content = text, path = "fixture.txt"))
        assertEquals(4, receipt.recordsImported)
        val sessionId = receipt.sessionId!!
        assertEquals(4L, db.schemaQueries.countCombatEvents(sessionId).executeAsOne())
        assertEquals(478L, db.schemaQueries.sumCombatDamage(sessionId).executeAsOne())
        val types = db.schemaQueries.selectCombatEvents(sessionId).executeAsList().map { it.log_type }
        assertTrue(types.contains("UnknownFutureType"))
        assertFalse(types.contains("HealDone"))
        val summary = com.solisium.core.query.CatalogQuery(db).combatSummary(sessionId)!!
        assertEquals(4L, summary.eventCount)
        assertEquals(478L, summary.observedDamageSum)
        assertEquals("4", summary.logVersion)
        assertTrue(summary.observedDps != null && summary.observedDps!! > 0.0)
        val skill = summary.skillTotals.single()
        assertEquals("ExampleSkill", skill.skillName)
        assertEquals("1", skill.skillId)
        assertEquals(478L, skill.observedDamageSum)
        assertEquals(4L, skill.hits)
    }
}
