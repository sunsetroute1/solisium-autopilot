package com.solisium.core.query

import com.solisium.core.domain.GateOfMemoryRegion
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GateOfMemoryTimerTest {
    /** MetaForge NA grid point: Sep 16, 2026 9:46 AM Denver. */
    private val anchor = Instant.parse("2026-09-16T15:46:00Z")
    private val cycleMs = GateOfMemoryTimer.CYCLE_MINUTES * 60_000L
    private val openMs = GateOfMemoryTimer.OPEN_MINUTES * 60_000L
    private val denver = ZoneId.of("America/Denver")

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
    fun `metaforge sample gap before 946 AM`() {
        // MetaForge @ ~8:59 AM Denver: next open 9:46 AM, ~46m 25s out.
        val now = Instant.parse("2026-09-16T14:59:35Z")
        val plan = GateOfMemoryTimer { now }.plan(GateOfMemoryRegion.NA)
        val nextLocal = Instant.ofEpochMilli(plan.nextStartEpochMs).atZone(denver)
        assertEquals(9, nextLocal.hour)
        assertEquals(46, nextLocal.minute)
        assertFalse(plan.activeNow)
        assertEquals(anchor.toEpochMilli(), plan.nextStartEpochMs)
        assertEquals(2_785_000.0, plan.countdownMs.toDouble(), 5_000.0)
    }

    @Test
    fun `metaforge upcoming chain includes 102 PM`() {
        val now = Instant.parse("2026-09-16T14:59:35Z")
        val plan = GateOfMemoryTimer { now }.plan(GateOfMemoryRegion.NA)
        val labels = plan.upcoming.take(2).map {
            Instant.ofEpochMilli(it.startsAtEpochMs).atZone(denver).let { z ->
                "${z.hour}:${z.minute.toString().padStart(2, '0')}"
            }
        }
        assertEquals(listOf("9:46", "13:03"), labels)
    }
}
