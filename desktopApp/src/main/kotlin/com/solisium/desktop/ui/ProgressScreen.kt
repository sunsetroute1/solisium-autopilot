package com.solisium.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.LiveProgressionSnapshot
import com.solisium.core.domain.ProgressionCadence
import com.solisium.core.domain.ProgressionDifficulty
import com.solisium.core.domain.ProgressionEase
import com.solisium.core.domain.ProgressionPlan
import com.solisium.core.domain.ProgressionRecommendation
import com.solisium.core.domain.ProgressionCharacterSnapshot
import com.solisium.desktop.AppModel
import com.solisium.desktop.Load
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProgressScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Progress",
            "What to do next for progression — ranked by ease and value. Syncs from game files and clipboard paste.",
        ) {
            ActionButton(
                if (model.progressionRefreshing) "Syncing…" else "Sync & refresh",
                onClick = { model.syncLiveProgressionFromFiles() },
                primary = true,
                enabled = !model.progressionRefreshing,
            )
        }
        when (val state = model.progressionPlan) {
            is Load.Loading -> LoadingRow("Analyzing your sheet")
            is Load.Err -> Column(Modifier.padding(horizontal = Spacing.xxl)) { ErrorState(state.message) }
            is Load.Ok -> ProgressBody(model, state.value)
        }
    }
}

@Composable
private fun ProgressBody(model: AppModel, plan: ProgressionPlan) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xxl, vertical = Spacing.md),
    ) {
        CharacterSummaryCard(plan.character, plan.openCount, plan.completedCount)
        Spacer(Modifier.height(Spacing.lg))
        val buildPlan = (model.plan as? Load.Ok)?.value
        WatermarkCalculatorCard(
            model,
            buildPlan?.watermark ?: model.currentWatermarkPlan(),
            compact = true,
        )
        Spacer(Modifier.height(Spacing.lg))
        LiveSyncCard(model, plan.live)
        Spacer(Modifier.height(Spacing.lg))
        plan.notes.forEach { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            Spacer(Modifier.height(Spacing.xs))
        }
        Spacer(Modifier.height(Spacing.md))
        val open = plan.recommendations.filter { !it.completed }.sortedByDescending { it.priorityScore }
        val done = plan.recommendations.filter { it.completed }
        if (open.isEmpty() && plan.character.id == null) {
            EmptyState(
                "No character loaded",
                "Select a character on the Character screen, or import one, so Solisium can rank build gaps and layers.",
            )
            Spacer(Modifier.height(Spacing.md))
            ActionButton("Open Character", { model.go(com.solisium.desktop.Screen.Character) }, primary = true)
            return
        }
        if (open.isNotEmpty()) {
            ProgressionCadence.entries.forEach { cadence ->
                val group = open.filter { it.cadence == cadence }.sortedByDescending { it.priorityScore }
                if (group.isEmpty()) return@forEach
                SectionLabel("${cadence.label} · ${group.size}")
                Spacer(Modifier.height(Spacing.sm))
                group.forEach { task ->
                    TaskRow(task) { model.toggleProgressionTask(task.id) }
                    Spacer(Modifier.height(Spacing.sm))
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
        if (done.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xl))
            SectionLabel("Completed · ${done.size}")
            Spacer(Modifier.height(Spacing.sm))
            done.forEach { task ->
                TaskRow(task) { model.toggleProgressionTask(task.id) }
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun LiveSyncCard(model: AppModel, live: LiveProgressionSnapshot?) {
    Card(Modifier.fillMaxWidth()) {
        SectionLabel("Live sync")
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Reads NCStorageLocalData.ini from your T&L Saved folder, or paste commission/codex UI text from the game (Ctrl+A, Ctrl+C).",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActionButton("Sync game files", { model.syncLiveProgressionFromFiles() }, enabled = !model.progressionRefreshing)
            ActionButton("Import clipboard", { model.importProgressionFromClipboard() }, enabled = !model.progressionRefreshing)
            ActionButton("Apply paste", { model.applyProgressionPaste() }, primary = true, enabled = !model.progressionRefreshing)
        }
        Spacer(Modifier.height(Spacing.md))
        BasicTextField(
            value = model.progressionPasteText,
            onValueChange = { model.onProgressionPaste(it) },
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Palette.Text),
            cursorBrush = SolidColor(Palette.Accent),
            modifier = Modifier.fillMaxWidth().height(96.dp),
        )
        if (live != null) {
            Spacer(Modifier.height(Spacing.md))
            val synced = if (live.syncedAtEpochMs > 0L) {
                SYNC_TIME.format(Instant.ofEpochMilli(live.syncedAtEpochMs).atZone(ZoneId.systemDefault()))
            } else {
                "just now"
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Badge("${live.itemCount} live signal(s)", Palette.Derived, caps = false)
                if (live.sources.isNotEmpty()) {
                    Badge(live.sources.joinToString(", "), Palette.Unverified, caps = false)
                }
                Badge("synced $synced", Palette.TextFaint, caps = false)
            }
            live.warnings.forEach { warning ->
                Spacer(Modifier.height(Spacing.xs))
                Text(warning, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
            }
        }
    }
}

private val SYNC_TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun CharacterSummaryCard(
    character: ProgressionCharacterSnapshot,
    openCount: Int,
    completedCount: Int,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(
                    character.name ?: "No character",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    listOfNotNull(
                        character.level?.let { "Level $it" },
                        character.combatPower?.let { "CP $it" },
                        character.gearScore?.let { "GS $it" },
                        character.classLabel,
                        character.buildGoalLabel?.let { "Goal: $it" },
                    ).joinToString(" · ").ifEmpty { "Load a character for personalized ranks." },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Badge("$openCount open", Palette.Cool)
                Spacer(Modifier.height(Spacing.xs))
                Badge("$completedCount done", Palette.Extracted)
            }
        }
    }
}

@Composable
private fun TaskRow(task: ProgressionRecommendation, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (task.completed) Palette.TextFaint else Palette.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    EaseBadge(task.ease)
                    Spacer(Modifier.width(Spacing.xs))
                    DifficultyBadge(task.difficulty)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    task.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                if (task.reasons.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        task.reasons.joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Badge(task.cadence.label, Palette.TextFaint, caps = false)
                    Badge(task.category, Palette.TextFaint, caps = false)
                    Badge("value ${task.progressionValue}", Palette.Derived, caps = false)
                    if (task.source != "catalog") {
                        Badge(task.source, Palette.Unverified, caps = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun EaseBadge(ease: ProgressionEase) {
    val color = when (ease) {
        ProgressionEase.Easy -> Palette.Uncommon
        ProgressionEase.Moderate -> Color(0xFFE8C36A)
        ProgressionEase.Hard -> Palette.Danger
    }
    Badge(ease.label, color, caps = false)
}

@Composable
private fun DifficultyBadge(difficulty: ProgressionDifficulty) {
    Badge(difficulty.label, Palette.TextMuted, caps = false)
}
