package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.solisium.desktop.AppModel
import com.solisium.desktop.Load
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun SkillCoresScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Skill cores",
            subtitle = "Perk items from your warehouse. Search by the name on the core.",
            trailing = { CoreSearchField(model) },
        )
        Divider()
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { CoreList(model) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
            Box(Modifier.width(500.dp).fillMaxHeight()) {
                CatalogDetailPane(model, emptyTitle = "Pick a skill core on the left")
            }
        }
    }
}

@Composable
private fun CoreSearchField(model: AppModel) {
    Row(
        Modifier.width(280.dp).clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (model.skillCoreSearch.isEmpty()) {
                Text(
                    "Search cores by name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint,
                )
            }
            BasicTextField(
                value = model.skillCoreSearch,
                onValueChange = model::onSkillCoreSearch,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CoreList(model: AppModel) {
    when (val state = model.skillCoreRows) {
        is Load.Loading -> LoadingRow("Querying skill cores")
        is Load.Err -> Column(Modifier.padding(Spacing.lg)) { ErrorState(state.message) }
        is Load.Ok -> {
            val rows = state.value
            if (rows.isEmpty()) {
                EmptyState(
                    title = if (model.skillCoreSearch.isBlank()) "No skill cores yet" else "No matches",
                    detail = if (model.skillCoreSearch.isBlank()) {
                        "This snapshot has no perk items named Skill Core."
                    } else {
                        "Nothing named \"${model.skillCoreSearch}\"."
                    },
                )
                return
            }
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.sm),
                ) {
                    SectionLabel(
                        if (model.skillCoreTotal > rows.size) {
                            "Showing ${formatLong(rows.size.toLong())} of ${formatLong(model.skillCoreTotal.toLong())} cores"
                        } else {
                            "${formatLong(rows.size.toLong())} cores"
                        },
                    )
                }
                LazyListWithScrollbar(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg),
                ) {
                    items(rows, key = { it.sourceTable + "|" + it.sourceRowId }) { row ->
                        CatalogResultRow(row, model.selected == row) { model.select(row) }
                    }
                }
            }
        }
    }
}
