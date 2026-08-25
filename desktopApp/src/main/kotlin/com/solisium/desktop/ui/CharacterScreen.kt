package com.solisium.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.ResolvedLoadoutLine
import com.solisium.core.domain.UserCharacter
import com.solisium.desktop.AppModel
import com.solisium.desktop.FilePickers
import com.solisium.desktop.Load
import com.solisium.desktop.theme.MonoStyle
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun CharacterScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Character",
            "Your loadout, matched against the active dataset.",
        ) {
            ActionButton(
                "Import character JSON",
                onClick = {
                    FilePickers.pickFile("Select a character JSON", ".json")
                        ?.let { model.importCharacter(it) }
                },
                enabled = !model.importing,
            )
        }
        when (val state = model.characters) {
            is Load.Loading -> LoadingRow("Reading characters")
            is Load.Err -> Column(Modifier.padding(horizontal = Spacing.xxl)) { ErrorState(state.message) }
            is Load.Ok -> if (state.value.isEmpty()) {
                Column(Modifier.padding(horizontal = Spacing.xxl)) {
                    Card {
                        Text(
                            "No character imported",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.Text,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Throne and Liberty exposes no character data to third parties, so a " +
                                "loadout has to be entered by hand. Import a JSON describing your " +
                                "gear, traits, runes and skills, and it will be resolved against " +
                                "the extracted catalog.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextMuted,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        CodeLine("examples/character.json")
                    }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    CharacterList(model, state.value)
                    Divider(Modifier.width(1.dp).fillMaxHeight())
                    SheetPane(model, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CharacterList(model: AppModel, characters: List<UserCharacter>) {
    LazyColumn(Modifier.width(280.dp).fillMaxHeight().padding(horizontal = Spacing.lg)) {
        items(characters, key = { it.id }) { character ->
            HoverRow(
                selected = character.id == model.selectedCharacterId,
                onClick = { model.selectCharacter(character.id) },
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Column(Modifier.weight(1f).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    Bold(character.name)
                    Text(
                        listOfNotNull(
                            character.level?.let { "Level $it" },
                            character.combatPower?.let { "CP ${it.format()}" },
                            character.server,
                        ).joinToString(" · ").ifEmpty { character.id },
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetPane(model: AppModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().padding(horizontal = Spacing.xl)) {
        when (val state = model.characterSheet) {
            null -> EmptyState("Select a character", "Pick a character to see the resolved loadout.")
            is Load.Loading -> LoadingRow("Resolving loadout")
            is Load.Err -> ErrorState(state.message)
            is Load.Ok -> SheetBody(state.value)
        }
    }
}

@Composable
private fun SheetBody(resolved: ResolvedCharacterSheet) {
    val sheet = resolved.sheet
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        sheet.character.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Text,
                    )
                    Text(
                        "Resolved against build ${resolved.snapshotBuild ?: "none"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                }
                Badge("manual", Palette.Unverified)
            }
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                sheet.character.level?.let {
                    StatTile("Level", it.toString(), modifier = Modifier.weight(1f))
                }
                sheet.character.combatPower?.let {
                    StatTile("Combat power", it.format(), modifier = Modifier.weight(1f))
                }
                sheet.cookingLevel?.let {
                    StatTile("Cooking", it.toString(), modifier = Modifier.weight(1f))
                }
                StatTile(
                    "Loadout entries",
                    resolved.lines.size.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            // An unresolved key means the loadout names something this dataset does not
            // contain, usually because the JSON predates the imported build.
            if (resolved.unresolvedCount > 0) {
                Spacer(Modifier.height(Spacing.md))
                WarningBanner(
                    message = "${resolved.unresolvedCount} of ${resolved.lines.size} loadout entries are " +
                        "not in this dataset.",
                    label = "unresolved",
                    detail = "Those keys may come from a different game build, or be misspelled. " +
                        "Names are never guessed.",
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))

        resolved.lines.groupBy { it.kind }.forEach { (kind, lines) ->
            SectionLabel("${prettyEnum(kind) ?: kind} · ${lines.size}")
            Spacer(Modifier.height(Spacing.sm))
            Card(Modifier.fillMaxWidth()) {
                lines.forEachIndexed { index, line ->
                    if (index > 0) Divider(Modifier.padding(vertical = Spacing.sm))
                    LoadoutLine(line)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }

        if (sheet.currency.isNotEmpty() || sheet.goals.isNotEmpty() || sheet.builds.isNotEmpty()) {
            SectionLabel("Other")
            Spacer(Modifier.height(Spacing.sm))
            Card(Modifier.fillMaxWidth()) {
                sheet.currency.forEach { KeyValueRow(it.currency, it.amount.format()) }
                sheet.goals.forEach { KeyValueRow(it.goalType, it.label) }
                sheet.builds.forEach { KeyValueRow("Build", it.name) }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun LoadoutLine(line: ResolvedLoadoutLine) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The slot stays visible even once an item resolves, so a loadout can be read
        // slot-by-slot rather than as an unordered pile of item names.
        line.label?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
                modifier = Modifier.width(88.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Bold(line.hit?.name ?: line.sourceRowId ?: "unspecified")
                line.extra?.let { Badge(it, Palette.TextMuted, caps = false) }
            }
            // Unresolved lines already show the raw key as the title, so there is no
            // second line to add.
            val subtitle = line.hit?.let { hit ->
                listOfNotNull(prettyEnum(hit.detail), hit.sourceRowId).joinToString("  ")
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MonoStyle, color = Palette.TextFaint)
            }
        }
        if (line.unresolved) {
            Badge("not in dataset", Palette.Danger)
        } else if (line.hit != null) {
            Badge("extracted", Palette.Extracted)
        }
    }
}
