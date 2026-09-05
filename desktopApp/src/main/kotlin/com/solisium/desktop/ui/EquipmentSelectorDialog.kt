package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.solisium.core.domain.RankedGear
import com.solisium.desktop.AppModel
import com.solisium.desktop.SlotPickerMode
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

private val SLOT_LABELS = mapOf(
    "head" to "Head",
    "chest" to "Chest",
    "hands" to "Hands",
    "legs" to "Legs",
    "feet" to "Feet",
    "cloak" to "Cloak",
    "necklace" to "Neck",
    "earring" to "Earring",
    "bracelet" to "Wrist",
    "ring" to "Ring",
    "belt" to "Belt",
)

@Composable
fun EquipmentSelectorDialog(model: AppModel) {
    val picker = model.slotPicker ?: return
    val slotLabel = SLOT_LABELS[picker.slot.lowercase()]
        ?: picker.slot.replaceFirstChar { it.uppercase() }
    val sideLabel = when (picker.mode) {
        SlotPickerMode.Current -> "I currently have"
        SlotPickerMode.Desired -> "I would like to have"
    }
    val selectedId = when (picker.mode) {
        SlotPickerMode.Desired -> model.desiredGearFor(picker.slot)?.sourceRowId
        SlotPickerMode.Current -> (model.plan as? com.solisium.desktop.Load.Ok)?.value
            ?.advice?.slots?.firstOrNull { it.slot == picker.slot }?.equipped?.sourceRowId
    }
    val query = model.slotPickerQuery.trim()
    val options = model.slotPickerOptions.filter { gear ->
        query.isEmpty() || gear.name.contains(query, ignoreCase = true)
    }

    Dialog(onDismissRequest = { model.dismissSlotPicker() }) {
        Column(
            Modifier.width(640.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Surface)
                .border(1.dp, Palette.Border, RoundedCornerShape(14.dp))
                .padding(Spacing.xl),
        ) {
            Text(
                "$slotLabel · $sideLabel",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                when (picker.mode) {
                    SlotPickerMode.Desired ->
                        "Meta-ranked picks for your goal appear first. Choose any piece for your target loadout."
                    SlotPickerMode.Current ->
                        if (model.characterDraft != null) {
                            "Pick what you are wearing now. Updates your Character draft loadout."
                        } else {
                            "Load a character on the Character screen first to save current gear picks."
                        }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(Spacing.md))
            SearchField(
                value = model.slotPickerQuery,
                onValueChange = model::onSlotPickerQuery,
                placeholder = "Filter by item name…",
            )
            Spacer(Modifier.height(Spacing.md))
            when {
                model.slotPickerLoading -> LoadingRow("Ranking gear for this slot")
                options.isEmpty() -> Text(
                    if (query.isNotEmpty()) "No ranked items match that filter."
                    else "No extracted ranks for this slot on the active snapshot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint,
                )
                else -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    itemsIndexed(options, key = { _, gear -> gear.sourceRowId }) { index, gear ->
                        SelectorGearRow(
                            gear = gear,
                            rank = index + 1,
                            metaTop = index == 0,
                            selected = gear.sourceRowId == selectedId,
                            enabled = picker.mode == SlotPickerMode.Desired || model.characterDraft != null,
                            onClick = { model.pickSlotGear(gear) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                if (picker.mode == SlotPickerMode.Desired && model.isCustomDesiredSlot(picker.slot)) {
                    ActionButton(
                        "Reset to meta top",
                        onClick = {
                            model.clearDesiredSlotPick(picker.slot)
                            model.dismissSlotPicker()
                        },
                    )
                }
                ActionButton("Cancel", { model.dismissSlotPicker() })
                if (options.isNotEmpty() && picker.mode == SlotPickerMode.Desired) {
                    ActionButton("Use #1 meta pick", { model.useMetaTopForPickerSlot() }, primary = true)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Palette.Base)
            .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.md, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
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
}

@Composable
private fun SelectorGearRow(
    gear: RankedGear,
    rank: Int,
    metaTop: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val frame = if (selected) Palette.Accent else Palette.Border
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.AccentSoft else Palette.Base)
            .border(1.dp, frame, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#$rank", style = MonoStyle, color = Palette.TextFaint, modifier = Modifier.width(28.dp))
        GearIcon(iconPath = gear.iconPath, slot = gear.slot, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            ItemNameWithRarity(name = gear.name, grade = gear.grade, maxLines = 1)
            Text(
                listOfNotNull(
                    gear.kind.takeIf { it.isNotBlank() },
                    gear.itemPower?.let { "item CP $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
        Text(gear.score.format(), style = MonoStyle, color = Palette.Accent)
        Spacer(Modifier.width(Spacing.sm))
        if (metaTop) Badge("meta top", Palette.Extracted, caps = false)
        if (gear.communityHits > 0) Badge("questlog", Palette.Unverified, caps = false)
        if (selected) Badge("selected", Palette.Accent, caps = false)
    }
}
