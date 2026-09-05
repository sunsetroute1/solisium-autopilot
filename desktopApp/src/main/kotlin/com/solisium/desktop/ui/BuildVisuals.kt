package com.solisium.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.RankedGear
import com.solisium.core.domain.AxisScore
import com.solisium.core.domain.BuildAdvice
import com.solisium.core.domain.DesiredBuildPlan
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.SlotAdvice
import com.solisium.core.source.WarehouseIconLocator
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val iconLocator = WarehouseIconLocator()

enum class DollMode { Current, Desired }

@Composable
fun ScoreBar(
    value: Long,
    max: Long,
    modifier: Modifier = Modifier,
    color: Color = Palette.Accent,
) {
    val fraction = if (max <= 0L) 0f else (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Base),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).height(7.dp).clip(RoundedCornerShape(4.dp)).background(color),
        )
    }
}

@Composable
fun ShareBar(
    label: String,
    share: Double,
    trailing: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Cool,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f), maxLines = 1)
            Text(trailing, style = MonoStyle, color = Palette.TextFaint)
        }
        Spacer(Modifier.height(4.dp))
        ScoreBar((share * 1000).toLong(), 1000, color = color)
    }
}

/**
 * You vs recommended extracted totals for the goal's keys. Polygons are linear
 * interpolations of stored numbers, not a modeled stat wheel.
 */
@Composable
fun CompareRadar(axes: List<AxisScore>, modifier: Modifier = Modifier) {
    if (axes.isEmpty()) return
    val shown = axes.take(6)
    val peak = shown.maxOf { maxOf(it.yours, it.recommended, 1L) }.toFloat()
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(10.dp))
                .background(Palette.Base).padding(Spacing.sm),
        ) {
            Canvas(Modifier.fillMaxWidth().height(152.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(cx, cy) - 10f
                val n = shown.size
                fun point(index: Int, value: Long): Offset {
                    val angle = -Math.PI / 2 + index * (2 * Math.PI / n)
                    val r = radius * (value.toFloat() / peak)
                    return Offset(cx + (cos(angle) * r).toFloat(), cy + (sin(angle) * r).toFloat())
                }
                fun ring(t: Float) {
                    val path = Path()
                    shown.indices.forEach { i ->
                        val angle = -Math.PI / 2 + i * (2 * Math.PI / n)
                        val p = Offset(
                            cx + (cos(angle) * radius * t).toFloat(),
                            cy + (sin(angle) * radius * t).toFloat(),
                        )
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path, Palette.Border, style = Stroke(width = 1f))
                }
                ring(0.5f)
                ring(1f)
                fun polygon(values: List<Long>, color: Color, fill: Boolean) {
                    val path = Path()
                    values.forEachIndexed { i, v ->
                        val p = point(i, v)
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    if (fill) drawPath(path, color.copy(alpha = 0.18f))
                    drawPath(path, color, style = Stroke(width = 2f))
                }
                polygon(shown.map { it.recommended }, Palette.Accent, fill = true)
                polygon(shown.map { it.yours }, Palette.Cool, fill = false)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row {
            LegendDot(Palette.Cool, "Your loadout")
            Spacer(Modifier.width(Spacing.md))
            LegendDot(Palette.Accent, "Top extracted picks")
        }
        Spacer(Modifier.height(Spacing.sm))
        shown.forEach { axis ->
            Text(
                "${axis.label}  you ${axis.yours} / rec ${axis.recommended}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

@Composable
fun HaveWantMeter(
    label: String,
    current: Long?,
    desired: Long?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val have = current ?: 0L
    val want = desired ?: have
    val max = maxOf(have, want, 1L)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint, modifier = Modifier.weight(1f))
            Text(
                "${current?.format() ?: "—"}  →  ${desired?.format() ?: "—"}",
                style = MonoStyle,
                color = Palette.TextMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Base)) {
            if (desired != null && desired > 0L) {
                Box(
                    Modifier.fillMaxWidth((want.toFloat() / max).coerceIn(0f, 1f))
                        .height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.28f)),
                )
            }
            Box(
                Modifier.fillMaxWidth((have.toFloat() / max).coerceIn(0f, 1f))
                    .height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent),
            )
        }
    }
}

/**
 * T&L player-page layout: armor down the left, accessories down the right,
 * weapons under a body silhouette. [mode] picks equipped vs top extracted rank.
 */
