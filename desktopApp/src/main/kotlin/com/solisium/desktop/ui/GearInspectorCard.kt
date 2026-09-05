package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.GearTraitSlot
import com.solisium.core.domain.ItemTraitCandidate
import com.solisium.core.query.GearInspectorModel
import com.solisium.desktop.AppModel
import com.solisium.desktop.CatalogKind
import com.solisium.desktop.RowDetail
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

fun showGearInspector(kind: CatalogKind, detail: RowDetail): Boolean = when (kind) {
    CatalogKind.Weapons, CatalogKind.Armor, CatalogKind.Accessories ->
        detail.traitProfile != null || detail.questlog?.traitLines?.isNotEmpty() == true
    CatalogKind.Items -> detail.combatPower != null || detail.questlog?.traitLines?.isNotEmpty() == true
    else -> false
}

@Composable
fun GearInspectorCard(
    model: AppModel,
    detail: RowDetail,
) {
    val state = model.gearInspectorState() ?: return
    val profile = detail.traitProfile
    val gearType = GearInspectorModel.gearTypeLabel(detail.row.meta, detail.category, model.kind.label)
    val summary = GearInspectorModel.summarize(
        state = state,
        profile = profile,
        questlog = detail.questlog,
        combatPower = detail.combatPower,
        gearType = gearType,
        itemName = detail.row.name,
        itemRowId = detail.row.sourceRowId,
    )

    Card {
        Text(
            "${summary.gearType} → ${summary.itemName}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Palette.Text,
        )
        Spacer(Modifier.height(Spacing.sm))

        GameRow(
            label = "Level",
            value = state.itemLevel,
            placeholder = GearInspectorModel.defaultItemLevel(detail.questlog, detail.curves).ifBlank { "Level" },
            editable = true,
        ) { text ->
            model.updateGearInspector { it.copy(itemLevel = text.filterDigits()) }
        }

        if (profile != null && state.slots.isNotEmpty()) {
            state.slots.forEachIndexed { index, slot ->
                Spacer(Modifier.height(Spacing.sm))
                TraitSlotBlock(
                    slotIndex = index,
                    slot = slot,
                    candidates = profile.candidates,
                    onSelectTrait = { traitId ->
                        model.updateGearInspectorSlot(
                            slotIndex = index,
                            traitId = traitId,
                            tier = if (traitId.isBlank()) 0 else null,
                        )
                    },
                    onSelectTier = { tier -> model.updateGearInspectorSlot(index, tier = tier) },
                )
            }
        } else {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Trait data loads from the warehouse snapshot when this item supports traits.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }

        Spacer(Modifier.height(Spacing.md))
        val resonanceUnlocked = profile?.let { GearInspectorModel.isResonanceUnlocked(state, it) } == true
        if (resonanceUnlocked && profile?.resonanceCandidates?.isNotEmpty() == true) {
            val activeResonanceTier = GearInspectorModel.activeResonanceTier(state, profile)
            val selectedResonance = GearInspectorModel.findResonanceCandidate(
                profile.resonanceCandidates,
                state.resonanceTraitId,
            ) ?: profile.resonanceCandidates.first()
            ResonancePicker(
                candidates = profile.resonanceCandidates,
                selectedId = state.resonanceTraitId,
                onSelect = { statKey -> model.updateGearInspectorResonance(statKey) },
            )
            Spacer(Modifier.height(Spacing.sm))
            TraitTierBlock(
                label = selectedResonance.rollLabel(activeResonanceTier),
                tierValues = selectedResonance.tierValues,
                statKey = selectedResonance.statKey,
                selectedTier = activeResonanceTier,
                enabled = true,
                onSelectTier = model::updateGearInspectorResonanceTier,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        GameRow("Trait resonance", summary.traitResonance ?: "—")
        GameRow(
            "Potential skill",
            summary.potentialSkill,
            highlight = summary.potentialUnlocked && summary.potentialSkill != "No",
        )

        if (summary.potentialUnlocked && profile?.uniqueCandidates?.isNotEmpty() == true) {
            Spacer(Modifier.height(Spacing.sm))
            PotentialPicker(
                candidates = profile.uniqueCandidates,
                selectedId = state.potentialTraitId,
                onSelect = model::updateGearInspectorPotential,
            )
        }

        val currentPower = summary.itemPowerCurrent
        val potentialPower = summary.itemPowerPotential
        if (currentPower != null && potentialPower != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                if (summary.potentialUnlocked) {
                    "Item power ${formatLong(currentPower)} → ${formatLong(potentialPower)}"
                } else {
                    "Item power ${formatLong(currentPower)}"
                },
                style = MonoStyle,
                color = if (summary.potentialUnlocked) Palette.Accent else Palette.TextMuted,
            )
        }
    }
}

@Composable
private fun TraitSlotBlock(
    slotIndex: Int,
    slot: GearTraitSlot,
    candidates: List<ItemTraitCandidate>,
    onSelectTrait: (String) -> Unit,
    onSelectTier: (Int) -> Unit,
) {
    val selected = candidates.firstOrNull { it.traitId == slot.traitId }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Trait ${slotIndex + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(Spacing.xs))
        ChipRow {
            candidates.forEach { candidate ->
                TraitChip(
                    label = candidate.label,
                    selected = candidate.traitId == slot.traitId,
                    onClick = {
                        onSelectTrait(
                            if (candidate.traitId == slot.traitId) "" else candidate.traitId,
                        )
                    },
                )
            }
        }
        if (selected != null) {
            Spacer(Modifier.height(Spacing.xs))
            TraitTierBlock(
                label = if (slot.tier > 0) selected.rollLabel(slot.tier) else selected.rollLabel(1),
                tierValues = selected.tierValues,
                statKey = selected.statKey,
                selectedTier = slot.tier,
                enabled = true,
                onSelectTier = onSelectTier,
            )
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun TraitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) Palette.Accent.copy(alpha = 0.6f) else Palette.Border
    val background = if (selected) Palette.AccentSoft else Palette.SurfaceHigh
    Text(
        label,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        ),
        color = if (selected) Palette.Accent else Palette.TextMuted,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
    )
}

