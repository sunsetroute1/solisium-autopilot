package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.solisium.desktop.Load
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun DataScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Data", "Imported datasets. Exactly one is active at a time.")
        when (val state = model.snapshots) {
            is Load.Loading -> LoadingRow("Reading snapshots")
            is Load.Err -> Column(Modifier.padding(horizontal = Spacing.xxl)) { ErrorState(state.message) }
            is Load.Ok -> {
                Column(Modifier.padding(horizontal = Spacing.xxl)) {
                    Text(
                        "Database: ${AppModel.databasePath()}",
                        style = MonoStyle,
                        color = Palette.TextFaint,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
                if (state.value.isEmpty()) {
                    Column(Modifier.padding(horizontal = Spacing.xxl)) {
                        Card {
                            Text(
                                "No datasets yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.Text,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            CodeLine("solisium import --source tl-helper --path <warehouse>.sqlite")
                        }
                    }
                    return@Column
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.xxl)) {
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
                ActivateButton(onActivate)
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

@Composable
private fun ActivateButton(onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(7.dp))
            .background(Palette.AccentSoft)
            .border(1.dp, Palette.Accent.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 6.dp),
    ) {
        Text("Make active", style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
    }
}
