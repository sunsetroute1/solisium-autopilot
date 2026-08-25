package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.DatasetSnapshot
import com.solisium.desktop.AppModel
import com.solisium.desktop.FilePickers
import com.solisium.desktop.ImportOutcome
import com.solisium.desktop.Load
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun DataScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Data", "Bring data in, and choose which dataset the app reads from.")
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.xxl)) {
            item {
                ImportPanel(model)
                Spacer(Modifier.height(Spacing.xl))
            }
            item {
                KeyPanel(model)
                Spacer(Modifier.height(Spacing.xl))
            }
            item {
                SectionLabel("Datasets")
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    AppModel.databasePath().toString(),
                    style = MonoStyle,
                    color = Palette.TextFaint,
                )
                Spacer(Modifier.height(Spacing.md))
            }
            when (val state = model.snapshots) {
                is Load.Loading -> item { LoadingRow("Reading snapshots") }
                is Load.Err -> item { ErrorState(state.message) }
                is Load.Ok -> if (state.value.isEmpty()) {
                    item {
                        EmptyState(
                            "No datasets yet",
                            "Import game data above to populate the catalog.",
                        )
                    }
                } else {
                    items(state.value, key = { it.id }) { snapshot ->
                        SnapshotCard(snapshot) { model.activate(snapshot.id) }
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPanel(model: AppModel) {
    Card(Modifier.fillMaxWidth()) {
        Text("Import", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Nothing is sent anywhere and nothing is written back to the game.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(Spacing.lg))

        val warehouse = model.detectedWarehouse
        ImportRow(
            title = "Game data",
            detail = warehouse?.toString() ?: "No TL-Helper warehouse detected",
            detected = warehouse != null,
            busy = model.importing,
            primaryLabel = "Import",
            onPrimary = warehouse?.let { { model.importWarehouse(it) } },
            onChoose = {
                FilePickers.pickFile("Select a TL-Helper warehouse", ".sqlite", warehouse?.parent)
                    ?.let { model.importWarehouse(it) }
            },
        )
        Divider(Modifier.padding(vertical = Spacing.md))

        val logs = model.detectedLogFolder
        ImportRow(
            title = "Combat log",
            detail = logs?.toString() ?: "No CombatLogs folder found; enable logging in game settings",
            detected = logs != null,
            busy = model.importing,
            primaryLabel = "Import newest",
            onPrimary = logs?.let { { model.importCombatLogs(null) } },
            onChoose = {
                FilePickers.pickFile("Select a combat log", ".txt", logs)
                    ?.let { model.importCombatLogs(it) }
            },
        )
        Divider(Modifier.padding(vertical = Spacing.md))

        ImportRow(
            title = "Character",
            detail = "A loadout JSON you wrote by hand. The game exposes no character API.",
            detected = false,
            busy = model.importing,
            primaryLabel = null,
            onPrimary = null,
            onChoose = {
                FilePickers.pickFile("Select a character JSON", ".json")
                    ?.let { model.importCharacter(it) }
            },
        )

        if (model.importing) {
            Spacer(Modifier.height(Spacing.md))
            LoadingRow("Importing. Large warehouses take a few seconds.")
        }
        model.lastImport?.let {
            Spacer(Modifier.height(Spacing.md))
            OutcomeStrip(it)
        }
    }
}

/**
 * Key finder.
 *
 * The panel deliberately never displays a key. A candidate is identified by a short
 * fingerprint and the place it came from, which is enough to choose between two
 * candidates and to confirm later that the right one was stored.
 */
@Composable
private fun KeyPanel(model: AppModel) {
    val state = model.keys
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Archive key", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Spacer(Modifier.weight(1f))
            if (state.stored.isEmpty()) Badge("none stored", Palette.TextFaint)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Only needed to extract game files yourself. This build ships without one.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            model.secretStorePath.toString(),
            style = MonoStyle,
            color = Palette.TextFaint,
        )

        if (state.stored.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            state.stored.forEach { ref ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Bold(ref.name)
                        Spacer(Modifier.height(2.dp))
                        Text("fingerprint ${ref.fingerprint}", style = MonoStyle, color = Palette.TextFaint)
                    }
                    ActionButton("Remove", { model.forgetKey(ref.name) })
                }
                Spacer(Modifier.height(Spacing.sm))
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActionButton(
                label = if (state.scanning) "Searching" else "Find my key",
                onClick = { model.scanForKeys() },
                primary = state.stored.isEmpty(),
                enabled = !state.scanning,
            )
            ActionButton(
                label = "Search a folder",
                onClick = {
                    FilePickers.pickDirectory("Choose the folder holding your key")
                        ?.let { model.scanForKeys(it) }
                },
                enabled = !state.scanning,
            )
        }

        if (state.scanning) {
            Spacer(Modifier.height(Spacing.md))
            LoadingRow("Searching a few likely folders")
        }

        if (state.candidates.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                if (state.candidates.size == 1) {
                    "Found one key. Check the location looks right, then save it."
                } else {
                    "Found ${state.candidates.size} keys. Pick the one from the folder you trust."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(Spacing.sm))
            state.candidates.forEach { candidate ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = Spacing.md)) {
                        Bold("fingerprint ${candidate.fingerprint}")
                        Spacer(Modifier.height(2.dp))
                        Text(candidate.source, style = MonoStyle, color = Palette.TextFaint)
                        Text(
                            "recognised by ${candidate.evidence}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextFaint,
                        )
                    }
                    ActionButton("Save this key", { model.storeKey(candidate) }, primary = true)
                }
                Spacer(Modifier.height(Spacing.sm))
            }
        }

        state.message?.let {
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.weight(1f))
                ActionButton("Dismiss", { model.dismissKeyMessage() })
            }
        }
    }
}