@Composable
fun EquipmentPaperDoll(
    advice: BuildAdvice,
    plan: DesiredBuildPlan,
    mode: DollMode,
    selectedSlot: String?,
    onSlotClick: (String, DollMode) -> Unit,
    desiredGearForSlot: (String) -> RankedGear? = { null },
    modifier: Modifier = Modifier,
) {
    val bySlot = advice.slots.associateBy { it.slot }
    val tokens = advice.weaponTokens.ifEmpty {
        plan.selectedClass?.tokens?.toList().orEmpty()
    }
    val weaponSlots = tokens.mapNotNull { token ->
        DisplayName.prettyEnum(token)?.lowercase()?.let { slot ->
            slot to (DisplayName.prettyEnum(token) ?: slot)
        }
    }.distinctBy { it.first }.ifEmpty {
        advice.slots.filter { it.slot in WEAPON_SLOT_KEYS }.map { it.slot to it.slot.replaceFirstChar { ch -> ch.uppercase() } }
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            SlotColumn(
                keys = ARMOR_SLOTS,
                bySlot = bySlot,
                mode = mode,
                selectedSlot = selectedSlot,
                onSlotClick = onSlotClick,
                desiredGearForSlot = desiredGearForSlot,
            )
            Box(
                Modifier.weight(1f).height(400.dp).padding(horizontal = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                CharacterSilhouette(Modifier.fillMaxSize())
            }
            SlotColumn(
                keys = ACCESSORY_SLOTS,
                bySlot = bySlot,
                mode = mode,
                selectedSlot = selectedSlot,
                onSlotClick = onSlotClick,
                desiredGearForSlot = desiredGearForSlot,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            weaponSlots.forEach { (slot, label) ->
                EquipmentSlot(
                    slot = slot,
                    label = label,
                    advice = bySlot[slot],
                    mode = mode,
                    selected = selectedSlot == slot,
                    desiredGear = if (mode == DollMode.Desired) desiredGearForSlot(slot) else null,
                    onClick = { onSlotClick(slot, mode) },
                )
            }
        }
    }
}

@Composable
private fun SlotColumn(
    keys: List<Pair<String, String>>,
    bySlot: Map<String, SlotAdvice>,
    mode: DollMode,
    selectedSlot: String?,
    onSlotClick: (String, DollMode) -> Unit,
    desiredGearForSlot: (String) -> RankedGear?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        keys.forEach { (slot, label) ->
            EquipmentSlot(
                slot = slot,
                label = label,
                advice = bySlot[slot],
                mode = mode,
                selected = selectedSlot == slot,
                desiredGear = if (mode == DollMode.Desired) desiredGearForSlot(slot) else null,
                onClick = { onSlotClick(slot, mode) },
            )
        }
    }
}

@Composable
fun EquipmentSlot(
    slot: String,
    label: String,
    advice: SlotAdvice?,
    mode: DollMode,
    selected: Boolean,
    desiredGear: RankedGear? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gear = when (mode) {
        DollMode.Current -> advice?.equipped
        DollMode.Desired -> desiredGear ?: advice?.recommended?.firstOrNull()
    }
    val customPick = mode == DollMode.Desired && desiredGear != null &&
        desiredGear.sourceRowId != advice?.recommended?.firstOrNull()?.sourceRowId
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rarity = rarityColor(displayGrade(gear?.sourceRowId, gear?.grade))
    val filled = gear != null
    val upgrade = mode == DollMode.Desired && (advice?.gap ?: 0L) > 0L
    val frame = when {
        selected -> Palette.Accent
        upgrade -> Palette.Accent.copy(alpha = 0.75f)
        filled && rarity != Palette.Text -> rarity
        filled -> if (mode == DollMode.Current) Palette.Cool else Palette.Accent
        else -> Palette.BorderStrong
    }
    val fill = when {
        selected || hovered -> Palette.SurfaceHover
        filled -> Palette.SurfaceHigh
        else -> Palette.Base
    }
    Column(modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                .background(fill)
                .border(1.5.dp, frame.copy(alpha = if (filled || selected) 0.85f else 0.45f), RoundedCornerShape(8.dp))
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GearIcon(
                iconPath = gear?.iconPath,
                slot = slot,
                modifier = Modifier.size(32.dp),
                tint = if (filled) frame else Palette.TextFaint.copy(alpha = 0.55f),
            )
            if (upgrade) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(3.dp)
                        .size(7.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Accent),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            gear?.name ?: "Pick…",
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) rarityColor(displayGrade(gear?.sourceRowId, gear?.grade)) else Palette.TextFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (customPick) {
            Badge("custom", Palette.Derived, caps = false)
        }
    }
}

@Composable
fun GearIcon(
    iconPath: String?,
    slot: String,
    modifier: Modifier = Modifier,
    tint: Color = Palette.TextMuted,
) {
    val bitmap = remember(iconPath) { loadWarehouseIcon(iconPath) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = slot,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        SlotGlyph(slot, modifier, tint)
    }
}

