package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.domain.DatasetSnapshot
import com.solisium.core.domain.DiscoveredInfluence
import com.solisium.core.source.PatchWatchState
import com.solisium.core.source.TLHelperExtractProgress
import com.solisium.desktop.AppModel
import com.solisium.desktop.Load
import com.solisium.desktop.Overview
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun OverviewScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Home", "A read-only companion for Throne and Liberty. Nothing writes back to the game.")
        when (val state = model.overview) {
            is Load.Loading -> LoadingRow("Reading the catalog")
            is Load.Err -> Column(Modifier.padding(horizontal = Spacing.xxl)) { ErrorState(state.message) }
            is Load.Ok -> OverviewBody(model, state.value)
        }
    }
}

@Composable
private fun OverviewBody(model: AppModel, overview: Overview) {
    val snapshot = overview.snapshot
    if (snapshot == null) {
        Column(Modifier.padding(horizontal = Spacing.xxl)) {
            Card {
                Text("Start here", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Import the game data warehouse, then search gear by the names you see in game.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                )
                Spacer(Modifier.height(Spacing.md))
                ActionButton("Import game data", { model.go(com.solisium.desktop.Screen.Data) }, primary = true)
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xxl),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ActionButton("What kind of build?", { model.go(com.solisium.desktop.Screen.Build) }, primary = true)
            ActionButton("Browse gear", { model.go(com.solisium.desktop.Screen.Catalog) })
            ActionButton("Farm drops", { model.go(com.solisium.desktop.Screen.Drops) })
            ActionButton("Event timeline", { model.go(com.solisium.desktop.Screen.Events) })
            ActionButton("Talking Wall", { model.go(com.solisium.desktop.Screen.Wall) })
            ActionButton("My character", { model.go(com.solisium.desktop.Screen.Character) })
            ActionButton("Combat logs", { model.go(com.solisium.desktop.Screen.Combat) })
            ActionButton("Import more", { model.go(com.solisium.desktop.Screen.Data) })
        }
        Spacer(Modifier.height(Spacing.lg))
        val needsExtract = model.patchWatch?.state == PatchWatchState.WAITING_FOR_WAREHOUSE ||
            model.patchWatch?.state == PatchWatchState.NO_WAREHOUSE ||
            (overview.buildWarning != null && model.patchWatch?.canImport != true)
        if (overview.buildWarning != null) {
            WarningBanner(
                overview.buildWarning,
                detail = listOfNotNull(
                    "The game has been patched since this dataset was extracted. Values may no longer match.",
                    model.tlHelperLastRun?.takeUnless { it.succeeded }?.summary(),
                ).joinToString(" "),
                actionLabel = if (needsExtract) {
                    if (model.tlHelperCheckout != null) "Run TL-Helper" else "Get TL-Helper"
                } else null,
                onAction = if (needsExtract) ({ model.runTLHelper() }) else null,
                progress = if (needsExtract) model.extractProgress else null,
            )
            Spacer(Modifier.height(Spacing.lg))
        }
        model.patchWatch?.takeIf {
            it.state != PatchWatchState.CURRENT
        }?.let { watch ->
            val needsExtract = watch.state == PatchWatchState.WAITING_FOR_WAREHOUSE ||
                watch.state == PatchWatchState.NO_WAREHOUSE
            WarningBanner(
                watch.reason,
                label = when (watch.state) {
                    PatchWatchState.IMPORT_READY -> "warehouse ready"
                    PatchWatchState.WAITING_FOR_WAREHOUSE -> "waiting on TL-Helper"
                    else -> "patch watch"
                },
                detail = listOfNotNull(
                    if (watch.canImport) {
                        "Use Import warehouse below, or it will import automatically after first-run setup."
                    } else if (model.tlHelperCheckout == null) {
                        "The starter catalog is already loaded so you can browse. For live patch data, " +
                            "download TL-Helper, install Node.js and the .NET SDK, then run extract. " +
                            "A key is found from Data → Find my key if you already have one."
                    } else {
                        "Solisium does not unpack game paks. A new warehouse for this Steam build is not on disk yet."
                    },
                    model.tlHelperLastRun?.takeUnless { it.succeeded }?.summary(),
                ).joinToString(" "),
                actionLabel = if (needsExtract) {
                    if (model.tlHelperCheckout != null) "Run TL-Helper" else "Get TL-Helper"
                } else null,
                onAction = if (needsExtract) ({ model.runTLHelper() }) else null,
                progress = if (needsExtract) model.extractProgress else null,
            )
            if (watch.canImport) {
                Spacer(Modifier.height(Spacing.sm))
                ActionButton("Import warehouse", { model.importReadyWarehouse() }, primary = true, enabled = !model.importing)
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        model.catalogSyncNote?.let { note ->
            ConfirmBanner(note)
            Spacer(Modifier.height(Spacing.lg))
        }
        model.tlHelperMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            Spacer(Modifier.height(Spacing.lg))
        }
        if (model.discoveredInfluences.isNotEmpty()) {
            InfluenceRoster(model.discoveredInfluences)
            Spacer(Modifier.height(Spacing.lg))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Card(Modifier.weight(1f)) { Provenance(snapshot, overview.installedBuild) }
            Card(Modifier.weight(1f)) { Coverage(overview.counts) }
        }

        Spacer(Modifier.height(Spacing.lg))
        overview.counts?.let { CountGrid(it) }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun Provenance(snapshot: DatasetSnapshot, installedBuild: String?) {
    SectionLabel("Provenance")
    Spacer(Modifier.height(Spacing.md))
    KeyValueRow("Source", snapshot.source)
    KeyValueRow("Game build", snapshot.gameBuild, mono = true)
    KeyValueRow("Game version", snapshot.gameVersion, mono = true)
    KeyValueRow("Installed build", installedBuild ?: "not detected", mono = true)
    KeyValueRow("Extracted", snapshot.extractedAt)
    KeyValueRow("Decoder", snapshot.decoderVersion ?: "unknown")
    KeyValueRow("Schema version", snapshot.schemaVersion.toString())
    if (snapshot.aliases.isNotEmpty()) {
        KeyValueRow("Aliases", snapshot.aliases.joinToString(", "))
    }
}

@Composable
private fun Coverage(counts: CatalogCounts?) {
    SectionLabel("What is known")
    Spacer(Modifier.height(Spacing.md))
    if (counts == null) {
        Text("no counts available", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        return
    }
    Text(
        "Every value below was read from a client table. Nothing here is inferred.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextMuted,
    )
    Spacer(Modifier.height(Spacing.md))
    KeyValueRow("Items with stats", "${counts.itemsWithStats.format()} of ${counts.items.format()}")
    KeyValueRow("Stat values", counts.itemStats.format())
    KeyValueRow("Curve points", counts.curvePoints.format())
    Spacer(Modifier.height(Spacing.md))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Badge("extracted", Palette.Extracted)
        Badge("scale unverified", Palette.Unverified)
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        "Stat values are raw client integers. Per-stat display scaling has not been " +
            "confirmed, so they are not shown as percentages.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextFaint,
    )
}

@Composable
private fun CountGrid(counts: CatalogCounts) {
    val tiles = listOf(
        "Items" to counts.items,
        "Weapons" to counts.weapons,
        "Armor" to counts.armor,
        "Accessories" to counts.accessories,
        "Traits" to counts.traits,
        "Runes" to counts.runes,
        "Synergies" to counts.synergies,
        "Skills" to counts.skills,
        "Effects" to counts.effects,
        "Formulas" to counts.formulas,
        "Recipes" to counts.recipes,
        "Materials" to counts.materials,
        "Named stats" to counts.stats,
        "Item curves" to counts.itemCurveLinks,
        "Classes" to counts.classes,
        "Item CP rows" to counts.combatPowerRows,
        "Item CP links" to counts.itemPowerLinks,
        "Talking Wall" to counts.talkingWallStatements,
    )
    SectionLabel("Catalog")
    Spacer(Modifier.height(Spacing.md))
    // Height is derived from the row count so the grid does not scroll independently.
    val rows = (tiles.size + 3) / 4
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth().height((rows * 84).dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        userScrollEnabled = false,
    ) {
        items(tiles) { (label, value) ->
            StatTile(label = label, value = value.format())
        }
    }
}

@Composable
private fun InfluenceRoster(influences: List<DiscoveredInfluence>) {
    var selectedPrefix by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        SectionLabel("New build influences")
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Prefixes observed in this warehouse that are not one of the hardcoded skills-screen families. " +
                "Presence only; not combat power. Click a family to see its names.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(Spacing.sm))
        influences.forEach { inf ->
            val selected = inf.prefix == selectedPrefix
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Palette.SurfaceHigh else Palette.Surface)
                    .clickable {
                        selectedPrefix = if (selected) null else inf.prefix
                    }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${inf.label} · ${inf.namedCount} named",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (inf.newThisPatch) Badge("new this patch", Palette.Derived)
                }
                if (selected) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(inf.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                    Spacer(Modifier.height(Spacing.xs))
                    val shown = inf.names.take(80)
                    shown.forEach { name ->
                        Text(name, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                    if (inf.names.size > shown.size) {
                        Text(
                            "+${inf.names.size - shown.size} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextFaint,
                        )
                    }
                    if (inf.names.isEmpty()) {
                        Text(
                            "No localized names in this warehouse.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextFaint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractProgressBars(progress: TLHelperExtractProgress.Snapshot) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                progress.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (progress.failed) Palette.Danger else Palette.Text,
            )
            Text(
                "${progress.overallPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (progress.failed) Palette.Danger else Palette.Accent,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { progress.overallPercent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (progress.failed) Palette.Danger else Palette.Accent,
            trackColor = Palette.Border,
        )
        Spacer(Modifier.height(Spacing.sm))
        progress.stages.forEach { stage ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stage.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stage.name == progress.activeStage) Palette.Text else Palette.TextFaint,
                )
                Text(
                    buildString {
                        append("${stage.percent}%")
                        stage.counts?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { stage.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = if (stage.name == progress.activeStage) Palette.Accent else Palette.TextMuted,
                trackColor = Palette.Border,
            )
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun ConfirmBanner(message: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Palette.Extracted.copy(alpha = 0.10f))
            .border(1.dp, Palette.Extracted.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(Spacing.md),
    ) {
        Badge("current", Palette.Extracted)
        Spacer(Modifier.height(Spacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
    }
}

@Composable
fun WarningBanner(
    message: String,
    label: String = "stale data",
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    progress: TLHelperExtractProgress.Snapshot? = null,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Palette.Unverified.copy(alpha = 0.10f))
            .border(1.dp, Palette.Unverified.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(Spacing.md),
    ) {
        Row {
            Badge(label, Palette.Unverified)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        if (detail != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        if (progress != null) {
            Spacer(Modifier.height(Spacing.sm))
            ExtractProgressBars(progress)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.sm))
            ActionButton(actionLabel, onAction, primary = true)
        }
    }
}

@Composable
fun CodeLine(text: String) {
    Text(
        text,
        style = com.solisium.desktop.theme.MonoStyle,
        color = Palette.Cool,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(Palette.Base).padding(Spacing.sm),
    )
}

/** Thousands separators without pulling in a locale-dependent formatter. */
fun Long.format(): String = toString().reversed().chunked(3).joinToString(",").reversed()
