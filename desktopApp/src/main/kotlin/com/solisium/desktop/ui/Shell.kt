package com.solisium.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solisium.desktop.AppModel
import com.solisium.desktop.Load
import com.solisium.desktop.Screen
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun AppShell(model: AppModel) {
    Row(Modifier.fillMaxSize().background(Palette.Base)) {
        NavRail(model)
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
        Column(Modifier.fillMaxSize()) {
            when (model.screen) {
                Screen.Overview -> OverviewScreen(model)
                Screen.Build -> BuildScreen(model)
                Screen.Catalog -> CatalogScreen(model)
                Screen.Character -> CharacterScreen(model)
                Screen.Combat -> CombatScreen(model)
                Screen.Data -> DataScreen(model)
            }
        }
    }
    // Sits above every screen, because it appears on first run before the user has
    // chosen where to go.
    KeyOfferDialog(model)
}

@Composable
private fun NavRail(model: AppModel) {
    Column(
        Modifier.width(216.dp).fillMaxHeight().background(Palette.Base)
            .padding(horizontal = Spacing.md, vertical = Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.sm)) {
            Box(
                Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp))
                    .background(Palette.Accent),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    "Solisium",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Palette.Text,
                )
                Text("Autopilot", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        Screen.entries.forEach { entry ->
            NavItem(
                label = entry.label,
                selected = model.screen == entry,
                onClick = { model.go(entry) },
            )
            Spacer(Modifier.height(2.dp))
        }

        Spacer(Modifier.height(Spacing.xl))
        Divider()
        Spacer(Modifier.height(Spacing.md))
        SnapshotChip(model)

        Spacer(Modifier.weight(1f))

        Text(
            "Read-only companion. No game files are modified.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val textColor by animateColorAsState(if (selected) Palette.Text else Palette.TextMuted)
    HoverRow(selected = selected, onClick = onClick) {
        Box(
            Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp))
                .background(if (selected) Palette.Accent else androidx.compose.ui.graphics.Color.Transparent),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = textColor,
            modifier = Modifier.padding(vertical = 9.dp),
        )
    }
}

/** Which dataset every number on screen came from, always visible. */
@Composable
private fun SnapshotChip(model: AppModel) {
    val overview = model.overview
    Column(Modifier.padding(horizontal = Spacing.sm)) {
        SectionLabel("Active dataset")
        Spacer(Modifier.height(Spacing.xs))
        when (overview) {
            is Load.Loading -> Text(
                "checking",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
            is Load.Err -> Text(
                "unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Danger,
            )
            is Load.Ok -> {
                val snapshot = overview.value.snapshot
                if (snapshot == null) {
                    Text(
                        "none imported",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Unverified,
                    )
                } else {
                    Text(
                        "build ${snapshot.gameBuild}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Text,
                    )
                    Text(
                        snapshot.gameVersion,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                    if (overview.value.buildWarning != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Badge("stale", Palette.Unverified)
                    }
                }
            }
        }
    }
}

/** Standard page header: title, one line of context, optional trailing content. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.Text)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
            trailing()
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
fun rowArrangement() = Arrangement.spacedBy(Spacing.md)
