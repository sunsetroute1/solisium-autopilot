package com.solisium.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.GameCurvePoint
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

/** Distinct enough to tell four or five overlapping stat lines apart on a dark surface. */
private val SeriesColors = listOf(
    Color(0xFFE3B15C),
    Color(0xFF6FB3D2),
    Color(0xFF5BC8A0),
    Color(0xFFC49BE0),
    Color(0xFFE0857A),
    Color(0xFF9FC26B),
)

/**
 * Plots one shared curve: cumulative client value against level, one line per stat.
 * Deliberately plain — it reports stored numbers and does not smooth, extrapolate,
 * or project beyond the levels present in the data.
 */
@Composable
fun CurveChart(points: List<GameCurvePoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val series = remember(points) {
        points.groupBy { it.statName ?: it.statKey }
            .mapValues { (_, pts) -> pts.sortedBy { it.level } }
            .entries
            .sortedByDescending { entry -> entry.value.maxOf { it.rawValue } }
    }
    val levels = remember(points) { points.map { it.level } }
    val minLevel = levels.min()
    val maxLevel = levels.max()
    val maxValue = points.maxOf { it.rawValue }

    if (maxLevel == minLevel) {
        SingleLevelTable(series.map { it.key to it.value })
        return
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(8.dp))
                .background(Palette.Base).padding(Spacing.sm),
        ) {
            Canvas(Modifier.fillMaxWidth().height(116.dp)) {
                val left = 4f
                val right = size.width - 4f
                val top = 6f
                val bottom = size.height - 14f
                val span = (maxLevel - minLevel).toFloat()

                fun x(level: Long) = left + (level - minLevel) / span * (right - left)
                fun y(value: Long) =
                    bottom - (if (maxValue == 0L) 0f else value.toFloat() / maxValue) * (bottom - top)

                // Horizontal guides at 0, half, and max of the value range.
                listOf(0L, maxValue / 2, maxValue).distinct().forEach { guide ->
                    drawLine(
                        color = Palette.Border,
                        start = Offset(left, y(guide)),
                        end = Offset(right, y(guide)),
                        strokeWidth = 1f,
                    )
                }

                series.forEachIndexed { index, entry ->
                    val color = SeriesColors[index % SeriesColors.size]
                    val pts = entry.value
                    val path = Path()
                    pts.forEachIndexed { i, point ->
                        val px = x(point.level)
                        val py = y(point.rawValue)
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, color = color, style = Stroke(width = 2f))
                    pts.forEach { point ->
                        drawCircle(color, radius = 2.5f, center = Offset(x(point.level), y(point.rawValue)))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("+$minLevel", style = MonoStyle, color = Palette.TextFaint)
                Text("+$maxLevel", style = MonoStyle, color = Palette.TextFaint)
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        Legend(series.map { it.key to it.value.maxOf { p -> p.rawValue } })
    }
}

@Composable
private fun Legend(entries: List<Pair<String, Long>>) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        entries.forEachIndexed { index, (label, peak) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp).clip(RoundedCornerShape(2.dp))
                        .background(SeriesColors[index % SeriesColors.size]),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                Spacer(Modifier.width(Spacing.xs))
                Text("max ${peak.format()}", style = MonoStyle, color = Palette.TextFaint)
            }
        }
    }
}

/** A curve with only one level is a table, not a trend; drawing a line would imply one. */
@Composable
private fun SingleLevelTable(series: List<Pair<String, List<GameCurvePoint>>>) {
    Column(Modifier.fillMaxWidth()) {
        series.forEach { (label, pts) ->
            KeyValueRow(
                key = label,
                value = pts.joinToString(", ") { "+${it.level} = ${it.rawValue.format()}" },
                keyWidth = 170.dp,
            )
        }
    }
}
