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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.solisium.desktop.AppModel
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

/**
 * Shown once, on the first run after installing, when a key is already on this machine.
 *
 * The point is that finding a key is the app's job, not the user's. The dialog names the
 * fingerprint and the file it came from so the user can tell it is theirs, states plainly
 * what will and will not happen to it, and defaults to doing nothing.
 */
@Composable
fun KeyOfferDialog(model: AppModel) {
    if (model.warehouseSetup != null || model.dropSyncOffer != null) return
    if (model.keys.offerChoice && model.keys.candidates.isNotEmpty()) {
        ManyKeysOfferDialog(model)
        return
    }
    val offer = model.keys.offer ?: return
    Dialog(onDismissRequest = { model.declineFoundKey() }) {
        Column(
            Modifier.width(560.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Surface)
                .border(1.dp, Palette.Border, RoundedCornerShape(14.dp))
                .padding(Spacing.xl),
        ) {
            Text(
                "Found an archive key on this PC",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Store it so you do not have to find it again? You do not need it to browse " +
                    "data you have already imported.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )

            Spacer(Modifier.height(Spacing.lg))
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Base)
                    .padding(Spacing.md),
            ) {
                Bold("fingerprint ${offer.fingerprint}")
                Spacer(Modifier.height(2.dp))
                Text(offer.source, style = MonoStyle, color = Palette.TextFaint)
                Text(
                    "recognised by ${offer.evidence}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Reassurance("It stays on this PC. It is never sent anywhere and never shared.")
            Reassurance("Only you can see it. The app shows the fingerprint above, never the key.")
            Reassurance("You can delete it at any time from Data, and removing the app leaves it untouched.")

            Spacer(Modifier.height(Spacing.md))
            Text(
                model.secretStorePath.toString(),
                style = MonoStyle,
                color = Palette.TextFaint,
            )

            Spacer(Modifier.height(Spacing.xl))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                ActionButton("No thanks", { model.declineFoundKey() })
                ActionButton("Store this key", { model.acceptFoundKey() }, primary = true)
            }
        }
    }
}

@Composable
private fun ManyKeysOfferDialog(model: AppModel) {
    val found = model.keys.candidates
    Dialog(onDismissRequest = { model.declineFoundKey() }) {
        Column(
            Modifier.width(560.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Surface)
                .border(1.dp, Palette.Border, RoundedCornerShape(14.dp))
                .padding(Spacing.xl),
        ) {
            Text(
                "Found ${found.size} archive keys on this PC",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Store one so you do not have to find it again? You do not need a key to browse " +
                    "data you have already imported.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(Spacing.lg))
            found.take(6).forEach { candidate ->
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Base)
                        .padding(Spacing.md),
                ) {
                    Bold("fingerprint ${candidate.fingerprint}")
                    Spacer(Modifier.height(2.dp))
                    Text(candidate.source, style = MonoStyle, color = Palette.TextFaint)
                }
                Spacer(Modifier.height(Spacing.sm))
            }
            if (found.size > 6) {
                Text(
                    "and ${found.size - 6} more on Data",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            Reassurance("It stays on this PC. It is never sent anywhere and never shared.")
            Reassurance("Only you can see it. The app shows fingerprints, never the key.")
            Spacer(Modifier.height(Spacing.xl))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                ActionButton("No thanks", { model.declineFoundKey() })
                ActionButton("Choose on Data", { model.chooseFoundKeysOnData() }, primary = true)
            }
        }
    }
}

@Composable
private fun Reassurance(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("-", style = MaterialTheme.typography.bodySmall, color = Palette.Extracted)
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}