@Composable
private fun ImportRow(
    title: String,
    detail: String,
    detected: Boolean,
    busy: Boolean,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    onChoose: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Bold(title)
                if (detected) Badge("detected", Palette.Extracted)
            }
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MonoStyle, color = Palette.TextFaint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (primaryLabel != null) {
                ActionButton(
                    label = primaryLabel,
                    onClick = { onPrimary?.invoke() },
                    primary = true,
                    enabled = onPrimary != null && !busy,
                )
            }
            ActionButton("Choose file", onChoose, enabled = !busy)
        }
    }
}

@Composable
private fun OutcomeStrip(outcome: ImportOutcome) {
    val failed = outcome.error != null
    val tint = if (failed) Palette.Danger else Palette.Extracted
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(Spacing.md),
    ) {
        Text(
            if (failed) "${outcome.label} import failed" else "${outcome.label} imported",
            style = MaterialTheme.typography.titleSmall,
            color = tint,
        )
        Spacer(Modifier.height(Spacing.xs))
        if (failed) {
            Text(outcome.error!!, style = MonoStyle, color = Palette.TextMuted)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "The previous dataset is untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        } else {
            Text(
                "${outcome.imported.format()} rows mapped, ${outcome.skipped.format()} skipped",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        // Skipped rows are normal (not every decoded table maps yet), but warnings are
        // the only place the user learns *which* data did not make it in.
        outcome.warnings.take(6).forEach {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
        }
        if (outcome.warnings.size > 6) {
            Spacer(Modifier.height(2.dp))
            Text(
                "+${outcome.warnings.size - 6} more warnings",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }
}

private fun Int.format(): String = toLong().format()

@Composable
private fun SnapshotCard(snapshot: DatasetSnapshot, onActivate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "build ${snapshot.gameBuild}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
                Text(snapshot.id, style = MonoStyle, color = Palette.TextFaint)
            }
            if (snapshot.active) {
                Badge("active", Palette.Extracted)
            } else {
                ActionButton("Make active", onActivate)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        KeyValueRow("Source", snapshot.source)
        KeyValueRow("Game version", snapshot.gameVersion, mono = true)
        KeyValueRow("Extracted", snapshot.extractedAt)
        KeyValueRow("Decoder", snapshot.decoderVersion ?: "unknown")
        snapshot.sourcePath?.let { KeyValueRow("Source path", it, mono = true) }
        if (snapshot.aliases.isNotEmpty()) {
            KeyValueRow("Aliases", snapshot.aliases.joinToString(", "))
        }
    }
}
