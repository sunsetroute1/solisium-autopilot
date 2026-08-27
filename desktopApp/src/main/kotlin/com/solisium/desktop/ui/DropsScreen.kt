package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.DropLabels
import com.solisium.core.domain.FarmEstimate
import com.solisium.core.domain.FarmEstimator
import com.solisium.core.domain.GameItem
import com.solisium.core.domain.MonsterProfile
import androidx.compose.material3.LinearProgressIndicator
import com.solisium.core.domain.ItemDropSource
import com.solisium.core.domain.ItemGradeHints
import com.solisium.core.domain.QuestlogDropEntry
import com.solisium.core.domain.QuestlogItemOverlay
import com.solisium.core.domain.QuestlogNpcDetail
import com.solisium.desktop.AppModel
import com.solisium.desktop.DropLookupMode
import com.solisium.desktop.Load
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun DropsScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Drops",
            subtitle = "Search offline after syncing loot tables from Questlog. Monster locations use row-id hints + map ids.",
            trailing = { DropSearchField(model) },
        )
        DropCacheBanner(model)
        DropModeChips(model)
        Spacer(Modifier.height(Spacing.md))
        Divider()
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.95f).fillMaxHeight()) { DropResultList(model) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
            Box(Modifier.weight(1.05f).fillMaxHeight()) { DropDetailPane(model) }
        }
    }
}

@Composable
private fun DropSearchField(model: AppModel) {
    SearchBox(
        value = model.dropSearch,
        onValueChange = model::onDropSearch,
        placeholder = when (model.dropMode) {
            DropLookupMode.Item -> "Search item to farm"
            DropLookupMode.Monster -> "Search monster or boss"
        },
        width = 300.dp,
    )
}

