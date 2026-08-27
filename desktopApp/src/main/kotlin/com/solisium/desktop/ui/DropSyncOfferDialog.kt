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
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

/** Offered once after a successful warehouse import when drop cache is empty or stale. */
@Composable
fun DropSyncOfferDialog(model: AppModel) {
    if (model.warehouseSetup != null) return
    val offer = model.dropSyncOffer ?: return

    Dialog(onDismissRequest = { model.dismissDropSyncOffer() }) {
        Column(
            Modifier.width(580.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Surface)
                .border(1.dp, Palette.Border, RoundedCornerShape(14.dp))
                .padding(Spacing.xl),
        ) {
            Text(
                "Sync drop tables?",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Your warehouse has ${offer.monstersTotal} monster reward profiles. Pull loot tables " +
                    "from Questlog now so Drops works offline with locations and community rates.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            if (offer.dropRows > 0L) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "${offer.dropRows} drop rows cached from a previous sync — re-sync after patch imports.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Base)
                    .padding(Spacing.md),
            ) {
                Text(
                    "Exact client drop weights come from TLItemLotteryUnit in the warehouse. " +
                        "Questlog sync adds community rates and fills monsters without lottery data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Unverified,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                ActionButton("Later", { model.dismissDropSyncOffer() })
                ActionButton(
                    "Sync drop database",
                    { model.acceptDropSyncOffer() },
                    primary = true,
                )
            }
        }
    }
}