@Composable
private fun ResonancePicker(
    candidates: List<ItemTraitCandidate>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Trait resonance", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        Spacer(Modifier.height(Spacing.xs))
        ChipRow {
            candidates.forEach { candidate ->
                TraitChip(
                    label = candidate.label,
                    selected = GearInspectorModel.matchesResonanceCandidate(candidate, selectedId),
                    onClick = { onSelect(GearInspectorModel.resonanceSelectionKey(candidate)) },
                )
            }
        }
    }
}

@Composable
private fun PotentialPicker(
    candidates: List<ItemTraitCandidate>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Choose potential", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        Spacer(Modifier.height(Spacing.xs))
        ChipRow {
            candidates.forEach { candidate ->
                TraitChip(
                    label = candidate.label,
                    selected = candidate.traitId == selectedId ||
                        (selectedId.isBlank() && candidate == candidates.first()),
                    onClick = { onSelect(candidate.traitId) },
                )
            }
        }
    }
}

@Composable
private fun TraitTierBlock(
    label: String,
    tierValues: List<String>,
    statKey: String,
    selectedTier: Int,
    enabled: Boolean,
    onSelectTier: (Int) -> Unit,
) {
    val values = GearInspectorModel.tierDisplayValues(tierValues, statKey)
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        if (values.isEmpty()) return@Column
        Spacer(Modifier.height(Spacing.xs))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            values.forEachIndexed { index, value ->
                val tier = index + 1
                val selected = selectedTier == tier
                val border = if (selected) Palette.Accent.copy(alpha = 0.6f) else Palette.Border
                val background = if (selected) Palette.AccentSoft else Palette.SurfaceHigh
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(background)
                        .border(1.dp, border, RoundedCornerShape(8.dp))
                        .then(
                            if (enabled) {
                                // Selecting a tier only; re-clicking must not clear it.
                                // Clearing a selected T4 was dropping resonance unlocks.
                                Modifier.clickable(enabled = !selected) {
                                    onSelectTier(tier)
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = Spacing.sm, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("T$tier", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (selected) Palette.Accent else Palette.Text,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameRow(
    label: String,
    value: String,
    placeholder: String = "",
    highlight: Boolean = false,
    editable: Boolean = false,
    onValueChange: (String) -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
            modifier = Modifier.width(140.dp),
        )
        if (editable) {
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Palette.Surface)
                    .border(1.dp, Palette.Border, RoundedCornerShape(7.dp))
                    .padding(horizontal = Spacing.sm, vertical = 6.dp),
            ) {
                if (value.isEmpty() && placeholder.isNotBlank()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                    cursorBrush = SolidColor(Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (highlight) Palette.Accent else Palette.Text,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun String.filterDigits(): String = filter { it.isDigit() }
