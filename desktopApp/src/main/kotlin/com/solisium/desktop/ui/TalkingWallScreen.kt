package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.TalkingWallStatement
import com.solisium.desktop.AppModel
import com.solisium.desktop.Load
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun TalkingWallScreen(model: AppModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxl),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        WallTitleRow(model)
        Spacer(Modifier.height(Spacing.md))
        WallSearchBar(model)
        Spacer(Modifier.height(Spacing.sm))
        WallCategoryChips(model)
        Spacer(Modifier.height(Spacing.md))
        WallHeroAnswer(model)
        Spacer(Modifier.height(Spacing.md))
        Divider()
        WallResultList(model, Modifier.weight(1f))
    }
}

@Composable
private fun WallTitleRow(model: AppModel) {
    val coverage = model.wallCoverage
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Talking Wall",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Palette.Text,
            )
            Text(
                buildString {
                    append("Blue = TRUE · Red = FALSE · event ~every 3h 17m")
                    coverage?.let { append(" · ${it.total} answers") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        coverage?.let { cov ->
            if (cov.warehouse == 0L) {
                Badge("Community key", Palette.Unverified, caps = false)
            } else {
                Badge("${cov.warehouse} official", Palette.Extracted, caps = false)
            }
        }
    }
}

@Composable
private fun WallSearchBar(model: AppModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.BorderStrong, RoundedCornerShape(10.dp))
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicSearchField(
            value = model.wallSearch,
            onValueChange = model::onWallSearch,
            placeholder = "Paste a snippet from the in-game statement (Ctrl+F keywords work)",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WallCategoryChips(model: AppModel) {
    val categories = model.wallCoverage?.categories.orEmpty()
    if (categories.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            label = "All",
            selected = model.wallCategory == null,
            onClick = { model.onWallCategory(null) },
        )
        categories.forEach { bucket ->
            FilterChip(
                label = "${categoryLabel(bucket.category)} (${bucket.count})",
                selected = model.wallCategory == bucket.category,
                onClick = { model.onWallCategory(bucket.category) },
            )
        }
    }
}

@Composable
private fun WallHeroAnswer(model: AppModel) {
    when (val state = model.wallStatements) {
        is Load.Loading -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Surface)
                    .border(1.dp, Palette.Border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingRow("Looking up answer…")
            }
        }
        is Load.Err -> ErrorState(state.message)
        is Load.Ok -> {
            val row = model.selectedWallStatement
            if (row == null) {
                WallHeroPlaceholder(
                    hasSearch = model.wallSearch.isNotBlank(),
                    matchCount = state.value.size,
                )
            } else {
                WallHeroCard(row, matchCount = state.value.size)
            }
        }
    }
}

@Composable
private fun WallHeroPlaceholder(hasSearch: Boolean, matchCount: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(14.dp))
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                hasSearch && matchCount == 0 -> "No match — try fewer keywords"
                hasSearch -> "Searching…"
                else -> "Paste the on-screen question above"
            },
            style = MaterialTheme.typography.titleMedium,
            color = Palette.TextMuted,
        )
        if (!hasSearch) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "The answer shows here in large blue (TRUE) or red (FALSE).",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun WallHeroCard(row: TalkingWallStatement, matchCount: Int) {
    val accent = answerColor(row.answerTrue)
    val label = if (row.answerTrue) "TRUE" else "FALSE"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(2.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(Spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.displayMedium,
                color = accent,
            )
            Spacer(Modifier.weight(1f))
            if (matchCount > 1) {
                Text(
                    "$matchCount matches — pick below if needed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            row.statement,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = accent,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "${categoryLabel(row.category)} · ${row.sourceKind}",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun WallResultList(model: AppModel, modifier: Modifier = Modifier) {
    when (val state = model.wallStatements) {
        is Load.Loading, is Load.Err -> Unit
        is Load.Ok -> {
            val rows = state.value
            if (rows.isEmpty()) return
            if (rows.size == 1 && model.wallSearch.isNotBlank()) {
                return
            }
            Column(modifier.fillMaxWidth()) {
                SectionLabel(
                    if (model.wallSearch.isBlank()) "All statements" else "Matches",
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.sourceTable + it.sourceRowId }) { row ->
                        WallResultRow(
                            row = row,
                            selected = model.selectedWallStatement == row,
                            onClick = { model.selectWallStatement(row) },
                        )
                        Spacer(Modifier.height(Spacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
private fun WallResultRow(row: TalkingWallStatement, selected: Boolean, onClick: () -> Unit) {
    val accent = answerColor(row.answerTrue)
    HoverRow(selected = selected, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                if (row.answerTrue) "T" else "F",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = accent,
                modifier = Modifier.width(16.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                row.statement,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) accent else Palette.Text,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun answerColor(answerTrue: Boolean) =
    if (answerTrue) Palette.WallTrue else Palette.WallFalse

private fun categoryLabel(category: String?): String = when (category?.lowercase()) {
    "nix" -> "Nix"
    "cosmology" -> "Cosmology"
    "regions" -> "Regions & bestiary"
    "characters" -> "Core characters"
    "npcs" -> "NPCs & factions"
    "trivia" -> "Trivia & tricks"
    "community" -> "Community extras"
    null, "" -> "General"
    else -> category.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    HoverRow(selected = selected, onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (selected) Palette.Text else Palette.TextMuted,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
        )
    }
}
