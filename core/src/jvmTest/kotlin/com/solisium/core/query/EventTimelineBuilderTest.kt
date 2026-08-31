package com.solisium.core.query

import com.solisium.core.domain.CommunityEventEntry
import com.solisium.core.domain.EventKind
import com.solisium.core.domain.MonsterProfile
import com.solisium.core.meta.GameServers
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventTimelineBuilderTest {
    @Test
    fun fieldBossWindowSitsAtNineteenInServerZone() {
        val server = GameServers.find("Adentus")!!
        val noon = ZonedDateTime.of(2026, 8, 29, 12, 0, 0, 0, ZoneId.of(server.zoneId)).toInstant()
        val plan = EventTimelineBuilder { noon }.plan(server)
        val field = plan.slots.filter { it.kind == EventKind.FieldBoss }
        assertEquals(listOf(19, 22), field.map { it.hour })
        assertTrue(plan.slots.any { it.kind == EventKind.Dynamic && it.hour == 15 })
        assertEquals("Adentus", plan.server.name)
    }

    @Test
    fun switchingRegionMovesTheSameLocalHour() {
        val east = GameServers.find("NA East (any)")!!
        val seoul = GameServers.find("Korea (any)")!!
        val instant = Instant.parse("2026-08-29T16:00:00Z")
        val eastPlan = EventTimelineBuilder { instant }.plan(east)
        val seoulPlan = EventTimelineBuilder { instant }.plan(seoul)
        val eastStart = eastPlan.slots.first { it.kind == EventKind.FieldBoss && it.hour == 19 }.startsAtEpochMs
        val seoulStart = seoulPlan.slots.first { it.kind == EventKind.FieldBoss && it.hour == 19 }.startsAtEpochMs
        assertTrue(eastStart != seoulStart)
    }

    @Test
    fun rosterUsesCatalogNamesNotInventedSpawns() {
        val server = GameServers.default
        val plan = EventTimelineBuilder { Instant.parse("2026-08-29T16:00:00Z") }.plan(
            server = server,
            catalog = listOf(CommunityEventEntry("1", "Morokai Riftstone", "bossstone")),
            warehouse = listOf(
                MonsterProfile("TLRewardNpcFoItem", "FD_L03_M_Golem_Talus_001", "Talus", "field boss", "L03"),
            ),
        )
        val rift = plan.roster.single { it.kind == EventKind.Riftstone }
        assertEquals(listOf("Morokai Riftstone"), rift.names)
        val field = plan.roster.single { it.kind == EventKind.FieldBoss }
        assertEquals(listOf("Talus"), field.names)
        assertTrue(plan.slots.none { it.title.contains("Morokai") })
    }
}
