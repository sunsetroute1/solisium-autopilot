package com.solisium.core.query

import com.solisium.core.domain.GateOfMemoryPlan
import com.solisium.core.domain.GateOfMemoryRegion
import com.solisium.core.domain.GateOfMemoryWindow
import com.solisium.core.meta.MetaForgeThroneLiberty
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Gate of Memory countdown: 11806 s cycle, 240 s open window.
 * NA uses an in-game opening (7:41 PM Denver on 2026-09-17). MetaForge's public
 * page currently copies the EU anchor onto NA, which lands ~1h 7m early.
 */
class GateOfMemoryTimer(
    private val clock: () -> Instant = { Instant.now() },
    private val anchorEpochMs: () -> Long = {
        MetaForgeThroneLiberty.fallbackAnchorEpochMs("na")
    },
) {
    fun plan(region: GateOfMemoryRegion, horizonHours: Int = 24): GateOfMemoryPlan {
        val zone = runCatching { ZoneId.of(region.zoneId) }.getOrDefault(ZoneId.of("UTC"))
        val nowMs = clock().toEpochMilli()
        val anchorMs = anchorEpochMs()
        val cycleMs = MetaForgeThroneLiberty.CYCLE_MS
        val openMs = MetaForgeThroneLiberty.OPEN_MS

        val slotStartMs = slotStartEpochMs(nowMs, anchorMs, cycleMs)
        val slotEndMs = slotStartMs + openMs
        val activeNow = nowMs in slotStartMs until slotEndMs
        val nextOpenMs = if (activeNow || nowMs >= slotEndMs) {
            slotStartMs + cycleMs
        } else {
            slotStartMs
        }
        val nextEndMs = nextOpenMs + openMs

        val countdownMs = when {
            activeNow -> slotEndMs - nowMs
            nowMs < slotStartMs -> slotStartMs - nowMs
            else -> nextOpenMs - nowMs
        }

        val upcoming = buildList {
            var cursor = nextOpenMs
            val limitMs = nowMs + horizonHours * 3_600_000L
            while (cursor <= limitMs && size < 8) {
                val windowEnd = cursor + openMs
                add(
                    GateOfMemoryWindow(
                        startsAtEpochMs = cursor,
                        endsAtEpochMs = windowEnd,
                        active = nowMs in cursor until windowEnd,
                        startsInMs = (cursor - nowMs).coerceAtLeast(0),
                    ),
                )
                cursor += cycleMs
            }
        }

        val nowZoned = Instant.ofEpochMilli(nowMs).atZone(zone)
        val headlineMs = if (activeNow) slotEndMs else nextOpenMs
        val headlineZoned = Instant.ofEpochMilli(headlineMs).atZone(zone)
        val notes = listOf(
            "Times are ${region.label} server local (${region.zoneId}).",
            "Gate of Memory opens every ${MetaForgeThroneLiberty.cycleLabel()} for about ${OPEN_MINUTES} minutes.",
            if (region == GateOfMemoryRegion.NA) {
                "NA times are from an in-game opening, not MetaForge's copied EU anchor."
            } else {
                "EU/Asia times follow MetaForge's published anchor. Confirm in-game when you can."
            },
        )
        return GateOfMemoryPlan(
            region = region,
            zoneLabel = zoneLabel(nowZoned),
            activeNow = activeNow,
            countdownMs = countdownMs.coerceAtLeast(0),
            nextStartEpochMs = nextOpenMs,
            nextEndEpochMs = nextEndMs,
            nextStartLabel = if (activeNow) {
                "Event ends at ${headlineZoned.format(TIME)}"
            } else {
                formatStartLabel(headlineZoned, nowZoned)
            },
            upcoming = upcoming,
            notes = notes,
        )
    }

    private fun zoneLabel(now: ZonedDateTime): String {
        val hours = now.offset.totalSeconds / 3600
        val sign = if (hours >= 0) "+" else ""
        return "${now.zone.id}  UTC$sign$hours"
    }

    private fun formatStartLabel(start: ZonedDateTime, now: ZonedDateTime): String {
        val dayDiff = start.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        val time = start.format(TIME)
        return when (dayDiff) {
            0L -> "Starts today at $time"
            1L -> "Starts tomorrow at $time"
            else -> "Starts ${start.format(DAY)} at $time"
        }
    }

    companion object {
        /** Kept for tests; cycle length is [MetaForgeThroneLiberty.CYCLE_MS]. */
        const val CYCLE_SECONDS = 11_806
        const val OPEN_MINUTES = 4

        fun slotStartEpochMs(nowMs: Long, anchorMs: Long, cycleMs: Long = MetaForgeThroneLiberty.CYCLE_MS): Long {
            val slotIndex = Math.floorDiv(nowMs - anchorMs, cycleMs)
            return anchorMs + slotIndex * cycleMs
        }

        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    }
}
