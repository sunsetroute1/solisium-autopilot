package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.GearWatermarkCategory
import com.solisium.core.domain.GearWatermarkPlan
import com.solisium.core.domain.WatermarkDropChance
import com.solisium.core.query.GearWatermarkInferrer
import com.solisium.desktop.AppModel
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun WatermarkCalculatorCard(
    model: AppModel,
    plan: GearWatermarkPlan?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Drop watermark",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
                Text(
                    "Highest item level ever dropped per category — not equipped ilvl.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
            Badge("Aragon datamine", Palette.Unverified, caps = false)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
            WatermarkField(
                label = "Weapon",
                value = model.watermarkWeaponText,
                highlighted = plan?.farmCategories?.contains(GearWatermarkCategory.WEAPON) == true,
                onValueChange = model::onWatermarkWeapon,
                modifier = Modifier.weight(1f),
            )
            WatermarkField(
                label = "Armor",
                value = model.watermarkArmorText,
                highlighted = plan?.farmCategories?.contains(GearWatermarkCategory.ARMOR) == true,
                onValueChange = model::onWatermarkArmor,
                modifier = Modifier.weight(1f),
            )
            WatermarkField(
                label = "Accessory",
                value = model.watermarkAccessoryText,
                highlighted = plan?.farmCategories?.contains(GearWatermarkCategory.ACCESSORY) == true,
                onValueChange = model::onWatermarkAccessory,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActionButton("Use equipped ilvl (hint)", onClick = model::applyWatermarkFromLoadout)
            ActionButton("Sync to desired GS", onClick = model::syncDesiredGearScoreFromWatermark, enabled = plan != null)
        }
        plan?.let { wm ->
            Spacer(Modifier.height(Spacing.md))
            WatermarkSummary(wm, compact)
            if (!compact && wm.dropChances.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                WatermarkDropTable(wm.dropChances)
            }
            Spacer(Modifier.height(Spacing.sm))
            wm.notes.forEach { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun WatermarkField(
    label: String,
    value: String,
    highlighted: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            if (highlighted) {
                Spacer(Modifier.width(Spacing.xs))
                Badge("farm", Palette.Accent, caps = false)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        WatermarkNumberField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "45–80",
        )
    }
}

@Composable
private fun WatermarkNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
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
}

@Composable
private fun WatermarkSummary(plan: GearWatermarkPlan, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(Modifier.weight(1f)) {
            Text("Watermark", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Text(
                plan.watermark.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = Palette.Accent,
            )
            Text(
                buildString {
                    append("avg ${"%.2f".format(plan.average)}")
                    if (plan.mayRoundUp) append(" · may round up")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        if (!plan.atCap) {
            Column(Modifier.weight(1f)) {
                Text("Farm next", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                Text(
                    GearWatermarkInferrer.farmLabel(plan.farmCategories),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Accent,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("+1 IL chance", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                Text(
                    "${"%.2f".format(plan.upgradeChancePercent)}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Extracted,
                )
                plan.expectedDropsToUpgrade?.let {
                    Text(
                        "~${"%.1f".format(it)} drops per +1",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                    )
                }
            }
        }
        if (!compact) {
            Column(Modifier.weight(1f)) {
                Text("To IL 80", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                Text(
                    "~${plan.expectedDropsPerCategoryTo80.toInt()} / cat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                )
                Text(
                    "~${plan.expectedTotalDropsTo80.toInt()} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun WatermarkDropTable(chances: List<WatermarkDropChance>) {
    SectionLabel("Next drop chances")
    Spacer(Modifier.height(Spacing.sm))
    chances.forEach { row ->
        val accent = when {
            row.delta > 0 -> Palette.Extracted
            row.delta == 0 -> Palette.TextMuted
            else -> Palette.TextFaint
        }
        val deltaLabel = when (row.delta) {
            1 -> "+1 level"
            0 -> "same level"
            else -> "${row.delta} level"
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(deltaLabel, style = MaterialTheme.typography.bodySmall, color = accent, modifier = Modifier.width(88.dp))
            Text("IL ${row.gearLevel}", style = MonoStyle, color = Palette.TextMuted, modifier = Modifier.width(52.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Base)
                    .border(1.dp, Palette.Border, RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = (row.percent / 100.0).toFloat().coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(accent.copy(alpha = 0.55f)),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text("${"%.2f".format(row.percent)}%", style = MonoStyle, color = Palette.Text)
        }
    }
}
