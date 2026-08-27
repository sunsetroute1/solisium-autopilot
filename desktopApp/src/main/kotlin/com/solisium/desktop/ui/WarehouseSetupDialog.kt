package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.solisium.core.source.ImportProgress
import com.solisium.desktop.AppModel
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

/**
 * Shown once after install when a real TL-Helper warehouse is on disk. Demo starter data
 * loads immediately; this dialog offers to replace it with live game data and shows
 * import progress instead of doing it silently in the background.
 */
@Composable
fun WarehouseSetupDialog(model: AppModel) {
    val offer = model.warehouseSetup ?: return
    val importing = model.importing
    val progress = model.importProgress
    val failed = !importing && model.lastImport?.error != null &&
        model.lastImport?.label == "Game data"

    Dialog(onDismissRequest = { if (!importing) model.dismissWarehouseSetup() }) {
        Column(
            Modifier.width(580.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Surface)
                .border(1.dp, Palette.Border, RoundedCornerShape(14.dp))
                .padding(Spacing.xl),
        ) {
            Text(
                if (importing) "Importing game data" else "Import your warehouse",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                when {
                    importing ->
                        "Reading your TL-Helper warehouse and mapping it into Solisium. " +
                            "This usually takes under a minute."
                    offer.demoActive ->
                        "Demo data is loaded so you can explore the app. Import your real " +
                            "TL-Helper warehouse now to search live gear and get accurate build advice."
                    else ->
                        "We found a TL-Helper warehouse on this PC. Import it now so gear search " +
                            "and build advice match your game patch."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )

            Spacer(Modifier.height(Spacing.lg))
            offer.warehousePath?.let { path ->
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Base)
                        .padding(Spacing.md),
                ) {
                    offer.warehouseBuild?.let { build ->
                        Bold("build $build")
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(path.toString(), style = MonoStyle, color = Palette.TextFaint)
                    offer.reason?.let { reason ->
                        Spacer(Modifier.height(Spacing.sm))
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                }
            }

            if (importing) {
                Spacer(Modifier.height(Spacing.lg))
                WarehouseImportProgress(progress)
            } else if (failed) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    model.lastImport?.error ?: "Import failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Danger,
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            if (!importing) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                ) {
                    ActionButton(
                        if (offer.demoActive) "Keep demo data" else "Not now",
                        { model.dismissWarehouseSetup() },
                    )
                    ActionButton(
                        if (failed) "Try again" else "Import warehouse now",
                        { model.acceptWarehouseSetup() },
                        primary = true,
                        enabled = offer.warehousePath != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun WarehouseImportProgress(progress: ImportProgress?) {
    val phase = progress?.phase ?: "Starting import"
    val fraction = progress?.let { warehouseImportFraction(it) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(phase, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            if (progress != null && progress.total > 0) {
                Text(
                    "${progress.current} / ${progress.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Palette.Accent,
                trackColor = Palette.Border,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Palette.Accent,
                trackColor = Palette.Border,
            )
        }
    }
}

private fun warehouseImportFraction(progress: ImportProgress): Float {
    val slice = progress.fraction
    return when {
        progress.phase.contains("Reading", ignoreCase = true) ->
            ((slice ?: 0.5f) * 0.08f).coerceIn(0f, 0.08f)
        progress.phase.contains("Mapping game", ignoreCase = true) ->
            0.08f + ((slice ?: 0f) * 0.72f)
        progress.phase.contains("Linking materials", ignoreCase = true) -> 0.82f
        progress.phase.contains("stat curves", ignoreCase = true) -> 0.86f
        progress.phase.contains("item stats", ignoreCase = true) -> 0.90f
        progress.phase.contains("combat power", ignoreCase = true) -> 0.92f
        progress.phase.contains("monster drops", ignoreCase = true) -> 0.96f
        progress.phase.contains("Finishing", ignoreCase = true) -> 1f
        else -> slice ?: 0.05f
    }.coerceIn(0f, 1f)
}
