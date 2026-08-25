package com.solisium.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.desktop.AppModel
import com.solisium.desktop.Load
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun CombatScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Combat", "Damage the game itself recorded, not a simulation")
        when (val state = model.combat) {
            is Load.Loading -> LoadingRow("Reading sessions")
            is Load.Err -> Column(Modifier.padding(horizontal = Spacing.xxl)) { ErrorState(state.message) }
            is Load.Ok -> {
                val sessions = state.value
                if (sessions.isEmpty()) {
                    Column(Modifier.padding(horizontal = Spacing.xxl)) {
                        Card {
                            Text(
                                "No combat logs imported",
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.Text,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "Throne and Liberty writes a log after combat ends. Import one to see " +
                                    "observed damage per skill.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextMuted,
                            )
                            Spacer(Modifier.height(Spacing.md))
                            CodeLine("solisium import --source combat-log")
                        }
                    }
                    return@Column
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.xxl)) {
                    items(sessions, key = { it.sessionId }) { session ->
                        SessionCard(session)
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: CombatSessionSummary) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.startedAt ?: "unknown start",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
                Text(
                    "log v${session.logVersion ?: "?"} · ${session.eventCount.format()} events",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
            Badge("observed", Palette.Extracted)
        }

        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(
                label = "Observed damage",
                value = session.observedDamageSum.format(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Observed DPS",
                value = session.observedDps?.let { "%.1f".format(it) } ?: "—",
                hint = "sum ÷ log duration",
                modifier = Modifier.weight(1f),
            )
        }

        if (session.skillTotals.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            SectionLabel("By skill")
            Spacer(Modifier.height(Spacing.sm))
            session.skillTotals.sortedByDescending { it.observedDamageSum }.forEach { total ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        total.skillName ?: total.skillId ?: "unnamed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        "${total.hits.format()} hits",
                        style = MonoStyle,
                        color = Palette.TextFaint,
                        modifier = Modifier.padding(end = Spacing.md),
                    )
                    Text(total.observedDamageSum.format(), style = MonoStyle, color = Palette.Text)
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Text(
            "Logs record DamageDone only. Healing, buffs and uptime are absent, so this is " +
                "not a full performance picture.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}
