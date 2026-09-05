package com.solisium.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.ItemGradeHints
import com.solisium.desktop.theme.appScrollbarStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

/** Colour for a confidence label. Unknown labels fall back to the cautious colour. */
fun confidenceColor(confidence: String?): Color = when (confidence?.lowercase()) {
    "extracted" -> Palette.Extracted
    "derived", "computed" -> Palette.Derived
    else -> Palette.Unverified
}

/**
 * A small provenance chip. Every number surfaced from the catalog is expected to be
 * shown next to one of these, so the user can always tell extracted values apart from
 * anything we inferred.
 */
@Composable
fun Badge(
    text: String,
    color: Color = Palette.TextMuted,
    modifier: Modifier = Modifier,
    caps: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (caps) text.uppercase() else text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(12.dp))
            .padding(Spacing.lg),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Palette.TextMuted,
        modifier = modifier,
    )
}

/** One headline number with its label. Used for the catalog coverage grid. */
@Composable
fun StatTile(
    label: String,
    value: String,
    hint: String? = null,
    accent: Color = Palette.Text,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
    }
}

/** A labelled row of key/value text, aligned so a column of them scans cleanly. */
@Composable
fun KeyValueRow(
    key: String,
    value: String,
    valueColor: Color = Palette.Text,
    keyWidth: androidx.compose.ui.unit.Dp = 148.dp,
    mono: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            key,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
            modifier = Modifier.width(keyWidth),
        )
        Text(
            value,
            style = if (mono) com.solisium.desktop.theme.MonoStyle else LocalTextStyle.current
                .merge(MaterialTheme.typography.bodyMedium),
            color = valueColor,
        )
    }
}

/** A clickable row that lights up on hover. Shared by the nav rail and result lists. */
@Composable
fun HoverRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> Palette.SurfaceHover
            hovered -> Palette.SurfaceHigh
            else -> Color.Transparent
        },
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun BasicSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
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
fun LoadingRow(text: String = "Loading") {
    Row(
        Modifier.fillMaxWidth().padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = Palette.Accent,
            strokeWidth = 2.dp,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

/**
 * Shown wherever the catalog genuinely has nothing rather than where a query failed.
 * The distinction matters: an empty table is a data-coverage fact, not an error.
 */
@Composable
fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(Spacing.xl)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextMuted)
        Spacer(Modifier.height(Spacing.xs))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
    }
}

@Composable
fun ErrorState(message: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Danger.copy(alpha = 0.10f))
            .border(1.dp, Palette.Danger.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(Spacing.md),
    ) {
        Text("Could not read the catalog", style = MaterialTheme.typography.titleMedium, color = Palette.Danger)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = com.solisium.desktop.theme.MonoStyle, color = Palette.TextMuted)
    }
}

/**
 * The one button style in the app. `primary` marks the action a screen exists for;
 * everything else is quieter so a panel never has two competing calls to action.
 */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint = if (primary) Palette.Accent else Palette.TextMuted
    val fill by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            hovered -> tint.copy(alpha = 0.20f)
            primary -> Palette.AccentSoft
            else -> Palette.SurfaceHigh
        },
    )
    val content = if (enabled) tint else Palette.TextFaint
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(fill)
            .border(1.dp, content.copy(alpha = if (enabled) 0.45f else 0.2f), RoundedCornerShape(7.dp))
            .hoverable(interaction)
            .let { if (enabled) it.clickable(interactionSource = interaction, indication = null, onClick = onClick) else it }
            .padding(horizontal = Spacing.md, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = content)
    }
}

fun displayGrade(sourceRowId: String?, grade: String?): String? =
    ItemGradeHints.resolve(grade, sourceRowId)

fun rarityColor(grade: String?): Color {
    val resolved = grade?.let { ItemGradeHints.normalizeGrade(it) ?: it }
    val token = DisplayName.prettyEnum(resolved)?.uppercase() ?: return Palette.Text
    return when {
        token in setOf("AAA", "LEGENDARY", "HEROIC", "MYTHIC") -> Palette.Gold
        token.startsWith("EPIC") || token == "AA" -> Palette.Epic
        token.startsWith("RARE") || token == "A" -> Palette.Rare
        token == "B" || token == "UNCOMMON" -> Palette.Uncommon
        token == "C" || token == "COMMON" -> Palette.Text
        else -> Palette.Text
    }
}

fun hasRarityColor(grade: String?, sourceRowId: String? = null): Boolean {
    val resolved = displayGrade(sourceRowId, grade) ?: return false
    return rarityColor(resolved) != Palette.Text
}

/** Subtle left tint so rarity is obvious even at a glance. */
fun Modifier.rarityRowTint(grade: String?, sourceRowId: String? = null): Modifier {
    val resolved = displayGrade(sourceRowId, grade) ?: return this
    val tint = rarityColor(resolved)
    if (tint == Palette.Text) return this
    return this.background(tint.copy(alpha = 0.10f))
}

/** Grade token from catalog hits when [CatalogHit.detail] holds item/rune rarity. */
fun catalogHitGrade(hit: com.solisium.core.domain.CatalogHit?): String? =
    when (hit?.kind) {
        "item", "rune" -> hit.detail
        else -> null
    }

/** Best-effort grade parsed from Questlog-style detail strings (`type · category · Epic`). */
fun detailGradeToken(detail: String?): String? =
    detail?.split(" · ")
        ?.map { it.trim() }
        ?.firstOrNull { part -> hasRarityColor(part) || ItemGradeHints.normalizeGrade(part) != null }

@Composable
fun RarityBadge(grade: String?, modifier: Modifier = Modifier) {
    val label = prettyEnum(grade) ?: return
    Badge(label, rarityColor(grade), modifier = modifier, caps = false)
}

@Composable
fun ItemNameWithRarity(
    name: String,
    grade: String?,
    modifier: Modifier = Modifier,
    sourceRowId: String? = null,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    pipHeight: androidx.compose.ui.unit.Dp = 20.dp,
    maxLines: Int = Int.MAX_VALUE,
) {
    val resolved = displayGrade(sourceRowId, grade)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RarityPip(resolved, Modifier.height(pipHeight))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            name,
            style = style,
            color = rarityColor(resolved),
            maxLines = maxLines,
        )
    }
}

@Composable
fun RarityPip(grade: String?, modifier: Modifier = Modifier, sourceRowId: String? = null) {
    val resolved = displayGrade(sourceRowId, grade)
    Box(
        modifier
            .width(5.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(rarityColor(resolved)),
    )
}
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
}

@Composable
fun Bold(text: String, color: Color = Palette.Text, grade: String? = null, sourceRowId: String? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = grade?.let { rarityColor(displayGrade(sourceRowId, it)) }
            ?: color,
    )
}

@Composable
fun LazyListWithScrollbar(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val adapter = rememberScrollbarAdapter(state)
    Box(modifier) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            contentPadding = contentPadding,
            content = content,
        )
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(14.dp)
                .background(Palette.SurfaceHigh.copy(alpha = 0.85f)),
        ) {
            VerticalScrollbar(
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                adapter = adapter,
                style = appScrollbarStyle(),
            )
        }
    }
}

@Composable
fun ColumnWithScrollbar(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val adapter = rememberScrollbarAdapter(state)
    Box(modifier) {
        // Scroll height follows content; fillMaxSize on the scroller created empty scroll space.
        Column(Modifier.fillMaxWidth().verticalScroll(state), content = content)
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(14.dp)
                .background(Palette.SurfaceHigh.copy(alpha = 0.85f)),
        ) {
            VerticalScrollbar(
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                adapter = adapter,
                style = appScrollbarStyle(),
            )
        }
    }
}
