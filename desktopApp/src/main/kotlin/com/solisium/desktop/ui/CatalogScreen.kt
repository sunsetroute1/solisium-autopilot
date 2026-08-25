package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solisium.desktop.AppModel
import com.solisium.desktop.CatalogKind
import com.solisium.desktop.CatalogRow
import com.solisium.desktop.Load
import com.solisium.desktop.RowDetail
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun CatalogScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Gear & skills",
            subtitle = "Search by the name you see in game — Longbow, Explosive Trap, Hit Chance",
            trailing = { SearchField(model) },
        )
        KindChips(model)
        Spacer(Modifier.height(Spacing.md))
        Divider()
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { ResultList(model) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
            Box(Modifier.width(440.dp).fillMaxHeight()) { DetailPane(model) }
        }
    }
}

@Composable
private fun SearchField(model: AppModel) {
    Row(
        Modifier.width(280.dp).clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (model.search.isEmpty()) {
                Text(
                    "Search by name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint,
                )
            }
            BasicTextField(
                value = model.search,
                onValueChange = model::onSearch,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun KindChips(model: AppModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CatalogKind.entries.forEach { entry ->
            Chip(
                label = entry.label,
                selected = model.kind == entry,
                onClick = { model.selectKind(entry) },
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) Palette.Accent.copy(alpha = 0.5f) else Palette.Border
    val background = if (selected) Palette.AccentSoft else Palette.Surface
    val color = if (selected) Palette.Accent else Palette.TextMuted
    Box(
        Modifier.clip(RoundedCornerShape(7.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun ResultList(model: AppModel) {
    when (val state = model.rows) {
        is Load.Loading -> LoadingRow("Querying ${model.kind.label.lowercase()}")
        is Load.Err -> Column(Modifier.padding(Spacing.lg)) { ErrorState(state.message) }
        is Load.Ok -> {
            val rows = state.value
            if (rows.isEmpty()) {
                EmptyState(
                    title = if (model.search.isBlank()) "No named ${model.kind.label.lowercase()} yet" else "No matches",
                    detail = if (model.search.isBlank()) {
                        "This dataset has no localized names for ${model.kind.label.lowercase()}."
                    } else {
                        "Nothing named \"${model.search}\"."
                    },
                )
                return
            }
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.sm),
                ) {
                    SectionLabel(
                        if (model.browseTotal > rows.size) {
                            "Showing ${rows.size.toLong().format()} of ${model.browseTotal.toLong().format()} ${model.kind.label.lowercase()}"
                        } else {
                            "${rows.size.toLong().format()} ${model.kind.label.lowercase()}"
                        },
                    )
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
                    items(rows, key = { it.sourceTable + "|" + it.sourceRowId }) { row ->
                        ResultRow(row, model.selected == row) { model.select(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(row: CatalogRow, selected: Boolean, onClick: () -> Unit) {
    HoverRow(selected = selected, onClick = onClick) {
        Spacer(Modifier.width(Spacing.sm))
        RarityPip(row.grade)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = Palette.Text,
                maxLines = 1,
            )
            val type = prettyEnum(row.meta)
            val rarity = prettyEnum(row.grade)
            val subtitle = listOfNotNull(rarity, type).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, maxLines = 1)
            }
        }
        if (!row.named) {
            Badge("id", Palette.TextFaint, Modifier.padding(end = Spacing.md), caps = false)
        }
    }
}

@Composable
private fun DetailPane(model: AppModel) {
    val detail = model.detail
    if (detail == null) {
        EmptyState(
            title = "Pick something on the left",
            detail = "Stats and upgrade paths show up here.",
        )
        return
    }
    when (detail) {
        is Load.Loading -> LoadingRow("Reading values")
        is Load.Err -> Column(Modifier.padding(Spacing.lg)) { ErrorState(detail.message) }
        is Load.Ok -> DetailBody(detail.value)
    }
}

@Composable
private fun DetailBody(detail: RowDetail) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RarityPip(detail.row.grade)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(detail.row.name, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                val rarity = prettyEnum(detail.row.grade)
                val kind = prettyEnum(detail.row.meta)
                val line = listOfNotNull(rarity, kind).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        BaseStats(detail)
        Spacer(Modifier.height(Spacing.lg))
        Curves(detail)
        Spacer(Modifier.height(Spacing.xl))
        Text(
            detail.row.sourceRowId,
            style = MonoStyle,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun BaseStats(detail: RowDetail) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Base stats", Modifier.weight(1f))
            if (detail.stats.isNotEmpty()) {
                Badge(detail.stats.first().confidence, confidenceColor(detail.stats.first().confidence))
            }
        }
        Spacer(Modifier.height(Spacing.md))
        if (detail.stats.isEmpty()) {
            Text(
                "No stat values are linked to this row. Most non-equipment rows have none.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
            return@Card
        }
        val labels = statLabels(detail.stats.map { it.statKey to it.statName }.distinct())
        detail.stats.forEach { stat ->
            KeyValueRow(
                key = labels[stat.statKey] ?: stat.statKey,
                value = stat.rawValue.format(),
                keyWidth = 190.dp,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Raw client integers at +0. Display scaling is unverified.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun Curves(detail: RowDetail) {
    if (detail.curves.isEmpty()) return
    Card {
        SectionLabel("Upgrade curves")
        Spacer(Modifier.height(Spacing.sm))
        detail.curves.forEach { curve ->
            KeyValueRow(
                key = if (curve.curveKind == "enchant") "Enchant curve" else "Item level curve",
                value = curve.curveId + (curve.maxLevel?.let { " (max +$it)" } ?: ""),
                keyWidth = 130.dp,
                mono = true,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Shared curves: many items follow the same one. Each point is the client's " +
                "cumulative total at that level, not a per-level gain.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )

        detail.curvePoints.groupBy { it.curveKind }.forEach { (kindLabel, points) ->
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (kindLabel == "enchant") "Enchant" else "Item level",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                    modifier = Modifier.weight(1f),
                )
                Badge("extracted", Palette.Extracted)
            }
            Spacer(Modifier.height(Spacing.sm))
            CurveChart(points)
        }
        Spacer(Modifier.height(Spacing.md))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(Palette.Unverified.copy(alpha = 0.08f))
                .border(1.dp, Palette.Unverified.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                .padding(Spacing.sm),
        ) {
            Text(
                "Not combined with base stats: whether these add to the base value is not " +
                    "yet confirmed against the game, so Solisium will not do that arithmetic for you.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Unverified,
            )
        }
    }
}
