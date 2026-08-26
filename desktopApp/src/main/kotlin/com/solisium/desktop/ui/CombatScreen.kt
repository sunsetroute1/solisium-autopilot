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
import com.solisium.desktop.FilePickers
import com.solisium.desktop.ImportOutcome
import com.solisium.desktop.Load
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun CombatScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Combat",
            subtitle = "Damage the game itself recorded, not a simulation",
            trailing = { CombatImportActions(model) },
        )
        Column(Modifier.padding(horizontal = Spacing.xxl)) {
            if (model.importing) {
                LoadingRow("Importing combat log")
                Spacer(Modifier.height(Spacing.md))
            }
            model.lastImport?.takeIf { it.label == "Combat logs" }?.let { outcome ->
                CombatImportOutcome(outcome)
                Spacer(Modifier.height(Spacing.md))
            }
        }
        when (val state = model.combat) {
            is Load.Loading -> LoadingRow("Reading sessions")
            is Load.Err -> Column(Modifier.padding(horizontal = Spacing.xxl)) { ErrorState(state.message) }
            is Load.Ok -> {
                val sessions = state.value
                if (sessions.isEmpty()) {
                    Column(Modifier.padding(horizontal = Spacing.xxl)) {
                        EmptyCombatState(model)
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
private fun CombatImportActions(model: AppModel) {
    val logs = model.detectedLogFolder
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ActionButton(
            label = "Import newest",
            onClick = { model.importCombatLogs(null) },
            primary = true,
            enabled = !model.importing && logs != null,
        )
        ActionButton(
            label = "Choose file",
            onClick = {
                FilePickers.pickFile("Select a combat log", ".txt", logs)
                    ?.let { model.importCombatLogs(it) }
            },
            enabled = !model.importing,
        )
        ActionButton(
            label = "Choose folder",
            onClick = {
                FilePickers.pickDirectory("Select a combat log folder", logs)
                    ?.let { model.importCombatLogs(it) }
            },
            enabled = !model.importing,
        )
    }
}

@Composable
private fun EmptyCombatState(model: AppModel) {
    val logs = model.detectedLogFolder
    Card {
        Text(
            "No combat logs imported",
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Text,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Throne and Liberty writes a log after combat ends when logging is enabled in game settings. " +
                "Import the newest log from your CombatLogs folder, or pick a file manually.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            logs?.toString() ?: "No CombatLogs folder found under %LOCALAPPDATA%\\TL\\Saved\\CombatLogs",
            style = MonoStyle,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActionButton(
                label = "Import newest",
                onClick = { model.importCombatLogs(null) },
                primary = true,
                enabled = !model.importing && logs != null,
            )
            ActionButton(
                label = "Choose file",
                onClick = {
                    FilePickers.pickFile("Select a combat log", ".txt", logs)
                        ?.let { model.importCombatLogs(it) }
                },
                enabled = !model.importing,
            )
            ActionButton(
                label = "Choose folder",
                onClick = {
                    FilePickers.pickDirectory("Select a combat log folder", logs)
                        ?.let { model.importCombatLogs(it) }
                },
                enabled = !model.importing,
            )
        }
        if (logs == null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Enable combat logging in game, or use Choose file if your logs are saved elsewhere.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun CombatImportOutcome(outcome: ImportOutcome) {
    val failed = outcome.error != null
    val tint = if (failed) Palette.Danger else Palette.Extracted
    Card(Modifier.fillMaxWidth()) {
        Text(
            if (failed) "Combat log import failed" else "Combat log imported",
            style = MaterialTheme.typography.titleSmall,
            color = tint,
        )
        Spacer(Modifier.height(Spacing.xs))
        if (failed) {
            Text(outcome.error!!, style = MonoStyle, color = Palette.TextMuted)
        } else {
            Text(
                "${formatLong(outcome.imported.toLong())} events mapped, ${formatLong(outcome.skipped.toLong())} skipped",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        outcome.warnings.take(4).forEach { warning ->
            Spacer(Modifier.height(2.dp))
            Text(warning, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
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
                    "log v${session.logVersion ?: "?"} · ${formatLong(session.eventCount)} events",
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
                value = formatLong(session.observedDamageSum),
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
            val ranked = session.skillTotals.sortedByDescending { it.observedDamageSum }
            val peak = ranked.first().observedDamageSum.coerceAtLeast(1L)
            ranked.forEach { total ->
                ShareBar(
                    label = total.skillName ?: total.skillId ?: "unnamed",
                    share = total.observedDamageSum.toDouble() / peak,
                    trailing = "${formatLong(total.hits)} hits · ${formatLong(total.observedDamageSum)}",
                )
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
