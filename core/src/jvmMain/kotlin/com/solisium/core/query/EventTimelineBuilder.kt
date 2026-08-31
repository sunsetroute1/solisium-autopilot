package com.solisium.core.query

import com.solisium.core.domain.CommunityEventEntry
import com.solisium.core.domain.EventDayPlan
import com.solisium.core.domain.EventKind
import com.solisium.core.domain.EventRosterGroup
import com.solisium.core.domain.GameServer
import com.solisium.core.domain.MonsterProfile
import com.solisium.core.domain.TimelineSlot
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a T&L-style day timetable. Hour windows come from the community event
 * calendar cadence Questlog displays (dynamic every 3h, field bosses at 19:00 and
 * 22:00 in the selected server zone). Named bosses are a roster, not per-slot
 * assignments — Amazon does not publish a Western spawn API.
 */
class EventTimelineBuilder(
    private val clock: () -> Instant = { Instant.now() },
) {
    fun plan(
        server: GameServer,
        dayOffset: Int = 0,
        catalog: List<CommunityEventEntry> = emptyList(),
        warehouse: List<MonsterProfile> = emptyList(),
        fetchedAt: String? = null,
        warnings: List<String> = emptyList(),
    ): EventDayPlan {
        val zone = runCatching { ZoneId.of(server.zoneId) }.getOrDefault(ZoneId.of("UTC"))
        val now = clock().atZone(zone)
        val day = now.toLocalDate().plusDays(dayOffset.toLong())
        val start = day.atStartOfDay(zone)
        val slots = windows().map { window ->
            val begins = start.withHour(window.hour).withMinute(0).withSecond(0).withNano(0)
            val ends = begins.plusMinutes(window.minutes)
            TimelineSlot(
                startsAtEpochMs = begins.toInstant().toEpochMilli(),
                endsAtEpochMs = ends.toInstant().toEpochMilli(),
                hour = window.hour,
                minute = 0,
                kind = window.kind,
                title = window.kind.label,
                detail = window.detail,
                occurring = !now.isBefore(begins) && now.isBefore(ends),
                source = "community",
            )
        }.sortedBy { it.startsAtEpochMs }
        val upcoming = slots.filter { it.endsAtEpochMs > now.toInstant().toEpochMilli() }.take(8)
        val notes = listOf(
            "Times are ${server.regionLabel} local (${server.zoneId}).",
            "Windows follow the community event-type cadence (dynamic every 3 hours, field bosses at 19:00 and 22:00). Named bosses rotate per server and are not assigned to a slot.",
            "The in-game map timetable is still the live source. Amazon has not published a Western spawn API.",
        )
        return EventDayPlan(
            server = server,
            dayEpochMs = start.toInstant().toEpochMilli(),
            dayLabel = day.format(DAY_LABEL),
            zoneLabel = zoneLabel(now),
            slots = slots,
            upcoming = upcoming,
            roster = roster(catalog, warehouse),
            notes = notes,
            warnings = warnings,
            fetchedAt = fetchedAt,
            catalogCount = catalog.size,
        )
    }

    private fun roster(
        catalog: List<CommunityEventEntry>,
        warehouse: List<MonsterProfile>,
    ): List<EventRosterGroup> {
        val groups = linkedMapOf<EventKind, LinkedHashSet<String>>()
        fun put(kind: EventKind, name: String) {
            if (name.isBlank()) return
            groups.getOrPut(kind) { LinkedHashSet() }.add(name)
        }
        catalog.forEach { put(EventKind.fromCategory(it.category), it.name) }
        warehouse.forEach { monster ->
            val kind = when (monster.kindHint) {
                "field boss" -> EventKind.FieldBoss
                "world boss" -> EventKind.WorldBoss
                "guild raid" -> EventKind.Other
                else -> return@forEach
            }
            put(kind, monster.displayName)
        }
        return groups.map { (kind, names) ->
            val source = if (catalog.any { EventKind.fromCategory(it.category) == kind }) "community" else "warehouse"
            EventRosterGroup(kind, names.sorted(), source)
        }.filter { it.names.isNotEmpty() }
    }

    private fun zoneLabel(now: ZonedDateTime): String {
        val offset = now.offset
        val hours = offset.totalSeconds / 3600
        val sign = if (hours >= 0) "+" else ""
        return "${now.zone.id}  UTC$sign$hours"
    }

    private data class Window(val hour: Int, val minutes: Long, val kind: EventKind, val detail: String)

    companion object {
        private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

        /**
         * Community cadence observed on Questlog's public event calendar
         * (type windows, not named bosses).
         */
        private fun windows(): List<Window> {
            val dynamicHours = listOf(0, 3, 6, 9, 12, 15, 18, 21)
            val fieldHours = listOf(19, 22)
            return buildList {
                dynamicHours.forEach { hour ->
                    add(Window(hour, 30, EventKind.Dynamic, "Community 3-hour cadence"))
                }
                fieldHours.forEach { hour ->
                    add(Window(hour, 30, EventKind.FieldBoss, "Community field-boss window"))
                }
            }
        }
    }
}
