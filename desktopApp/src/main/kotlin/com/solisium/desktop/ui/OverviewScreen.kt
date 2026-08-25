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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.domain.DatasetSnapshot
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
            ActionButton("My character", { model.go(com.solisium.desktop.Screen.Character) })
            ActionButton("Combat logs", { model.go(com.solisium.desktop.Screen.Combat) })
            ActionButton("Import more", { model.go(com.solisium.desktop.Screen.Data) })
        }
        Spacer(Modifier.height(Spacing.lg))
        if (overview.buildWarning != null) {
            WarningBanner(
                overview.buildWarning,
                detail = "The game has been patched since this dataset was extracted. " +
                    "Values may no longer match.",
            )
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
fun WarningBanner(message: String, label: String = "stale data", detail: String? = null) {
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