private fun loadWarehouseIcon(iconPath: String?): ImageBitmap? {
    val file = iconLocator.find(iconPath) ?: return null
    return runCatching {
        file.toFile().inputStream().buffered().use { loadImageBitmap(it) }
    }.getOrNull()
}

@Composable
fun SlotGlyph(slot: String, modifier: Modifier = Modifier, tint: Color = Palette.TextMuted) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round)
        val pad = size.minDimension * 0.12f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val left = pad
        val top = pad
        when (normalizeSlot(slot)) {
            "head" -> {
                drawCircle(tint, radius = w * 0.28f, center = Offset(size.width / 2f, top + h * 0.38f), style = stroke)
                drawRoundRect(tint, Offset(left + w * 0.18f, top + h * 0.18f), Size(w * 0.64f, h * 0.22f), CornerRadius(6f), stroke)
            }
            "chest" -> {
                val path = Path().apply {
                    moveTo(left + w * 0.18f, top + h * 0.12f)
                    lineTo(left + w * 0.82f, top + h * 0.12f)
                    lineTo(left + w * 0.72f, top + h * 0.88f)
                    lineTo(left + w * 0.28f, top + h * 0.88f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            "hands" -> {
                drawRoundRect(tint, Offset(left + w * 0.08f, top + h * 0.22f), Size(w * 0.34f, h * 0.52f), CornerRadius(8f), stroke)
                drawRoundRect(tint, Offset(left + w * 0.58f, top + h * 0.22f), Size(w * 0.34f, h * 0.52f), CornerRadius(8f), stroke)
            }
            "legs" -> {
                drawRoundRect(tint, Offset(left + w * 0.16f, top + h * 0.12f), Size(w * 0.28f, h * 0.76f), CornerRadius(6f), stroke)
                drawRoundRect(tint, Offset(left + w * 0.56f, top + h * 0.12f), Size(w * 0.28f, h * 0.76f), CornerRadius(6f), stroke)
            }
            "feet" -> {
                drawRoundRect(tint, Offset(left + w * 0.08f, top + h * 0.42f), Size(w * 0.36f, h * 0.36f), CornerRadius(8f), stroke)
                drawRoundRect(tint, Offset(left + w * 0.56f, top + h * 0.42f), Size(w * 0.36f, h * 0.36f), CornerRadius(8f), stroke)
            }
            "cloak" -> {
                val path = Path().apply {
                    moveTo(size.width / 2f, top + h * 0.08f)
                    lineTo(left + w * 0.92f, top + h * 0.88f)
                    lineTo(left + w * 0.08f, top + h * 0.88f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            "necklace" -> {
                drawArc(tint, 200f, 140f, false, Offset(left + w * 0.12f, top + h * 0.08f), Size(w * 0.76f, h * 0.72f), style = stroke)
                drawCircle(tint, radius = w * 0.08f, center = Offset(size.width / 2f, top + h * 0.72f))
            }
            "earring" -> {
                drawCircle(tint, radius = w * 0.10f, center = Offset(size.width / 2f, top + h * 0.22f), style = stroke)
                drawLine(tint, Offset(size.width / 2f, top + h * 0.32f), Offset(size.width / 2f, top + h * 0.62f), strokeWidth = stroke.width)
                drawCircle(tint, radius = w * 0.12f, center = Offset(size.width / 2f, top + h * 0.74f), style = stroke)
            }
            "ring" -> {
                drawCircle(tint, radius = w * 0.28f, center = Offset(size.width / 2f, size.height / 2f + h * 0.06f), style = stroke)
                drawCircle(tint, radius = w * 0.10f, center = Offset(size.width / 2f, top + h * 0.22f))
            }
            "bracelet" -> {
                drawCircle(tint, radius = w * 0.32f, center = Offset(size.width / 2f, size.height / 2f), style = stroke)
                drawCircle(tint, radius = w * 0.20f, center = Offset(size.width / 2f, size.height / 2f), style = stroke)
            }
            "belt" -> {
                drawRoundRect(tint, Offset(left + w * 0.06f, top + h * 0.38f), Size(w * 0.88f, h * 0.24f), CornerRadius(4f), stroke)
                drawRoundRect(tint, Offset(left + w * 0.38f, top + h * 0.32f), Size(w * 0.24f, h * 0.36f), CornerRadius(3f), stroke)
            }
            "bow", "crossbow" -> {
                drawArc(tint, 110f, 140f, false, Offset(left + w * 0.08f, top), Size(w * 0.84f, h), style = stroke)
                drawLine(tint, Offset(left + w * 0.28f, top + h * 0.18f), Offset(left + w * 0.28f, top + h * 0.82f), strokeWidth = stroke.width)
            }
            "sword", "sword2h" -> {
                drawLine(tint, Offset(size.width / 2f, top + h * 0.08f), Offset(size.width / 2f, top + h * 0.70f), strokeWidth = stroke.width * 1.4f)
                drawLine(tint, Offset(left + w * 0.22f, top + h * 0.70f), Offset(left + w * 0.78f, top + h * 0.70f), strokeWidth = stroke.width)
                drawLine(tint, Offset(size.width / 2f, top + h * 0.70f), Offset(size.width / 2f, top + h * 0.90f), strokeWidth = stroke.width)
            }
            "dagger" -> {
                drawLine(tint, Offset(size.width / 2f, top + h * 0.18f), Offset(size.width / 2f, top + h * 0.68f), strokeWidth = stroke.width * 1.2f)
                drawLine(tint, Offset(left + w * 0.30f, top + h * 0.68f), Offset(left + w * 0.70f, top + h * 0.68f), strokeWidth = stroke.width)
            }
            "spear" -> {
                val tip = Path().apply {
                    moveTo(size.width / 2f, top + h * 0.08f)
                    lineTo(left + w * 0.62f, top + h * 0.28f)
                    lineTo(left + w * 0.38f, top + h * 0.28f)
                    close()
                }
                drawPath(tip, tint, style = stroke)
                drawLine(tint, Offset(size.width / 2f, top + h * 0.28f), Offset(size.width / 2f, top + h * 0.90f), strokeWidth = stroke.width)
            }
            "staff", "wand", "orb" -> {
                drawLine(tint, Offset(size.width / 2f, top + h * 0.38f), Offset(size.width / 2f, top + h * 0.92f), strokeWidth = stroke.width)
                drawCircle(tint, radius = w * 0.16f, center = Offset(size.width / 2f, top + h * 0.22f), style = stroke)
            }
            "gauntlet" -> {
                drawRoundRect(tint, Offset(left + w * 0.22f, top + h * 0.18f), Size(w * 0.56f, h * 0.64f), CornerRadius(10f), stroke)
            }
            else -> {
                drawRoundRect(tint, Offset(left, top), Size(w, h), CornerRadius(8f), stroke)
            }
        }
    }
}

@Composable
private fun CharacterSilhouette(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val color = Palette.Accent.copy(alpha = 0.22f)
        val stroke = Palette.Accent.copy(alpha = 0.40f)
        val line = Stroke(width = 2.2f)
        drawCircle(color, radius = size.minDimension * 0.11f, center = Offset(cx, size.height * 0.14f))
        drawCircle(stroke, radius = size.minDimension * 0.11f, center = Offset(cx, size.height * 0.14f), style = line)
        val torso = Path().apply {
            moveTo(cx - size.width * 0.16f, size.height * 0.26f)
            lineTo(cx + size.width * 0.16f, size.height * 0.26f)
            lineTo(cx + size.width * 0.13f, size.height * 0.58f)
            lineTo(cx - size.width * 0.13f, size.height * 0.58f)
            close()
        }
        drawPath(torso, color)
        drawPath(torso, stroke, style = line)
        drawRoundRect(
            color,
            Offset(cx - size.width * 0.12f, size.height * 0.58f),
            Size(size.width * 0.09f, size.height * 0.28f),
            CornerRadius(6f),
        )
        drawRoundRect(
            color,
            Offset(cx + size.width * 0.03f, size.height * 0.58f),
            Size(size.width * 0.09f, size.height * 0.28f),
            CornerRadius(6f),
        )
        drawLine(stroke, Offset(cx - size.width * 0.16f, size.height * 0.30f), Offset(cx - size.width * 0.28f, size.height * 0.52f), strokeWidth = 3f)
        drawLine(stroke, Offset(cx + size.width * 0.16f, size.height * 0.30f), Offset(cx + size.width * 0.28f, size.height * 0.52f), strokeWidth = 3f)
    }
}

private fun normalizeSlot(slot: String): String =
    DisplayName.prettyEnum(slot)?.lowercase() ?: slot.lowercase()

private val ARMOR_SLOTS = listOf(
    "head" to "Head",
    "chest" to "Chest",
    "hands" to "Hands",
    "legs" to "Legs",
    "feet" to "Feet",
)

private val ACCESSORY_SLOTS = listOf(
    "cloak" to "Cloak",
    "necklace" to "Neck",
    "earring" to "Ear",
    "bracelet" to "Wrist",
    "ring" to "Ring",
    "belt" to "Belt",
)

private val WEAPON_SLOT_KEYS = setOf(
    "bow", "crossbow", "sword", "sword2h", "dagger", "spear", "gauntlet",
    "staff", "wand", "orb", "weapon",
)
