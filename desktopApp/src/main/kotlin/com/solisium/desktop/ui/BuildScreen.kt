package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.RankedGear
import com.solisium.core.domain.SlotAdvice
import com.solisium.core.domain.UserCharacter
import com.solisium.core.query.BuildGoal
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
            "Extracted warehouse ranks versus Questlog and TLDB community names. Not modeled DPS.",
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
            CharacterBar(model)
            Spacer(Modifier.height(Spacing.md))
            QuestlogSlugBar(model)
            Spacer(Modifier.height(Spacing.lg))
            when (val state = model.advice) {
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
                    "Import character JSON",
                    onClick = {
                        FilePickers.pickFile("Select a character JSON", ".json")
                            ?.let { model.importCharacter(it) }
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
        character.name,
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
private fun AdviceBody(model: AppModel, advice: BuildAdvice) {
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
            PaperDoll(advice, model.goal)
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
private fun PaperDoll(advice: BuildAdvice, goal: BuildGoal) {
    val bySlot = advice.slots.associateBy { it.slot }
    val weaponSlots = goal.weaponTokens.mapNotNull { token ->
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
            Bold(gear.name, color)
            Spacer(Modifier.width(Spacing.sm))
            Text(gear.kind, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            DisplayName.prettyEnum(gear.grade)?.let { grade ->
                Spacer(Modifier.width(Spacing.sm))
                Badge(grade, Palette.TextMuted, caps = false)
            }
            Spacer(Modifier.weight(1f))
            Text(gear.score.format(), style = MonoStyle, color = color)
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
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(hit.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
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
