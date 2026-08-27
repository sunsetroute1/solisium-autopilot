package com.solisium.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.CombatPortfolio
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.CombatSkillTotal
import com.solisium.core.domain.CombatTrend
import com.solisium.core.query.CombatAnalyzer
import com.solisium.core.query.CombatInsight
import com.solisium.core.query.InsightSeverity
import com.solisium.core.source.CombatLogFolderStatus
import com.solisium.core.source.CombatLogSetupGuide
import com.solisium.desktop.AppModel
import com.solisium.desktop.FilePickers
import com.solisium.desktop.ImportOutcome
import com.solisium.desktop.Load
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun CombatScreen(model: AppModel) {
    LaunchedEffect(Unit) { model.refreshCombatLogDiscovery() }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Combat",
            subtitle = "Observed damage from T&L logs — crit rates, rotation shifts, build cross-check",
            trailing = { CombatImportActions(model) },
        )
        Column(Modifier.padding(horizontal = Spacing.xxl)) {
            CombatDiscoveryBanner(model)
            if (shouldShowSetupGuide(model)) {
                Spacer(Modifier.height(Spacing.md))
                CombatLoggingSetupGuide()
            }
            if (model.importing) {
                Spacer(Modifier.height(Spacing.md))
                LoadingRow("Importing combat log")
            }
            model.lastImport?.takeIf { it.label == "Combat logs" }?.let { outcome ->
                Spacer(Modifier.height(Spacing.md))
                CombatImportOutcome(outcome)
            }
            Spacer(Modifier.height(Spacing.md))
        }
        when (val state = model.combat) {
            is Load.Loading -> LoadingRow("Reading sessions")
            is Load.Err -> Column(Modifier.padding(horizontal = Spacing.xxl)) { ErrorState(state.message) }
            is Load.Ok -> {
                val sessions = state.value
                if (sessions.isEmpty()) {
                    Column(Modifier.padding(horizontal = Spacing.xxl)) {
                        EmptyCombatState(model)
                    }
                } else {
                    val portfolio = remember(sessions) { CombatAnalyzer.portfolio(sessions) }
                    var expandedId by remember { mutableStateOf<String?>(sessions.firstOrNull()?.sessionId) }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.xxl)) {
                        item(key = "portfolio") {
                            CombatPortfolioCard(portfolio)
                            Spacer(Modifier.height(Spacing.md))
                        }
                        val buildInsights = (model.advice as? Load.Ok)?.value?.combatInsights.orEmpty()
                        if (buildInsights.isNotEmpty()) {
                            item(key = "build-insights") {
                                CombatInsightsPanel(buildInsights)
                                Spacer(Modifier.height(Spacing.md))
                            }
                        }
                        itemsIndexed(sessions, key = { _, s -> s.sessionId }) { index, session ->
                            val previous = sessions.getOrNull(index + 1)
                            SessionCard(
                                session = session,
                                portfolio = portfolio,
                                compareTo = previous,
                                expanded = expandedId == session.sessionId,
                                onToggle = {
                                    expandedId = if (expandedId == session.sessionId) null else session.sessionId
                                },
                            )
                            Spacer(Modifier.height(Spacing.md))
                        }
                    }
                }
            }
        }
    }
}

private fun shouldShowSetupGuide(model: AppModel): Boolean {
    val hasSessions = (model.combat as? Load.Ok)?.value?.isNotEmpty() == true
    return !hasSessions
}

