package com.solisium.core.query

import com.solisium.core.domain.GateOfMemoryPlan
import com.solisium.core.domain.GateOfMemoryRegion
import com.solisium.core.domain.GateOfMemoryWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Gate of Memory (Talking Wall) countdown — same cadence as MetaForge's timer:
 * 197 minutes between openings, 4-minute active window per region cluster.
 *
 * Anchor calibrated from MetaForge NA schedule (Sep 2026). Not an official Amazon API.
 */
class GateOfMemoryTimer(
    private val clock: () -> Instant = { Instant.now() },
) {
    fun plan(region: GateOfMemoryRegion, horizonHours: Int = 24): GateOfMemoryPlan {
        val zone = runCatching { ZoneId.of(region.zoneId) }.getOrDefault(ZoneId.of("UTC"))
        val nowMs = clock().toEpochMilli()
        val anchorMs = regionAnchorMs(region)
        val cycleMs = CYCLE_MINUTES * 60_000L
        val openMs = OPEN_MINUTES * 60_000L

        val slotIndex = Math.floorDiv(nowMs - anchorMs, cycleMs)
        val slotStartMs = anchorMs + slotIndex * cycleMs
        val slotEndMs = slotStartMs + openMs
        val activeNow = nowMs in slotStartMs until slotEndMs
        val nextStartMs = slotStartMs + cycleMs
        val nextEndMs = nextStartMs + openMs

        val countdownMs = if (activeNow) slotEndMs - nowMs else nextStartMs - nowMs

        val upcoming = buildList {
            var cursor = nextStartMs
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
        val headlineMs = if (activeNow) slotEndMs else nextStartMs
        val headlineZoned = Instant.ofEpochMilli(headlineMs).atZone(zone)
        val notes = listOf(
            "Times are ${region.label} server local (${region.zoneId}).",
            "Gate of Memory opens every ${CYCLE_MINUTES / 60}h ${CYCLE_MINUTES % 60}m for about $OPEN_MINUTES minutes (MetaForge cadence).",
            "Community schedule — confirm in-game when you can.",
        )
        return GateOfMemoryPlan(
            region = region,
            zoneLabel = zoneLabel(nowZoned),
            activeNow = activeNow,
            countdownMs = countdownMs.coerceAtLeast(0),
            nextStartEpochMs = nextStartMs,
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

    private fun regionAnchorMs(region: GateOfMemoryRegion): Long =
        ANCHOR_EPOCH_MS + regionPhaseOffsetMs(region)

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
        const val CYCLE_MINUTES = 197
        const val OPEN_MINUTES = 4

        /** Known NA window start used to calibrate MetaForge's 197-minute grid. */
        private val ANCHOR_EPOCH_MS: Long = Instant.parse("2026-09-05T04:17:00Z").toEpochMilli()

        /**
         * Per-region phase offsets. MetaForge exposes NA / EU / Asia selectors; when all
         * regions share the same global phase these stay zero.
         */
        private fun regionPhaseOffsetMs(region: GateOfMemoryRegion): Long = when (region) {
            GateOfMemoryRegion.NA -> 0L
            GateOfMemoryRegion.EU -> 0L
            GateOfMemoryRegion.ASIA -> 0L
        }

        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    }
}
