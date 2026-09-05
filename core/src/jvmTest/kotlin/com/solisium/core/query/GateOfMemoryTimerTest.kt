package com.solisium.core.query

import com.solisium.core.domain.GateOfMemoryRegion
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GateOfMemoryTimerTest {
    private val anchor = Instant.parse("2026-09-05T04:17:00Z")
    private val cycleMs = GateOfMemoryTimer.CYCLE_MINUTES * 60_000L
    private val openMs = GateOfMemoryTimer.OPEN_MINUTES * 60_000L

    @Test
    fun `just before open counts down to anchor`() {
        val now = anchor.minusMillis(150_000)
        val plan = GateOfMemoryTimer { now }.plan(GateOfMemoryRegion.NA)
        assertFalse(plan.activeNow)
        assertEquals(150_000, plan.countdownMs)
        assertEquals(anchor.toEpochMilli(), plan.nextStartEpochMs)
    }

    @Test
    fun `active window counts down to close`() {
        val during = anchor.plusMillis(openMs / 2)
        val plan = GateOfMemoryTimer { during }.plan(GateOfMemoryRegion.NA)
        assertTrue(plan.activeNow)
        assertEquals(openMs / 2, plan.countdownMs)
        assertEquals(anchor.toEpochMilli() + cycleMs, plan.nextStartEpochMs)
    }

    @Test
    fun `gap after window targets next cycle`() {
        val after = anchor.plusMillis(openMs + 60_000)
        val plan = GateOfMemoryTimer { after }.plan(GateOfMemoryRegion.NA)
        assertFalse(plan.activeNow)
        assertEquals(cycleMs - openMs - 60_000, plan.countdownMs)
    }

    @Test
    fun `upcoming rows stay on 197 minute grid`() {
        val plan = GateOfMemoryTimer { anchor }.plan(GateOfMemoryRegion.NA)
        val starts = plan.upcoming.map { it.startsAtEpochMs }
        starts.zip(starts.drop(1)).forEach { (a, b) ->
            assertEquals(cycleMs, b - a)
        }
    }

    @Test
    fun `denver labels match metaforge sample`() {
        val plan = GateOfMemoryTimer { anchor.minusSeconds(150) }.plan(GateOfMemoryRegion.NA)
        val zone = ZoneId.of("America/Denver")
        val nextLocal = Instant.ofEpochMilli(plan.nextStartEpochMs).atZone(zone)
        assertEquals(22, nextLocal.hour)
        assertEquals(17, nextLocal.minute)
    }
}