@Composable
private fun DropCacheBanner(model: AppModel) {
    val stats = model.dropCacheStats
    Column(Modifier.padding(horizontal = Spacing.xxl).padding(bottom = Spacing.sm)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (stats == null) {
                    Text("Drop cache", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                } else if (stats.dropRows == 0L) {
                    Text(
                        "${stats.monstersTotal} monsters in warehouse · not synced yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Unverified,
                    )
                } else {
                    Text(
                        buildString {
                            append("${stats.dropRows} drop rows")
                            if (stats.extractedDropRows > 0L) {
                                append(" · ${stats.extractedDropRows} extracted")
                            }
                            append(" · ${stats.monstersSynced}/${stats.monstersTotal} monsters synced")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Extracted,
                    )
                    stats.lastSyncedAt?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                    }
                }
                model.dropSyncMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
            ActionButton(
                if (model.dropSyncRunning) "Syncing…" else "Sync drop database",
                { model.syncDropCache() },
                primary = stats?.dropRows == 0L,
                enabled = !model.dropSyncRunning,
            )
        }
        model.dropSyncProgress?.fraction?.let { fraction ->
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Palette.Accent,
                trackColor = Palette.Border,
            )
            Text(
                model.dropSyncProgress?.phase ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun DropModeChips(model: AppModel) {
    Row(
        Modifier.padding(horizontal = Spacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        DropLookupMode.entries.forEach { mode ->
            val selected = model.dropMode == mode
            ActionButton(
                label = when (mode) {
                    DropLookupMode.Item -> "Find item sources"
                    DropLookupMode.Monster -> "Browse monsters"
                },
                onClick = { model.pickDropMode(mode) },
                primary = selected,
            )
        }
    }
}

@Composable
private fun DropResultList(model: AppModel) {
    when (model.dropMode) {
        DropLookupMode.Item -> DropItemList(model)
        DropLookupMode.Monster -> DropMonsterList(model)
    }
}

@Composable
private fun DropItemList(model: AppModel) {
    when (val state = model.dropItems) {
        is Load.Loading -> LoadingRow("Searching items")
        is Load.Err -> Column(Modifier.padding(Spacing.lg)) { ErrorState(state.message) }
        is Load.Ok -> {
            if (state.value.isEmpty()) {
                EmptyState(
                    "No matching items",
                    "Import your warehouse first, then search by the in-game item name.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(Spacing.md)) {
                    items(state.value, key = { "${it.sourceTable}:${it.sourceRowId}" }) { item ->
                        val selected = model.selectedDropItem?.sourceRowId == item.sourceRowId &&
                            model.selectedDropItem?.sourceTable == item.sourceTable
                        HoverRow(selected = selected, onClick = { model.selectDropItem(item) }) {
                            Column(Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.md)) {
                                ItemNameWithRarity(
                                    name = item.name ?: item.sourceRowId,
                                    grade = item.grade,
                                    maxLines = 1,
                                )
                                item.category?.let {
                                    Text(
                                        DisplayName.prettyEnum(it) ?: it,
                                        modifier = Modifier.padding(start = 16.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Palette.TextFaint,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropMonsterList(model: AppModel) {
    when (val state = model.dropMonsters) {
        is Load.Loading -> LoadingRow("Loading monsters")
        is Load.Err -> Column(Modifier.padding(Spacing.lg)) { ErrorState(state.message) }
        is Load.Ok -> {
            if (state.value.isEmpty()) {
                EmptyState(
                    "No reward profiles",
                    "Re-import a warehouse that includes TLRewardNpcFoItem rows (TL-Helper build 24829515+).",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(Spacing.md)) {
                    items(state.value, key = { it.sourceRowId }) { monster ->
                        val selected = model.selectedDropMonster?.sourceRowId == monster.sourceRowId
                        HoverRow(selected = selected, onClick = { model.selectDropMonster(monster) }) {
                            Column(Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.md)) {
                                Text(
                                    monster.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Palette.Text,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    monster.kindHint?.let { Badge(it, Palette.TextMuted, caps = false) }
                                    monster.level?.let {
                                        Text("Lv $it", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                                    }
                                    if (monster.synced) Badge("synced", Palette.Extracted)
                                }
                                monster.locationLabel?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropDetailPane(model: AppModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        when (model.dropMode) {
            DropLookupMode.Item -> DropItemDetail(model)
            DropLookupMode.Monster -> DropMonsterDetail(model)
        }
    }
}

@Composable
private fun DropItemDetail(model: AppModel) {
    val item = model.selectedDropItem
    if (item == null) {
        EmptyState(
            "Pick an item",
            "Search above, then select a row to see monsters and chests that drop it — sorted by drop rate.",
        )
        return
    }
    ItemNameWithRarity(
        name = item.name ?: item.sourceRowId,
        grade = item.grade,
        style = MaterialTheme.typography.titleLarge,
        pipHeight = 28.dp,
    )
    Spacer(Modifier.height(Spacing.xs))
    Text(
        listOfNotNull(item.grade?.let { DisplayName.prettyEnum(it) }, DisplayName.prettyEnum(item.category))
            .joinToString(" · ").ifBlank { "warehouse item" },
        style = MaterialTheme.typography.bodySmall,
        color = rarityColor(item.grade).copy(alpha = 0.85f),
    )
    if (model.dropFetching) {
        Spacer(Modifier.height(Spacing.lg))
        LoadingRow("Fetching live data")
        return
    }
    when (val offline = model.dropItemSources) {
        is Load.Ok -> if (offline.value.isNotEmpty()) {
            OfflineItemDropBody(offline.value, model.observedCombatDps())
            return
        }
        is Load.Loading -> {
            Spacer(Modifier.height(Spacing.lg))
            LoadingRow("Loading cached drops")
            return
        }
        else -> Unit
    }
    when (val detail = model.dropItemDetail) {
        null -> Unit
        is Load.Loading -> LoadingRow("Fetching drop data")
        is Load.Err -> {
            Spacer(Modifier.height(Spacing.lg))
            ErrorState(detail.message)
        }
        is Load.Ok -> ItemDropDetailBody(detail.value) { id -> model.itemGrade(id) }
    }
}

@Composable
private fun DropMonsterDetail(model: AppModel) {
    val monster = model.selectedDropMonster
    if (monster == null) {
        EmptyState(
            "Pick a monster",
            "Browse reward profiles from your warehouse, then load the live loot table from Questlog.",
        )
        return
    }
    Text(monster.displayName, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    Spacer(Modifier.height(Spacing.xs))
    Text(
        listOfNotNull(monster.kindHint, monster.levelHint).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextMuted,
    )
    monster.locationLabel?.let {
        Spacer(Modifier.height(Spacing.xs))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(monster.sourceRowId, style = MonoStyle, color = Palette.TextFaint)
    if (model.dropFetching) {
        Spacer(Modifier.height(Spacing.lg))
        LoadingRow("Fetching live data")
        return
    }
    when (val offline = model.dropMonsterSources) {
        is Load.Ok -> if (offline.value.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            OfflineMonsterDropBody(offline.value)
            return
        }
        is Load.Loading -> {
            Spacer(Modifier.height(Spacing.lg))
            LoadingRow("Loading cached loot")
            return
        }
        else -> Unit
    }
    when (val detail = model.dropNpcDetail) {
        null -> Unit
        is Load.Loading -> LoadingRow("Fetching loot table")
        is Load.Err -> {
            Spacer(Modifier.height(Spacing.lg))
            ErrorState(detail.message)
        }
        is Load.Ok -> NpcDropDetailBody(detail.value) { id -> model.itemGrade(id) }
    }
}

@Composable
private fun OfflineItemDropBody(sources: List<ItemDropSource>, observedDps: Double?) {
    Spacer(Modifier.height(Spacing.lg))
    Text(
        "Best farming sources (offline)",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Palette.Text,
    )
    FarmEstimator.estimate(sources, observedDps)?.let { estimate ->
        Spacer(Modifier.height(Spacing.sm))
        FarmEstimateCard(estimate, observedDps)
    }
    DropLabels.rateContextNote(sources)?.let { note ->
        Spacer(Modifier.height(Spacing.xs))
        Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
    }
    Spacer(Modifier.height(Spacing.sm))
    ItemDropSourceTable(sources, nameColumn = "Monster / source", showLocation = true)
    OfflineDisclaimer()
}

@Composable
private fun FarmEstimateCard(estimate: FarmEstimate, observedDps: Double?) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Farm estimate")
            Spacer(Modifier.weight(1f))
            Badge(
                if (estimate.confidence == "extracted") "client rate" else "Questlog",
                if (estimate.confidence == "extracted") Palette.Extracted else Palette.Unverified,
                caps = false,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(estimate.note, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            buildString {
                append("Best source: ${estimate.sourceName}")
                if (observedDps == null) {
                    append(" · import combat logs on the Combat tab for time estimates")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun OfflineMonsterDropBody(drops: List<ItemDropSource>) {
    Text(
        "Loot table (offline)",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Palette.Text,
    )
    DropLabels.rateContextNote(drops)?.let { note ->
        Spacer(Modifier.height(Spacing.xs))
        Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
    }
    Spacer(Modifier.height(Spacing.sm))
    ItemDropSourceTable(drops, nameColumn = "Item", showLocation = false)
    OfflineDisclaimer()
}

@Composable
fun ItemDropSourceTable(
    sources: List<ItemDropSource>,
    nameColumn: String,
    showLocation: Boolean,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Base)
            .border(1.dp, Palette.Border, RoundedCornerShape(10.dp))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(nameColumn, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Text("Qty", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Text("Rate", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        }
        sources.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    if (showLocation) {
                        Text(
                            row.sourceName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Text,
                        )
                    } else {
                        ItemNameWithRarity(
                            name = row.itemName ?: row.itemSourceRowId,
                            grade = row.itemGrade,
                        )
                    }
                    val meta = buildList {
                        if (showLocation) {
                            row.locationLabel?.let { add(it) }
                            row.sourceLevel?.let { add("Lv $it") }
                            row.sourceCategory?.let { add(it) }
                        } else {
                            row.variantHint?.let { add(it) }
                            DisplayName.prettyEnum(row.itemGrade)?.let { add(it) }
                            DropLabels.typeLabel(row.dropType)?.let { add(it) }
                            DropLabels.conditionLabel(row.dropCondition)?.let { add(it) }
                        }
                        if (row.confidence == "extracted") add("client")
                        else if (row.confidence == "community") add("Questlog")
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                    }
                }
                Text(
                    row.quantity?.toString() ?: "—",
                    Modifier.width(36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                Text(
                    row.probabilityLabel ?: "—",
                    Modifier.width(64.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text,
                )
            }
        }
    }
}

@Composable
private fun OfflineDisclaimer() {
    Spacer(Modifier.height(Spacing.lg))
    Text(
        "Cached locally. Client weights when TLItemLotteryUnit is in the warehouse; " +
            "Questlog community rates fill gaps. Re-sync after patches.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextFaint,
    )
}

@Composable
private fun ItemDropDetailBody(overlay: QuestlogItemOverlay, gradeFor: (String) -> String?) {
    val npcSources = overlay.droppedFromNpcs.sortedByDescending { it.probability ?: 0.0 }
    val containers = overlay.containerSources
    if (npcSources.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Best farming sources",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Palette.Text,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Sorted by reported drop rate. Field bosses and mobs from Questlog.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        DropLabels.questlogRateContextNote(npcSources)?.let { note ->
            Spacer(Modifier.height(Spacing.xs))
            Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
        }
        Spacer(Modifier.height(Spacing.sm))
        DropTable(npcSources, sourceLabel = "Source", gradeFor = gradeFor)
    }
    if (containers.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Also inside",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Palette.Text,
        )
        DropLabels.questlogRateContextNote(containers)?.let { note ->
            Spacer(Modifier.height(Spacing.xs))
            Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
        }
        Spacer(Modifier.height(Spacing.sm))
        DropTable(containers, sourceLabel = "Container", gradeFor = gradeFor)
    }
    if (npcSources.isEmpty() && containers.isEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        EmptyState(
            "No drop data online",
            "Questlog did not return sources for this item. It may be craft-only or untagged.",
        )
    }
    CommunityDisclaimer()
}

@Composable
private fun NpcDropDetailBody(npc: QuestlogNpcDetail, gradeFor: (String) -> String?) {
    Spacer(Modifier.height(Spacing.md))
    npc.subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        npc.level?.let { Text("Level $it", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint) }
        npc.category?.let { Badge(it, Palette.Unverified, caps = false) }
    }
    if (npc.drops.isEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        EmptyState("Empty loot table", "Questlog returned no drops for this id.")
    } else {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Loot table",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Palette.Text,
        )
        DropLabels.questlogRateContextNote(npc.drops)?.let { note ->
            Spacer(Modifier.height(Spacing.xs))
            Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
        }
        Spacer(Modifier.height(Spacing.sm))
        DropTable(
            npc.drops.sortedByDescending { it.probability ?: 0.0 },
            sourceLabel = "Item",
            gradeFor = gradeFor,
        )
    }
    CommunityDisclaimer()
}

@Composable
fun DropTable(
    entries: List<QuestlogDropEntry>,
    sourceLabel: String,
    gradeFor: (String) -> String? = { null },
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Base)
            .border(1.dp, Palette.Border, RoundedCornerShape(10.dp))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                sourceLabel,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint,
            )
            Text("Qty", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Text("Rate", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        }
        entries.forEach { entry ->
            val grade = gradeFor(entry.id)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (sourceLabel == "Item") {
                        ItemNameWithRarity(entry.name, grade)
                    } else {
                        Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                    }
                    val meta = buildList {
                        ItemGradeHints.variantLabel(entry.id)?.let { add(it) }
                        entry.category?.let { add(it) }
                        DropLabels.typeLabel(entry.dropType)?.let { add(it) }
                        DropLabels.conditionLabel(entry.dropCondition)?.let { add(it) }
                        entry.level?.let { add("Source Lv $it") }
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                    }
                }
                Text(
                    entry.quantity?.toString() ?: "—",
                    Modifier.width(36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                Text(
                    entry.probabilityLabel ?: "—",
                    Modifier.width(64.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = run {
                        val pct = entry.probability
                        when {
                            pct == null -> Palette.TextFaint
                            pct >= 0.2 -> Palette.Extracted
                            pct >= 0.05 -> Palette.Text
                            else -> Palette.TextMuted
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CommunityDisclaimer() {
    Spacer(Modifier.height(Spacing.lg))
    Text(
        "Drop rates and names come from Questlog (community mirror). They are not extracted from your game client. " +
            "Warehouse import stores monster reward profile ids only; lottery tables are not collected yet.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextFaint,
    )
}

@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    width: androidx.compose.ui.unit.Dp,
) {
    Row(
        Modifier.width(width).clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicSearchField(value, onValueChange, placeholder)
    }
}
