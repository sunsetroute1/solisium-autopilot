package com.solisium.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.AxisScore
import com.solisium.core.domain.SlotAdvice
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
fun SlotPip(label: String, slot: SlotAdvice?, modifier: Modifier = Modifier) {
    val equipped = slot?.equipped != null
    val recommended = !slot?.recommended.isNullOrEmpty()
    val border = when {
        recommended -> Palette.Accent.copy(alpha = 0.45f)
        equipped -> Palette.Cool.copy(alpha = 0.45f)
        else -> Palette.Border
    }
    Column(
        modifier.width(88.dp).clip(RoundedCornerShape(8.dp))
            .background(if (equipped) Palette.SurfaceHigh else Palette.Surface)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.size(10.dp).clip(RoundedCornerShape(5.dp))
                .background(if (equipped) Palette.Extracted else Palette.BorderStrong),
        )
    }
}