@Composable
private fun CombatPortfolioCard(portfolio: CombatPortfolio) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Combat portfolio", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    "${portfolio.sessionCount} imported session(s) · ${formatLong(portfolio.totalDamage)} total damage",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
            Badge(trendLabel(portfolio.dpsTrend), trendColor(portfolio.dpsTrend), caps = false)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(
                label = "Avg DPS",
                value = portfolio.avgDps?.let { "%.0f".format(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Best DPS",
                value = portfolio.bestDps?.let { "%.0f".format(it) } ?: "—",
                hint = portfolio.topSkillName?.let { "top skill: $it" },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Crit rate",
                value = portfolio.overallCritRate?.let { "${(it * 100).toInt()}%" } ?: "—",
                hint = portfolio.overallHeavyRate?.let { "heavy ${(it * 100).toInt()}%" },
                modifier = Modifier.weight(1f),
            )
        }
        if (portfolio.insights.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            SectionLabel("Patterns")
            portfolio.insights.forEach { insight ->
                Spacer(Modifier.height(Spacing.xs))
                Text(insight, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
    }
}

private fun trendLabel(trend: CombatTrend): String = when (trend) {
    CombatTrend.Up -> "DPS trending up"
    CombatTrend.Down -> "DPS trending down"
    CombatTrend.Flat -> "DPS stable"
    CombatTrend.Unknown -> "need more logs"
}

private fun trendColor(trend: CombatTrend) = when (trend) {
    CombatTrend.Up -> Palette.Extracted
    CombatTrend.Down -> Palette.Unverified
    CombatTrend.Flat -> Palette.TextMuted
    CombatTrend.Unknown -> Palette.TextFaint
}

@Composable
private fun CombatLoggingSetupGuide() {
    Card(Modifier.fillMaxWidth()) {
        Text(
            CombatLogSetupGuide.title,
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Text,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Throne and Liberty added this in update 3.11. It is configured through the Ring Menu, not Gameplay settings.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(Spacing.md))
        CombatLogSetupGuide.steps.forEachIndexed { index, step ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text("${index + 1}.", style = MonoStyle, color = Palette.TextFaint)
                Text(step, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(CombatLogSetupGuide.resetNote, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
        Spacer(Modifier.height(Spacing.xs))
        Text(CombatLogSetupGuide.guildRaidNote, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
        Spacer(Modifier.height(Spacing.sm))
        Text(CombatLogSetupGuide.logFolderNote, style = MonoStyle, color = Palette.TextFaint)
    }
}

@Composable
private fun CombatDiscoveryBanner(model: AppModel) {
    val discovery = model.combatLogDiscovery
    val tint = when (discovery.status) {
        CombatLogFolderStatus.FOUND_WITH_LOGS -> Palette.Extracted
        CombatLogFolderStatus.FOUND_EMPTY -> Palette.Unverified
        CombatLogFolderStatus.MISSING_BUT_SAVED_EXISTS -> Palette.Unverified
        CombatLogFolderStatus.TL_NOT_INSTALLED -> Palette.TextFaint
    }
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (discovery.status) {
                        CombatLogFolderStatus.FOUND_WITH_LOGS -> "Logs found on disk"
                        CombatLogFolderStatus.FOUND_EMPTY -> "CombatLogs folder is empty"
                        CombatLogFolderStatus.MISSING_BUT_SAVED_EXISTS -> "Enable combat logging in game"
                        CombatLogFolderStatus.TL_NOT_INSTALLED -> "Game save folder not found"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(discovery.hint(), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                discovery.primaryFolder?.let { folder ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(folder.toString(), style = MonoStyle, color = Palette.TextFaint)
                }
                if (discovery.logFiles.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Newest: ${discovery.logFiles.first().fileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                }
            }
            if (discovery.status == CombatLogFolderStatus.FOUND_WITH_LOGS) {
                Badge("${discovery.logFiles.size} file(s)", Palette.Extracted)
            }
        }
    }
}

@Composable
private fun CombatImportActions(model: AppModel) {
    val discovery = model.combatLogDiscovery
    val canImport = !model.importing &&
        (discovery.status == CombatLogFolderStatus.FOUND_WITH_LOGS || discovery.logFiles.isNotEmpty())
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ActionButton(
            label = "Import all",
            onClick = { model.importCombatLogs(null) },
            primary = true,
            enabled = canImport,
        )
        ActionButton(
            label = "Scan & import",
            onClick = {
                model.refreshCombatLogDiscovery()
                model.importCombatLogs(null)
            },
            enabled = !model.importing && discovery.savedRoot != null,
        )
        ActionButton(
            label = "Choose file",
            onClick = {
                FilePickers.pickFile("Select a combat log", ".txt", model.detectedLogFolder)
                    ?.let { model.importCombatLogs(it) }
            },
            enabled = !model.importing,
        )
        ActionButton(
            label = "Choose folder",
            onClick = {
                FilePickers.pickDirectory("Select a combat log folder", model.detectedLogFolder)
                    ?.let { model.importCombatLogs(it) }
            },
            enabled = !model.importing,
        )
    }
}

@Composable
private fun EmptyCombatState(model: AppModel) {
    Card {
        Text("No combat logs imported yet", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(Spacing.lg))
        CombatImportActions(model)
    }
}

@Composable
fun CombatInsightsPanel(insights: List<CombatInsight>) {
    if (insights.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        SectionLabel("Combat vs build")
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Compares imported DamageDone logs with your character sheet skill bar.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(Spacing.md))
        insights.forEach { insight ->
            val color = when (insight.severity) {
                InsightSeverity.Warn -> Palette.Unverified
                InsightSeverity.Positive -> Palette.Extracted
                InsightSeverity.Info -> Palette.TextMuted
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Badge(
                    when (insight.severity) {
                        InsightSeverity.Warn -> "check"
                        InsightSeverity.Positive -> "signal"
                        InsightSeverity.Info -> "note"
                    },
                    color,
                    caps = false,
                )
                Text(insight.message, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            }
        }
    }
}

@Composable
private fun CombatImportOutcome(outcome: ImportOutcome) {
    val failed = outcome.error != null
    val tint = if (failed) Palette.Danger else Palette.Extracted
    Card(Modifier.fillMaxWidth()) {
        Text(
            if (failed) "Combat log import failed" else "Combat log imported",
            style = MaterialTheme.typography.titleSmall,
            color = tint,
        )
        Spacer(Modifier.height(Spacing.xs))
        if (failed) {
            Text(outcome.error!!, style = MonoStyle, color = Palette.TextMuted)
        } else {
            val fileCount = outcome.receipts.size
            Text(
                "${fileCount} file(s) · ${formatLong(outcome.imported.toLong())} events · " +
                    "${formatLong(outcome.skipped.toLong())} skipped (duplicates)",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
        outcome.warnings.take(4).forEach { warning ->
            Spacer(Modifier.height(2.dp))
            Text(warning, style = MaterialTheme.typography.bodySmall, color = Palette.Unverified)
        }
    }
}

@Composable
private fun SessionCard(
    session: CombatSessionSummary,
    portfolio: CombatPortfolio,
    compareTo: CombatSessionSummary?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val compare = compareTo?.let { CombatAnalyzer.compare(it, session) }
    val highlights = remember(session, portfolio) { CombatAnalyzer.sessionHighlights(session, portfolio) }

    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.primaryTarget ?: session.startedAt ?: "unknown session",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
                Text(
                    buildString {
                        session.startedAt?.let { append(it) }
                        session.durationSeconds?.let { append(" · ${fmtDuration(it)}") }
                        append(" · log v${session.logVersion ?: "?"}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
            compare?.dpsDeltaPct?.let { delta ->
                val up = delta >= 0
                Badge(
                    "${if (up) "+" else ""}${(delta * 100).toInt()}% DPS",
                    if (up) Palette.Extracted else Palette.Unverified,
                    caps = false,
                )
            }
            Badge(if (expanded) "expanded" else "tap detail", Palette.TextFaint, caps = false)
        }

        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(
                label = "Observed damage",
                value = formatLong(session.observedDamageSum),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Observed DPS",
                value = session.observedDps?.let { "%.0f".format(it) } ?: "—",
                hint = "sum ÷ log duration",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Crit / heavy",
                value = session.critRate?.let { "${(it * 100).toInt()}%" } ?: "—",
                hint = session.heavyRate?.let { "heavy ${(it * 100).toInt()}%" },
                modifier = Modifier.weight(1f),
            )
        }

        if (expanded) {
            if (compare != null && compare.skillShareShifts.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel("vs prior session")
                Text(compare.headline, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                compare.skillShareShifts.take(4).forEach { shift ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "${shift.skillName}: ${pct(shift.baselineShare)} → ${pct(shift.currentShare)} " +
                            "(${signedPp(shift.deltaPp)})",
                        style = MonoStyle,
                        color = Palette.TextFaint,
                    )
                }
            }

            if (session.targets.size > 1) {
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel("Targets")
                session.targets.forEach { target ->
                    ShareBar(
                        label = target.targetName,
                        share = target.damageShare,
                        trailing = formatLong(target.observedDamageSum),
                    )
                }
            }

            if (session.skillTotals.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel("Skill breakdown")
                session.skillTotals.forEach { total ->
                    SkillDamageRow(total)
                }
            }

            if (highlights.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel("Session notes")
                highlights.forEach { note ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }

            session.sourcePath?.let { path ->
                Spacer(Modifier.height(Spacing.sm))
                Text(path, style = MonoStyle, color = Palette.TextFaint, maxLines = 1)
            }
        }

        if (!expanded && session.skillTotals.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            val top = session.skillTotals.first()
            Text(
                "Top skill: ${top.skillName ?: top.skillId} (${pct(top.damageShare)} · " +
                    "${top.critRate?.let { "${(it * 100).toInt()}% crit" } ?: "${formatLong(top.hits)} hits"})",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun SkillDamageRow(total: CombatSkillTotal) {
    val trailing = buildString {
        append(formatLong(total.observedDamageSum))
        append(" · ")
        append(formatLong(total.hits))
        append(" hits")
        total.critRate?.let { append(" · ${(it * 100).toInt()}% crit") }
        total.heavyRate?.takeIf { it > 0 }?.let { append(" · ${(it * 100).toInt()}% heavy") }
        total.avgHit?.let { append(" · ~${formatLong(it)}/hit") }
    }
    ShareBar(
        label = total.skillName ?: total.skillId ?: "unnamed",
        share = total.damageShare,
        trailing = trailing,
    )
}

private fun pct(v: Double): String = "${(v * 100).toInt()}%"

private fun signedPp(v: Double): String {
    val pp = (v * 100).toInt()
    return if (pp >= 0) "+${pp}pp" else "${pp}pp"
}

private fun fmtDuration(seconds: Double): String = when {
    seconds >= 120 -> "%.1f min".format(seconds / 60)
    else -> "%.0f s".format(seconds)
}
