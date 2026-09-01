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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.CommunityStatLine
import com.solisium.core.domain.CommunityTraitLine
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.GameItemPower
import com.solisium.core.domain.GameItemStat
import com.solisium.core.domain.QuestlogItemOverlay
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
            title = "Gear catalog",
            subtitle = "Equipment from your warehouse — stats, curves, and Questlog community detail",
            trailing = { SearchField(model) },
        )
        KindChips(model)
        Spacer(Modifier.height(Spacing.md))
        Divider()
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { ResultList(model) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
            Box(Modifier.width(500.dp).fillMaxHeight()) { CatalogDetailPane(model) }
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
                    "Search gear by name",
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
                    title = if (model.search.isBlank()) "No ${model.kind.label.lowercase()} yet" else "No matches",
                    detail = if (model.search.isBlank()) {
                        "This snapshot has no named ${model.kind.label.lowercase()} matching the gear filter."
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
                            "Showing ${formatLong(rows.size.toLong())} of ${formatLong(model.browseTotal.toLong())} ${model.kind.label.lowercase()}"
                        } else {
                            "${formatLong(rows.size.toLong())} ${model.kind.label.lowercase()}"
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

@Composable
fun CatalogResultRow(row: CatalogRow, selected: Boolean, onClick: () -> Unit) {
    val grade = displayGrade(row.sourceRowId, row.grade)
    HoverRow(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.rarityRowTint(grade, row.sourceRowId),
    ) {
        Spacer(Modifier.width(Spacing.sm))
        RarityPip(grade, sourceRowId = row.sourceRowId)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = rarityColor(grade),
                maxLines = 1,
            )
            val type = prettyEnum(row.meta)
            val rarity = prettyEnum(grade)
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
fun CatalogDetailPane(
    model: AppModel,
    emptyTitle: String = "Pick gear on the left",
) {
    val detail = model.detail
    if (detail == null) {
        EmptyState(
            title = emptyTitle,
            detail = "Warehouse stats, upgrade curves, and Questlog community detail appear here.",
        )
        return
    }
    when (detail) {
        is Load.Loading -> LoadingRow("Loading stats")
        is Load.Err -> Column(Modifier.padding(Spacing.lg)) { ErrorState(detail.message) }
        is Load.Ok -> DetailBody(detail.value)
    }
}

@Composable
private fun DetailBody(detail: RowDetail) {
    ColumnWithScrollbar(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            DetailHeader(detail)
            detail.questlog?.description?.takeIf { it.isNotBlank() }?.let { DescriptionCard(it) }
            WarehouseStats(detail.stats)
            detail.combatPower?.let { CombatPowerCard(it) }
            Curves(detail)
            detail.questlog?.let { CommunitySection(it, detail.questlogWarning) }
            TechnicalFooter(detail)
        }
    }
}

@Composable
private fun DetailHeader(detail: RowDetail) {
    val grade = displayGrade(detail.row.sourceRowId, detail.row.grade ?: detail.questlog?.grade)
    Row(verticalAlignment = Alignment.Top) {
        RarityPip(grade, sourceRowId = detail.row.sourceRowId)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                detail.row.name,
                style = MaterialTheme.typography.headlineSmall,
                color = rarityColor(grade),
            )
            val chips = buildList {
                prettyEnum(grade)?.let { add(it) }
                prettyEnum(detail.row.meta)?.let { add(it) }
                prettyEnum(detail.category)?.let { add(it) }
                detail.questlog?.tradeCategory?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
                detail.questlog?.requiredLevel?.let { add("Req. level $it") }
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(chips.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
    }
}

@Composable
private fun DescriptionCard(description: String) {
    Card {
        SectionLabel("Description")
        Spacer(Modifier.height(Spacing.sm))
        Text(
            description.replace("\r\n", "\n"),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Text,
        )
    }
}

@Composable
private fun WarehouseStats(stats: List<GameItemStat>) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Warehouse stats", Modifier.weight(1f))
            Badge("extracted", Palette.Extracted)
        }
        Spacer(Modifier.height(Spacing.md))
        if (stats.isEmpty()) {
            Text(
                "No stat values are linked to this row in your warehouse import.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
            return@Card
        }
        val labels = statLabels(stats.map { it.statKey to it.statName }.distinct())
        stats.groupBy { it.scope }.forEach { (scope, rows) ->
            Text(
                formatStatScope(scope),
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(Spacing.xs))
            rows.forEach { stat ->
                KeyValueRow(
                    key = labels[stat.statKey] ?: stat.statKey,
                    value = formatLong(stat.rawValue),
                    keyWidth = 200.dp,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        Text(
            "Raw client integers at +0. Display scaling is unverified.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun CombatPowerCard(power: GameItemPower) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Combat power link", Modifier.weight(1f))
            Badge(power.confidence, confidenceColor(power.confidence))
        }
        Spacer(Modifier.height(Spacing.sm))
        KeyValueRow("Base power", formatLong(power.basePower), keyWidth = 160.dp)
        power.potentialPower?.let {
            KeyValueRow("Potential power", formatLong(it), keyWidth = 160.dp)
        }
        if (power.evidence.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(power.evidence, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
    }
}

@Composable
private fun CommunitySection(overlay: QuestlogItemOverlay, warning: String?) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Questlog community", Modifier.weight(1f))
            Badge("community", Palette.Unverified)
        }
        if (warning != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(warning, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
        }
        if (overlay.properties.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                overlay.properties.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        if (overlay.statLines.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            CommunityStatBlock(overlay.statLines)
        }
        if (overlay.traitLines.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            CommunityTraitBlock(overlay.traitLines)
        }
        if (overlay.perkSummaries.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Text("Available perks", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Spacer(Modifier.height(Spacing.xs))
            overlay.perkSummaries.take(6).forEach { perk ->
                Text("• $perk", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                Spacer(Modifier.height(4.dp))
            }
            if (overlay.perkSummaries.size > 6) {
                Text(
                    "+ ${overlay.perkSummaries.size - 6} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
        }
        if (overlay.dropSources.isNotEmpty() || overlay.droppedFromNpcs.isNotEmpty() || overlay.containerSources.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Text("Where to get it", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Spacer(Modifier.height(Spacing.xs))
            val npcSources = overlay.droppedFromNpcs.sortedByDescending { it.probability ?: 0.0 }
            if (npcSources.isNotEmpty()) {
                DropTable(npcSources, sourceLabel = "Monster / boss")
                Spacer(Modifier.height(Spacing.sm))
            }
            val containers = overlay.containerSources
            if (containers.isNotEmpty()) {
                DropTable(containers, sourceLabel = "Chest / bundle")
            } else if (overlay.dropSources.isNotEmpty() && npcSources.isEmpty()) {
                Text(
                    overlay.dropSources.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Community site values. Not extracted from your game client.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun CommunityStatBlock(lines: List<CommunityStatLine>) {
    Text("Stats at max item level", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
    Spacer(Modifier.height(Spacing.xs))
    lines.groupBy { it.group }.forEach { (group, rows) ->
        Text(group, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        rows.forEach { line ->
            KeyValueRow(key = line.label, value = line.value, keyWidth = 200.dp)
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

@Composable
private fun CommunityTraitBlock(traits: List<CommunityTraitLine>) {
    Text("Trait tiers", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
    Spacer(Modifier.height(Spacing.xs))
    traits.forEach { trait ->
        KeyValueRow(key = trait.label, value = trait.tiers, keyWidth = 200.dp)
    }
}

@Composable
private fun TechnicalFooter(detail: RowDetail) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            if (expanded) "Hide technical id" else "Show technical id",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Accent,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (expanded) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${detail.row.sourceTable} · ${detail.row.sourceRowId}",
                style = MonoStyle,
                color = Palette.TextFaint,
            )
        }
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
                keyWidth = 140.dp,
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
