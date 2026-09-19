package com.solisium.core.query

import com.solisium.core.domain.GateOfMemoryRegion
import com.solisium.core.meta.MetaForgeThroneLiberty
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GateOfMemoryTimerTest {
    private val anchor = Instant.parse(MetaForgeThroneLiberty.FALLBACK_ANCHOR_ISO_NA)
    private val cycleMs = MetaForgeThroneLiberty.CYCLE_MS
    private val openMs = MetaForgeThroneLiberty.OPEN_MS
    private val denver = ZoneId.of("America/Denver")

    private fun timer(at: Instant) = GateOfMemoryTimer(
        clock = { at },
        anchorEpochMs = { anchor.toEpochMilli() },
    )

    @Test
    fun `just before open counts down to slot start`() {
        val slotStart = GateOfMemoryTimer.slotStartEpochMs(anchor.toEpochMilli(), anchor.toEpochMilli(), cycleMs)
        val now = Instant.ofEpochMilli(slotStart - 150_000)
        val plan = timer(now).plan(GateOfMemoryRegion.NA)
        assertFalse(plan.activeNow)
        assertEquals(150_000, plan.countdownMs)
        assertEquals(slotStart, plan.nextStartEpochMs)
    }

    @Test
    fun `active window counts down to close`() {
        val slotStart = anchor.toEpochMilli()
        val during = Instant.ofEpochMilli(slotStart + openMs / 2)
        val plan = timer(during).plan(GateOfMemoryRegion.NA)
        assertTrue(plan.activeNow)
        assertEquals(openMs / 2, plan.countdownMs)
        assertEquals(slotStart + cycleMs, plan.nextStartEpochMs)
    }

    @Test
    fun `gap after window targets next cycle`() {
        val slotStart = anchor.toEpochMilli()
        val after = Instant.ofEpochMilli(slotStart + openMs + 60_000)
        val plan = timer(after).plan(GateOfMemoryRegion.NA)
        assertFalse(plan.activeNow)
        assertEquals(cycleMs - openMs - 60_000, plan.countdownMs)
    }

    @Test
    fun `upcoming rows stay on 11806 second grid`() {
        val plan = timer(anchor).plan(GateOfMemoryRegion.NA)
        val starts = plan.upcoming.map { it.startsAtEpochMs }
        starts.zip(starts.drop(1)).forEach { (a, b) ->
            assertEquals(cycleMs, b - a)
        }
    }

    @Test
    fun `in-game NA opening is 741 PM Denver Sep 17`() {
        // User-observed NA slot, not MetaForge's copied EU 6:33 PM.
        val now = Instant.parse("2026-09-18T00:29:00Z")
        val plan = timer(now).plan(GateOfMemoryRegion.NA)
        val nextLocal = Instant.ofEpochMilli(plan.nextStartEpochMs).atZone(denver)
        assertEquals(19, nextLocal.hour)
        assertEquals(41, nextLocal.minute)
        assertFalse(plan.activeNow)
    }

    @Test
    fun `upcoming chain after 741 PM includes 1057 PM`() {
        val now = Instant.parse("2026-09-18T00:29:00Z")
        val plan = timer(now).plan(GateOfMemoryRegion.NA)
        val labels = plan.upcoming.take(2).map {
            Instant.ofEpochMilli(it.startsAtEpochMs).atZone(denver).let { z ->
                "${z.hour}:${z.minute.toString().padStart(2, '0')}"
            }
        }
        assertEquals(listOf("19:41", "22:57"), labels)
    }
}
