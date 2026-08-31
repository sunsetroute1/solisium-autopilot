package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.EventDayPlan
import com.solisium.core.domain.EventKind
import com.solisium.core.domain.GameServer
import com.solisium.core.domain.TimelineSlot
import com.solisium.core.meta.GameServers
import com.solisium.desktop.AppModel
import com.solisium.desktop.Load
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EventsScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Event timeline",
            "Server-local windows the way the in-game map timetable groups them. Named bosses are a roster, not live Amazon spawns.",
        ) {
            ActionButton(
                if (model.eventRefreshing) "Refreshing…" else "Refresh community roster",
                onClick = { model.refreshEventCatalog() },
                primary = true,
                enabled = !model.eventRefreshing,
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xxl),
        ) {
            ServerPicker(model)
            Spacer(Modifier.height(Spacing.md))
            DayStrip(model)
            Spacer(Modifier.height(Spacing.lg))
            when (val state = model.eventPlan) {
                is Load.Loading -> LoadingRow("Laying out the day")
                is Load.Err -> ErrorState(state.message)
                is Load.Ok -> TimelineBody(state.value)
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun ServerPicker(model: AppModel) {
    Card(Modifier.fillMaxWidth()) {
        Text("Server", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Pick the region your character plays on. Times shift to that zone. Named rotations still differ per world.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(Spacing.md))
        GameServers.regions().forEach { region ->
            val servers = GameServers.inRegion(region)
            val label = servers.first().regionLabel
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Spacer(Modifier.height(Spacing.xs))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                servers.forEach { server ->
                    ActionButton(
                        server.name,
                        onClick = { model.pickEventServer(server) },
                        primary = model.eventServer.key == server.key,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Or type the world name", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            BasicSearchField(
                value = model.eventServerQuery,
                onValueChange = model::onEventServerQuery,
                placeholder = "Adentus, Syleus…",
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(Palette.SurfaceHigh)
                    .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = Spacing.md, vertical = 8.dp),
            )
            ActionButton("Use", onClick = { model.commitEventServerQuery() }, enabled = model.eventServerQuery.isNotBlank())
        }
        val characterServer = model.characterServerHint()
        if (characterServer != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Character sheet says \"$characterServer\".",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun DayStrip(model: AppModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        (0..6).forEach { offset ->
            val label = when (offset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> "Day +$offset"
            }
            ActionButton(
                label,
                onClick = { model.pickEventDay(offset) },
                primary = model.eventDayOffset == offset,
            )
        }
    }
}

@Composable
private fun TimelineBody(plan: EventDayPlan) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Card(Modifier.weight(1.35f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.dayLabel, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    Text(
                        "${plan.server.name}  ·  ${plan.zoneLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                    )
                }
                Badge("community cadence", Palette.Unverified, caps = false)
            }
            Spacer(Modifier.height(Spacing.md))
            HourRail(plan)
            Spacer(Modifier.height(Spacing.md))
            plan.notes.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(bottom = 3.dp))
            }
            plan.warnings.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Card(Modifier.fillMaxWidth()) {
                SectionLabel("Upcoming")
                Spacer(Modifier.height(Spacing.sm))
                if (plan.upcoming.isEmpty()) {
                    Text("No remaining windows on this day.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                } else {
                    plan.upcoming.forEach { slot ->
                        UpcomingRow(slot, plan.server)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                SectionLabel("Boss / event roster")
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    if (plan.catalogCount > 0) {
                        "${plan.catalogCount} named rows from Questlog. Warehouse field bosses are mixed in when present."
                    } else {
                        "Refresh the community roster to pull named riftstones and boonstones from Questlog."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
                Spacer(Modifier.height(Spacing.md))
                if (plan.roster.isEmpty()) {
                    Text("No named bosses loaded yet.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                } else {
                    plan.roster.forEach { group ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EventKindPip(group.kind)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(group.kind.label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                            Spacer(Modifier.width(Spacing.sm))
                            Badge(group.source, if (group.source == "warehouse") Palette.Extracted else Palette.Unverified, caps = false)
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            group.names.take(16).joinToString("  ·  ") + if (group.names.size > 16) "  ·  +${group.names.size - 16}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextMuted,
                        )
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
                plan.fetchedAt?.let {
                    Text("Fetched $it", style = MonoStyle, color = Palette.TextFaint)
                }
            }
        }
    }
}

@Composable
private fun HourRail(plan: EventDayPlan) {
    val byHour = plan.slots.groupBy { it.hour }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Base).padding(Spacing.sm)) {
        (0..23).forEach { hour ->
            val here = byHour[hour].orEmpty()
            val nowHour = here.any { it.occurring }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (nowHour) Palette.Accent.copy(alpha = 0.10f) else Color.Transparent)
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    hourLabel(hour),
                    style = MonoStyle,
                    color = if (nowHour) Palette.Accent else Palette.TextFaint,
                    modifier = Modifier.width(48.dp),
                )
                if (here.isEmpty()) {
                    Box(Modifier.weight(1f).height(1.dp).background(Palette.Border.copy(alpha = 0.45f)))
                } else {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        here.forEach { EventChip(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventChip(slot: TimelineSlot) {
    val color = eventColor(slot.kind)
    Row(
        Modifier.clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EventKindPip(slot.kind)
        Spacer(Modifier.width(6.dp))
        Text(slot.title, style = MaterialTheme.typography.bodySmall, color = color)
        if (slot.occurring) {
            Spacer(Modifier.width(6.dp))
            Text("NOW", style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
        }
    }
}

@Composable
private fun UpcomingRow(slot: TimelineSlot, server: GameServer) {
    val zone = runCatching { ZoneId.of(server.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val whenText = Instant.ofEpochMilli(slot.startsAtEpochMs).atZone(zone).format(TIME)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        EventKindPip(slot.kind)
        Spacer(Modifier.width(Spacing.sm))
        Text(whenText, style = MonoStyle, color = Palette.TextMuted, modifier = Modifier.width(56.dp))
        Text(slot.title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
        if (slot.occurring) Badge("now", Palette.Accent, caps = false)
    }
}

@Composable
private fun EventKindPip(kind: EventKind) {
    Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(eventColor(kind)))
}

private fun eventColor(kind: EventKind): Color = when (kind) {
    EventKind.Dynamic -> Palette.Cool
    EventKind.FieldBoss -> Palette.Danger
    EventKind.WorldBoss -> Palette.Gold
    EventKind.Archboss -> Palette.Epic
    EventKind.Boonstone -> Palette.Uncommon
    EventKind.Riftstone -> Palette.Rare
    EventKind.Carrier -> Palette.Derived
    EventKind.Other -> Palette.TextMuted
}

private fun hourLabel(hour: Int): String = "%02d:00".format(hour)

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
