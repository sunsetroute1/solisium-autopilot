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
            title = "Catalog",
            subtitle = "Extracted client data for the active dataset",
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
                    title = if (model.search.isBlank()) "${model.kind.label} is empty" else "No matches",
                    detail = if (model.search.isBlank()) {
                        "This table has not been collected into the active dataset."
                    } else {
                        "No ${model.kind.label.lowercase()} match \"${model.search}\"."
                    },
                )
                return
            }
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.sm),
                ) {
                    SectionLabel("${rows.size.toLong().format()} ${model.kind.label.lowercase()}")
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
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md, vertical = 7.dp)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                ),
                color = Palette.Text,
                maxLines = 1,
            )
            // Unnamed rows fall back to the row id, so repeating it below would be noise.
            if (row.name != row.sourceRowId) {
                Text(row.sourceRowId, style = MonoStyle, color = Palette.TextFaint, maxLines = 1)
            }
        }
        prettyEnum(row.meta)?.let {
            Badge(it, Palette.TextMuted, Modifier.padding(end = Spacing.md), caps = false)
        }
    }
}

@Composable
private fun DetailPane(model: AppModel) {
    val detail = model.detail
    if (detail == null) {
        EmptyState(
            title = "Nothing selected",
            detail = "Pick a row to see its extracted stats and upgrade curves.",
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
        Text(detail.row.name, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "${detail.row.sourceTable} / ${detail.row.sourceRowId}",
            style = MonoStyle,
            color = Palette.TextFaint,
        )

        Spacer(Modifier.height(Spacing.lg))
        BaseStats(detail)
        Spacer(Modifier.height(Spacing.lg))
        Curves(detail)
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
