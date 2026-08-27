package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.BuildAdvice
import com.solisium.core.domain.CommunitySnapshot
import com.solisium.core.domain.DesiredBuildPlan
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.RankedGear
import com.solisium.core.domain.SlotAdvice
import com.solisium.core.domain.UserCharacter
import com.solisium.core.query.BuildGoal
import com.solisium.core.query.StatAxis
import com.solisium.desktop.AppModel
import com.solisium.desktop.FilePickers
import com.solisium.desktop.Load
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun BuildScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "What kind of build do you want?",
            "Questlog-style modeled CP and gear score from warehouse item weights, next to the typed window values. Not live-window CP, not DPS.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    if (model.metaRefreshing) "Searching…" else "Search current meta",
                    onClick = { model.refreshMeta() },
                    primary = true,
                    enabled = !model.metaRefreshing,
                )
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xxl),
        ) {
            GoalPicker(model)
            Spacer(Modifier.height(Spacing.md))
            ClassPicker(model)
            Spacer(Modifier.height(Spacing.md))
            DesiredTargetBar(model)
            Spacer(Modifier.height(Spacing.md))
            AxisPicker(model)
            Spacer(Modifier.height(Spacing.md))
            ExtraStatPicker(model)
            Spacer(Modifier.height(Spacing.md))
            CharacterBar(model)
            Spacer(Modifier.height(Spacing.md))
            QuestlogSlugBar(model)
            Spacer(Modifier.height(Spacing.lg))
            when (val state = model.plan) {
                is Load.Loading -> LoadingRow("Ranking extracted gear")
                is Load.Err -> ErrorState(state.message)
                is Load.Ok -> AdviceBody(model, state.value)
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun GoalPicker(model: AppModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        BuildGoal.entries.forEach { goal ->
            ActionButton(
                goal.label,
                onClick = { model.pickGoal(goal) },
                primary = model.goal == goal,
            )
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(model.goal.blurb, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
}

@Composable
private fun ClassPicker(model: AppModel) {
    Text("Class type", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
    Spacer(Modifier.height(Spacing.xs))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (model.classQuery.isEmpty()) {
                Text(
                    "Gladiator, Infiltrator, Invocator…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint,
                )
            }
            BasicTextField(
                value = model.classQuery,
                onValueChange = model::onClassQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(Spacing.xs))
    val term = model.classQuery.trim()
    val visible = if (term.isEmpty()) {
        model.buildClasses
    } else {
        model.buildClasses.filter {
            it.name.contains(term, ignoreCase = true) ||
                it.weaponsLabel.contains(term, ignoreCase = true)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ActionButton(
            "Any pair",
            onClick = { model.pickClass(null) },
            primary = model.selectedClassKey == null,
        )
        visible.forEach { option ->
            ActionButton(
                option.name,
                onClick = { model.pickClass(option) },
                primary = model.selectedClassKey == option.key,
            )
        }
    }
    val selected = model.selectedClassOption()
    Spacer(Modifier.height(Spacing.xs))
    if (selected != null) {
        Text(
            "${selected.weaponsLabel}. Weapon ranks use this pair. ${selected.source}.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
    } else {
        Text(
            "Pathway weapons stay on until you pick a class. Titles come from extracted TLPcClass, then the community table.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun DesiredTargetBar(model: AppModel) {
    val current = (model.characters as? Load.Ok)?.value
        ?.firstOrNull { it.id == model.selectedCharacterId }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NumberField(
            label = "Current CP",
            value = current?.combatPower?.format() ?: "—",
            enabled = false,
            onValueChange = {},
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Desired CP",
            value = model.desiredCombatPowerText,
            enabled = true,
            onValueChange = model::onDesiredCombatPower,
            placeholder = "e.g. 10000",
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Current gear score",
            value = current?.gearScore?.format() ?: "—",
            enabled = false,
            onValueChange = {},
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Desired gear score",
            value = model.desiredGearScoreText,
            enabled = true,
            onValueChange = model::onDesiredGearScore,
            placeholder = "watermark, typed",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(
        "Typed CP/GS are the character-window numbers you entered. Modeled values use Questlog's equipment + skills + mastery layout with warehouse TLItemCombatPower weights when the item maps.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextFaint,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        Spacer(Modifier.height(Spacing.xs))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(Palette.Surface)
                .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = Spacing.md, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && enabled) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (enabled) Palette.Text else Palette.TextMuted,
                    ),
                    cursorBrush = SolidColor(Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AxisPicker(model: AppModel) {
    Text("Offense / defense method", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
    Spacer(Modifier.height(Spacing.xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatAxis.entries.forEach { axis ->
            ActionButton(
                axis.label,
                onClick = { model.toggleAxis(axis) },
                primary = axis in model.axes,
            )
        }
    }
    val selected = model.axes
    if (selected.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            selected.joinToString(" · ") { it.blurb },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
    }
}

@Composable
private fun ExtraStatPicker(model: AppModel) {
    Text(
        "Add any warehouse stat influenced by gear",
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextFaint,
    )
    Spacer(Modifier.height(Spacing.xs))
    if (model.extraStatKeys.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            model.extraStatKeys.forEach { key ->
                val label = model.availableStatKeys.firstOrNull { it.first == key }?.second ?: key
                ActionButton(label, onClick = { model.removeExtraStat(key) })
            }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (model.extraStatQuery.isEmpty()) {
                Text(
                    "Type a stat key or name (hit, endurance, cooldown…)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint,
                )
            }
            BasicTextField(
                value = model.extraStatQuery,
                onValueChange = model::onExtraStatQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val term = model.extraStatQuery.trim()
    if (term.length >= 2) {
        val matches = model.availableStatKeys.filter { (key, label) ->
            key !in model.extraStatKeys &&
                (key.contains(term, ignoreCase = true) || label.contains(term, ignoreCase = true))
        }.take(8)
        if (matches.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            matches.forEach { (key, label) ->
                Text(
                    "$label  ·  $key",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Cool,
                    modifier = Modifier.fillMaxWidth().clickable { model.addExtraStat(key) }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CharacterBar(model: AppModel) {
    when (val state = model.characters) {
        is Load.Loading -> LoadingRow("Reading characters")
        is Load.Err -> Text(state.message, style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
        is Load.Ok -> if (state.value.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "No character imported — ranks only. Loadout comparison needs a JSON sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    if (model.detectedCharacter != null) "Import character JSON" else "Choose character JSON",
                    onClick = {
                        if (model.detectedCharacter != null) {
                            model.importDetectedCharacters()
                        } else {
                            FilePickers.pickFile("Select a character JSON", ".json", model.characterPickerDirectory)
                                ?.let { model.importCharacter(it) }
                        }
                    },
                    enabled = !model.importing,
                )
            }
        } else {
            Column {
                Text("Compare loadout", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    state.value.forEach { character ->
                        CharacterChip(model, character)
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterChip(model: AppModel, character: UserCharacter) {
    ActionButton(
        character.name + (character.className?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
        onClick = { model.selectCharacter(character.id) },
        primary = model.selectedCharacterId == character.id,
    )
}

@Composable
private fun QuestlogSlugBar(model: AppModel) {
    Column {
        Text(
            "Questlog character slug (public listing is locked; paste a builder URL or slug)",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(Palette.Surface)
                    .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = Spacing.md, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (model.questlogSlug.isEmpty()) {
                        Text(
                            "questlog.gg/.../character-builder/your-slug",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint,
                        )
                    }
                    BasicTextField(
                        value = model.questlogSlug,
                        onValueChange = model::onQuestlogSlug,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.Accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ActionButton(
                if (model.characterFetching) "Loading…" else "Overlay character",
                onClick = { model.loadQuestlogCharacter() },
                enabled = model.questlogSlug.isNotBlank() && !model.characterFetching,
            )
        }
    }
}

@Composable
private fun ScoreboardCard(plan: DesiredBuildPlan) {
    val modeled = plan.modeled
    Card(Modifier.fillMaxWidth()) {
        SectionLabel("Combat power / gear score")
        Spacer(Modifier.height(Spacing.sm))
        val classLabel = plan.selectedClass?.name ?: plan.characterClass?.name
        val classWeapons = plan.selectedClass?.weaponsLabel ?: plan.characterClass?.weaponsLabel
        val classSource = plan.selectedClass?.source ?: plan.characterClass?.source
        if (classLabel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(classLabel, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                classWeapons?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                classSource?.let { Badge(it, if (it == "extracted") Palette.Extracted else Palette.Unverified, caps = false) }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ScoreColumn("Combat power", listOf(
                "Typed window" to plan.currentCombatPower,
                "Modeled" to modeled?.current,
                "Potential" to modeled?.potential,
                "Desired" to plan.desiredCombatPower,
            ))
            ScoreColumn("Gear score", listOf(
                "Typed window" to plan.currentGearScore,
                "Modeled" to modeled?.gearScore,
                "Potential" to modeled?.potentialGearScore,
                "Desired" to plan.desiredGearScore,
            ))
        }
        if (modeled != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(modeled.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            Spacer(Modifier.height(Spacing.sm))
            BreakdownLine("Equipment starting value", modeled.equipmentBase, "community")
            BreakdownLine("Items (warehouse weights)", modeled.itemPower, "derived")
            BreakdownLine("Skills (level × 2)", modeled.skillPower, "community")
            BreakdownLine("Mastery (level × 3 + thresholds)", modeled.masteryPower, "community")
            BreakdownLine("Current modeled", modeled.current, "hybrid")
            BreakdownLine("Item potentials", modeled.itemPotentialPower - modeled.itemPower, "extracted")
            BreakdownLine("Potential modeled", modeled.potential, "hybrid")
            if (modeled.items.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                SectionLabel("Per-slot item CP")
                Spacer(Modifier.height(Spacing.xs))
                modeled.items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.slot, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.width(88.dp))
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                        Badge(
                            item.source,
                            if (item.source == "warehouse") Palette.Extracted else Palette.Unverified,
                            caps = false,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text("${item.current.format()} → ${item.potential.format()}", style = MonoStyle, color = Palette.TextMuted)
                    }
                }
                if (modeled.unresolvedCount > 0) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "${modeled.unresolvedCount} slotted item(s) have no combat-power row (A/AA families stay unresolved).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                }
            }
        } else {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Select a character with a loadout to score modeled CP. Typed window values still apply without one.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
    }
}

@Composable
private fun RowScope.ScoreColumn(title: String, rows: List<Pair<String, Long?>>) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        Spacer(Modifier.height(Spacing.xs))
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.weight(1f))
                Text(value?.format() ?: "—", style = MonoStyle, color = Palette.Text)
            }
        }
    }
}

@Composable
private fun BreakdownLine(label: String, value: Long, source: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.weight(1f))
        Badge(
            source,
            when (source) {
                "extracted" -> Palette.Extracted
                "derived" -> Palette.Derived
                "hybrid" -> Palette.Cool
                else -> Palette.Unverified
            },
            caps = false,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(value.format(), style = MonoStyle, color = Palette.Text)
    }
}

@Composable
private fun RoadmapCard(plan: DesiredBuildPlan) {
    Card(Modifier.fillMaxWidth()) {
        SectionLabel("Roadmap")
        Spacer(Modifier.height(Spacing.sm))
        val gapLine = listOfNotNull(
            plan.modeledCombatPowerGap?.let { "Modeled potential-CP gap ${it.format()}" },
            plan.combatPowerGap?.let { "typed CP gap ${it.format()}" },
            plan.modeledGearScoreGap?.let { "modeled GS gap ${it.format()}" },
            plan.gearScoreGap?.let { "typed gear-score gap ${it.format()}" },
        ).joinToString(" · ")
        if (gapLine.isNotEmpty()) {
            Text(gapLine, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(
            plan.skillCoverage.note,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        if (plan.influences.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            plan.influences.forEach { layer ->
                Text(
                    "${layer.label}: ${layer.slotted} typed · ${layer.resolved} resolved" +
                        (if (layer.catalogNamed > 0) " · ${layer.catalogNamed} catalog" else "") +
                        (if (layer.newThisPatch) " · new this patch" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        if (plan.roadmap.isEmpty()) {
            Text(
                "No slot gaps on this snapshot for the selected pathway. Enter a desired CP to see modeled and typed gaps.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
        } else {
            plan.roadmap.forEach { step ->
                Text(step.title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Badge("typed window", Palette.Unverified)
            Badge("warehouse item CP", Palette.Extracted)
            Badge("community skill/mastery", Palette.Unverified)
            Badge("derived row map", Palette.Derived)
        }
        Spacer(Modifier.height(Spacing.sm))
        plan.limits.take(5).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun AdviceBody(model: AppModel, plan: DesiredBuildPlan) {
    val advice = plan.advice
    ScoreboardCard(plan)
    Spacer(Modifier.height(Spacing.lg))
    RoadmapCard(plan)
    Spacer(Modifier.height(Spacing.lg))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Card(Modifier.weight(1.1f)) {
            SectionLabel("Extracted vs loadout")
            Spacer(Modifier.height(Spacing.sm))
            Text(
                advice.scoringNote,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
            Spacer(Modifier.height(Spacing.md))
            CompareRadar(advice.axes, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.md))
            PaperDoll(advice, plan)
        }
        Card(Modifier.weight(1f)) {
            SectionLabel("Briefing")
            Spacer(Modifier.height(Spacing.sm))
            advice.briefing.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(bottom = Spacing.sm))
            }
            model.narration?.let { spoken ->
                Spacer(Modifier.height(Spacing.sm))
                Badge("local ollama", Palette.Derived)
                Spacer(Modifier.height(Spacing.xs))
                Text(spoken, style = MaterialTheme.typography.bodyMedium, color = Palette.Cool)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Badge("extracted ranks", Palette.Extracted)
                Badge("community overlay", Palette.Unverified)
            }
        }
    }

    Spacer(Modifier.height(Spacing.lg))
    CommunityPanel(model.community)

    if (advice.combatInsights.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        CombatInsightsPanel(advice.combatInsights)
    }

    if (advice.skillShares.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        Card(Modifier.fillMaxWidth()) {
            SectionLabel("Observed rotation")
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Shares of DamageDone in imported combat logs. Healing and buffs are not in those files.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
            Spacer(Modifier.height(Spacing.md))
            advice.skillShares.forEach { share ->
                val suffix = listOfNotNull(
                    share.catalogName?.let { "catalog $it" },
                    share.questlogName?.let { "questlog $it" },
                ).joinToString(" · ")
                ShareBar(
                    label = share.name + if (suffix.isNotBlank()) "  ·  $suffix" else "",
                    share = share.share,
                    trailing = "${share.observedDamage.format()}  ${(share.share * 100).toInt()}%",
                )
            }
        }
    }

    Spacer(Modifier.height(Spacing.lg))
    advice.slots.filter { it.recommended.isNotEmpty() || it.equipped != null }.forEach { slot ->
        SlotCard(slot)
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun PaperDoll(advice: BuildAdvice, plan: DesiredBuildPlan) {
    val bySlot = advice.slots.associateBy { it.slot }
    val tokens = advice.weaponTokens.ifEmpty {
        plan.selectedClass?.tokens?.toList().orEmpty()
    }
    val weaponSlots = tokens.mapNotNull { token ->
        DisplayName.prettyEnum(token)?.lowercase()?.let { slot ->
            slot to (DisplayName.prettyEnum(token) ?: slot)
        }
    }.distinctBy { it.first }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SlotPip("Head", bySlot["head"])
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            weaponSlots.forEach { (slot, label) -> SlotPip(label, bySlot[slot]) }
            SlotPip("Chest", bySlot["chest"])
            SlotPip("Cloak", bySlot["cloak"])
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SlotPip("Hands", bySlot["hands"])
            SlotPip("Legs", bySlot["legs"])
            SlotPip("Feet", bySlot["feet"])
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SlotPip("Neck", bySlot["necklace"])
            SlotPip("Ring", bySlot["ring"])
            SlotPip("Wrist", bySlot["bracelet"])
            SlotPip("Belt", bySlot["belt"])
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            advice.characterName?.let { "Loadout for $it" } ?: "No character imported — ranks only",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Text(
            "Filled pip = equipped. Accent border = a named rank exists for this slot.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun SlotCard(slot: SlotAdvice) {
    val max = listOfNotNull(slot.equipped?.score, slot.recommended.maxOfOrNull { it.score }).maxOrNull() ?: 1L
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                slot.slot.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
            )
            slot.gap?.let { gap ->
                Badge(if (gap == 0L) "on rank" else "gap ${gap.format()}", if (gap == 0L) Palette.Extracted else Palette.Unverified)
            }
        }
        slot.equipped?.let { GearRow("You", it, max, Palette.Cool) }
        slot.recommended.forEachIndexed { index, gear ->
            GearRow(if (index == 0) "Top" else "#${index + 1}", gear, max, Palette.Accent)
        }
        if (slot.recommended.isEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text("No named piece in this snapshot scored for the selected goal.", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
    }
}

@Composable
private fun GearRow(tag: String, gear: RankedGear, max: Long, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tag, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.width(44.dp))
            ItemNameWithRarity(
                name = gear.name,
                grade = gear.grade,
                modifier = Modifier.weight(1f, fill = false),
                pipHeight = 22.dp,
                maxLines = 1,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(gear.kind, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            RarityBadge(gear.grade)
            Spacer(Modifier.weight(1f))
            Text(gear.score.format(), style = MonoStyle, color = color)
            gear.itemPower?.let { power ->
                Spacer(Modifier.width(Spacing.sm))
                Badge("item CP $power", Palette.Derived, caps = false)
            }
            gear.potentialPower?.let { power ->
                if (power != gear.itemPower) {
                    Spacer(Modifier.width(Spacing.sm))
                    Badge("potential $power", Palette.Extracted, caps = false)
                }
            }
            if (gear.communityHits > 0) {
                Spacer(Modifier.width(Spacing.sm))
                Badge("questlog", Palette.Unverified)
            }
        }
        Spacer(Modifier.height(4.dp))
        ScoreBar(gear.score, max, color = color)
    }
}

@Composable
private fun CommunityPanel(state: Load<CommunitySnapshot>?) {
    Card(Modifier.fillMaxWidth()) {
        SectionLabel("Current meta")
        Spacer(Modifier.height(Spacing.sm))
        when (state) {
            null -> Text(
                "Not fetched yet. Search current meta to query Questlog tRPC and the TLDB homepage. " +
                    "This is a community overlay, not client data.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            is Load.Loading -> LoadingRow("Talking to Questlog and TLDB")
            is Load.Err -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger)
            is Load.Ok -> {
                val snap = state.value
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    snap.patchLabel?.let { Badge(it, Palette.Unverified, caps = false) }
                    snap.sources.forEach { Badge(it, Palette.Cool) }
                    Text(snap.fetchedAt, style = MonoStyle, color = Palette.TextFaint)
                }
                Spacer(Modifier.height(Spacing.sm))
                snap.notes.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(bottom = 4.dp))
                }
                snap.warnings.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (snap.builds.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    SectionLabel("Questlog characters")
                    Spacer(Modifier.height(Spacing.xs))
                    snap.builds.take(8).forEach { hit ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(hit.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                            hit.catalogName?.let { Badge("in warehouse", Palette.Extracted, caps = false) }
                        }
                    }
                }
                if (snap.items.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    SectionLabel("Questlog items")
                    Spacer(Modifier.height(Spacing.xs))
                    snap.items.take(12).forEach { hit ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            ItemNameWithRarity(
                                name = hit.name,
                                grade = detailGradeToken(hit.detail),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            hit.catalogName?.let { Badge("in warehouse", Palette.Extracted, caps = false) }
                        }
                    }
                }
                if (snap.skills.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    SectionLabel("Questlog skill sets")
                    Spacer(Modifier.height(Spacing.xs))
                    snap.skills.take(12).forEach { hit ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(hit.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                            hit.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint) }
                        }
                    }
                }
            }
        }
    }
}
