package com.solisium.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.solisium.core.domain.BuildLayer
import com.solisium.core.domain.CatalogHit
import com.solisium.core.domain.CharacterAttributes
import com.solisium.core.domain.ClassSource
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.ResolvedLoadoutLine
import com.solisium.core.domain.StatKeyLabel
import com.solisium.core.domain.UserCharacter
import com.solisium.core.domain.WeaponClassMatch
import com.solisium.core.source.CharacterSheetJson
import com.solisium.desktop.AppModel
import com.solisium.desktop.FilePickers
import com.solisium.desktop.Load
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.Spacing

@Composable
fun CharacterScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Character",
            "Type 2+ letters in a slot or bag name to search the warehouse. Pick a match or keep typing.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    "Save character",
                    onClick = { model.saveCharacterDraft() },
                    primary = true,
                    enabled = !model.importing && model.characterDraft != null,
                )
                ActionButton(
                    if (model.detectedCharacter != null) "Reload JSON" else "Choose character JSON",
                    onClick = {
                        if (model.detectedCharacter != null) {
                            model.importDetectedCharacters()
                        } else {
                            FilePickers.pickFile("Select a character JSON", ".json", model.characterPickerDirectory)
                                ?.let { model.importCharacter(it) }
                        }
                    },
                    enabled = !model.importing,
                )
                ActionButton(
                    "Choose file",
                    onClick = {
                        FilePickers.pickFile("Select a character JSON", ".json", model.characterPickerDirectory)
                            ?.let { model.importCharacter(it) }
                    },
                    enabled = !model.importing,
                )
            }
        }
        when (val state = model.characters) {
            is Load.Loading -> LoadingRow("Reading characters")
            is Load.Err -> Column(Modifier.padding(horizontal = Spacing.xxl)) { ErrorState(state.message) }
            is Load.Ok -> if (state.value.isEmpty()) {
                Column(Modifier.weight(1f).padding(horizontal = Spacing.xxl)) {
                    Card {
                        Text(
                            "No character imported",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.Text,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Click Reload JSON to load %USERPROFILE%\\.solisium\\characters\\character.json, " +
                                "then type your in-game names and Save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextMuted,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        CodeLine(model.characterPickerDirectory.toString())
                    }
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth()) {
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
                            presentScore(character.combatPower)?.let { "CP $it" },
                            presentScore(character.gearScore)?.let { "GS $it" },
                            character.server?.takeIf { it.isNotBlank() },
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
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.xl)) {
        when (val state = model.characterSheet) {
            null -> EmptyState("Select a character", "Pick a character to see the resolved loadout.")
            is Load.Loading -> LoadingRow("Resolving loadout")
            is Load.Err -> ErrorState(state.message)
            is Load.Ok -> {
                val draft = model.characterDraft ?: CharacterSheetJson.fromResolved(state.value)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SheetBody(model, state.value, draft)
                }
            }
        }
    }
}

@Composable
private fun SheetBody(model: AppModel, resolved: ResolvedCharacterSheet, draft: CharacterSheetJson.Draft) {
    val sheet = resolved.sheet
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl)) {
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Field(
                        value = draft.name,
                        onValueChange = { model.updateCharacterDraft(draft.copy(name = it)) },
                        placeholder = "Character name",
                    )
                    Text(
                        "Resolved against build ${resolved.snapshotBuild ?: "none"}. " +
                            "Save writes ${model.characterPickerDirectory}\\character.json.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                }
                Badge("manual", Palette.Unverified)
            }
            Spacer(Modifier.height(Spacing.md))
            ClassEditor(model, draft, model.classSuggestion(draft))
            Spacer(Modifier.height(Spacing.md))
            Text(
                "The game does not export combat power, gear score, allocated stats, or your bag. Paste those from the character window. Combat power is not computed from Strength–Fortitude.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LabeledField("Level", draft.level, { model.updateCharacterDraft(draft.copy(level = it)) }, Modifier.weight(1f))
                LabeledField("Combat power", draft.combatPower, { model.updateCharacterDraft(draft.copy(combatPower = it)) }, Modifier.weight(1f))
                LabeledField("Gear score", draft.gearScore, { model.updateCharacterDraft(draft.copy(gearScore = it)) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.md))
            val allocated = CharacterAttributes.Points(
                strength = draft.strength.trim().replace(",", "").toLongOrNull(),
                dexterity = draft.dexterity.trim().replace(",", "").toLongOrNull(),
                wisdom = draft.wisdom.trim().replace(",", "").toLongOrNull(),
                perception = draft.perception.trim().replace(",", "").toLongOrNull(),
                fortitude = draft.fortitude.trim().replace(",", "").toLongOrNull(),
            ).allocated
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Stat points")
                Spacer(Modifier.weight(1f))
                Badge("typed sum", Palette.Unverified)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Allocated is Strength + Dexterity + Wisdom + Perception + Fortitude as typed. " +
                    "TLItemCombatPower covers gear, not these five attributes, so Combat power stays the window value.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LabeledField("Strength", draft.strength, { model.updateCharacterDraft(draft.copy(strength = it)) }, Modifier.weight(1f))
                LabeledField("Dexterity", draft.dexterity, { model.updateCharacterDraft(draft.copy(dexterity = it)) }, Modifier.weight(1f))
                LabeledField("Wisdom", draft.wisdom, { model.updateCharacterDraft(draft.copy(wisdom = it)) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LabeledField("Perception", draft.perception, { model.updateCharacterDraft(draft.copy(perception = it)) }, Modifier.weight(1f))
                LabeledField("Fortitude", draft.fortitude, { model.updateCharacterDraft(draft.copy(fortitude = it)) }, Modifier.weight(1f))
                LabeledValue("Allocated", allocated?.toString() ?: "—", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        SectionLabel("Equipped · ${draft.weapons.size + draft.equipment.size} slots")
        Spacer(Modifier.height(Spacing.sm))
        Card(Modifier.fillMaxWidth()) {
            val equipped = draft.weapons + draft.equipment
            equipped.forEachIndexed { index, slot ->
                if (index > 0) Divider(Modifier.padding(vertical = Spacing.sm))
                val line = resolved.lines.firstOrNull { it.kind != "inventory" && it.label == slot.slot }
                SlotEditor(model, draft, slot, line, isWeapon = index < draft.weapons.size)
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        SkillsAndLayers(model, draft, resolved)

        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Inventory")
            Spacer(Modifier.weight(1f))
            Text(
                "Add item",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Accent,
                modifier = Modifier.clickable {
                    model.updateCharacterDraft(draft.copy(inventory = draft.inventory + CharacterSheetJson.NamedStack("", "1")))
                },
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Card(Modifier.fillMaxWidth()) {
            draft.inventory.forEachIndexed { index, stack ->
                if (index > 0) Divider(Modifier.padding(vertical = Spacing.sm))
                val line = resolved.lines.filter { it.kind == "inventory" }.getOrNull(index)
                InventoryEditor(model, draft, index, stack, line)
            }
        }

        model.lastImport?.takeIf { it.label == "Character" }?.let { outcome ->
            Spacer(Modifier.height(Spacing.md))
            Text(
                if (outcome.error != null) "Save/import failed: ${outcome.error}"
                else "Saved ${outcome.imported} row(s) to the local database.",
                style = MaterialTheme.typography.bodySmall,
                color = if (outcome.error != null) Palette.Danger else Palette.Extracted,
            )
        }
        if (resolved.unresolvedCount > 0) {
            Spacer(Modifier.height(Spacing.md))
            WarningBanner(
                message = "${resolved.unresolvedCount} equipped or bag entries are not in this dataset.",
                label = "unresolved",
                detail = "Check the spelling against the in-game name. Names are never guessed.",
            )
        }

        resolved.lines.filter { line ->
            line.kind !in setOf("weapon", "equipment", "inventory", "skill", "weapon_mastery") &&
                BuildLayer.fromId(line.kind) == null &&
                !line.kind.startsWith("prefix:")
        }
            .groupBy { it.kind }
            .forEach { (kind, lines) ->
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel("${prettyEnum(kind) ?: kind} · ${lines.size}")
                Spacer(Modifier.height(Spacing.sm))
                Card(Modifier.fillMaxWidth()) {
                    lines.forEachIndexed { index, line ->
                        if (index > 0) Divider(Modifier.padding(vertical = Spacing.sm))
                        LoadoutLine(line)
                    }
                }
            }

        if (sheet.currency.isNotEmpty() || sheet.goals.isNotEmpty() || sheet.builds.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            SectionLabel("Other")
            Spacer(Modifier.height(Spacing.sm))
            Card(Modifier.fillMaxWidth()) {
                sheet.currency.forEach { KeyValueRow(it.currency, it.amount.format()) }
                sheet.goals.forEach { KeyValueRow(it.goalType, it.label) }
                sheet.builds.forEach { KeyValueRow("Build", it.name) }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun SkillsAndLayers(
    model: AppModel,
    draft: CharacterSheetJson.Draft,
    resolved: ResolvedCharacterSheet,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Weapon mastery levels")
        Spacer(Modifier.weight(1f))
        Text(
            "Add weapon",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Accent,
            modifier = Modifier.clickable {
                model.updateCharacterDraft(
                    draft.copy(weaponMastery = draft.weaponMastery + CharacterSheetJson.NamedMastery("", "")),
                )
            },
        )
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(
        "The 167 / 151 numbers from the skills screen. These are typed, not WM_ catalog nodes, and not a CP formula.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextFaint,
    )
    Spacer(Modifier.height(Spacing.sm))
    Card(Modifier.fillMaxWidth()) {
        draft.weaponMastery.forEachIndexed { index, row ->
            if (index > 0) Divider(Modifier.padding(vertical = Spacing.sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Field(
                        value = row.weapon,
                        onValueChange = { value ->
                            model.updateCharacterDraft(
                                draft.copy(
                                    weaponMastery = draft.weaponMastery.mapIndexed { i, item ->
                                        if (i == index) item.copy(weapon = value) else item
                                    },
                                ),
                            )
                        },
                        placeholder = "Greatsword or kSword2h",
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Box(Modifier.width(72.dp)) {
                    Field(
                        value = row.level,
                        onValueChange = { value ->
                            model.updateCharacterDraft(
                                draft.copy(
                                    weaponMastery = draft.weaponMastery.mapIndexed { i, item ->
                                        if (i == index) item.copy(level = value) else item
                                    },
                                ),
                            )
                        },
                        placeholder = "Lv",
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(Spacing.lg))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Weapon skills")
        Spacer(Modifier.weight(1f))
        Text(
            "Add skill",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Accent,
            modifier = Modifier.clickable {
                model.updateCharacterDraft(
                    draft.copy(skills = draft.skills + CharacterSheetJson.NamedSkill("", "PvE Grind", "", "weapon_skill")),
                )
            },
        )
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(
        "Active, passive, and defense skills on the two equipped weapons. Presence only.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextFaint,
    )
    Spacer(Modifier.height(Spacing.sm))
    Card(Modifier.fillMaxWidth()) {
        draft.skills.forEachIndexed { index, skill ->
            if (index > 0) Divider(Modifier.padding(vertical = Spacing.sm))
            val line = resolved.lines.filter { it.kind == "skill" }.getOrNull(index)
            SkillRow(model, draft, index, skill, line)
        }
    }

    BuildLayer.entries.filter { it != BuildLayer.WeaponSkill }.forEach { layer ->
        val indices = draft.buildLayers.mapIndexedNotNull { index, row ->
            if (row.layer == layer.id) index else null
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(layer.label)
            Spacer(Modifier.weight(1f))
            Text(
                "Add",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Accent,
                modifier = Modifier.clickable {
                    model.updateCharacterDraft(
                        draft.copy(buildLayers = draft.buildLayers + CharacterSheetJson.NamedLayer(layer.id, "${indices.size + 1}", "", "")),
                    )
                },
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(layer.blurb, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        Spacer(Modifier.height(Spacing.sm))
        Card(Modifier.fillMaxWidth()) {
            if (indices.isEmpty()) {
                Text("None typed", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            } else {
                indices.forEachIndexed { visible, index ->
                    if (visible > 0) Divider(Modifier.padding(vertical = Spacing.sm))
                    val row = draft.buildLayers[index]
                    val line = resolved.lines.filter { it.kind == layer.id }.getOrNull(visible)
                    LayerRow(model, draft, index, layer.id, row, line)
                }
            }
        }
    }

    model.discoveredInfluences.forEach { inf ->
        val indices = draft.buildLayers.mapIndexedNotNull { index, row ->
            if (row.layer == inf.id || row.layer.equals(inf.prefix, ignoreCase = true)) index else null
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(inf.label)
            Spacer(Modifier.weight(1f))
            if (inf.newThisPatch) Badge("new this patch", Palette.Derived)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "Add",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Accent,
                modifier = Modifier.clickable {
                    model.updateCharacterDraft(
                        draft.copy(
                            buildLayers = draft.buildLayers + CharacterSheetJson.NamedLayer(
                                inf.id,
                                "${indices.size + 1}",
                                "",
                                "",
                            ),
                        ),
                    )
                },
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(inf.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        Spacer(Modifier.height(Spacing.sm))
        Card(Modifier.fillMaxWidth()) {
            if (indices.isEmpty()) {
                Text("None typed", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            } else {
                indices.forEachIndexed { visible, index ->
                    if (visible > 0) Divider(Modifier.padding(vertical = Spacing.sm))
                    val row = draft.buildLayers[index]
                    val line = resolved.lines.filter { it.kind == inf.id }.getOrNull(visible)
                    LayerRow(model, draft, index, inf.id, row, line)
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    model: AppModel,
    draft: CharacterSheetJson.Draft,
    index: Int,
    skill: CharacterSheetJson.NamedSkill,
    line: ResolvedLoadoutLine?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SuggestField(
                model = model,
                fieldId = "sk:$index",
                slot = null,
                layer = BuildLayer.WeaponSkill.id,
                value = skill.name,
                onValueChange = { value ->
                    model.updateCharacterDraft(
                        draft.copy(
                            skills = draft.skills.mapIndexed { i, row -> if (i == index) row.copy(name = value) else row },
                        ),
                    )
                },
                placeholder = "In-game skill name",
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(Modifier.width(96.dp)) {
            Field(
                value = skill.loadout,
                onValueChange = { value ->
                    model.updateCharacterDraft(
                        draft.copy(
                            skills = draft.skills.mapIndexed { i, row -> if (i == index) row.copy(loadout = value) else row },
                        ),
                    )
                },
                placeholder = "Loadout",
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(Modifier.width(48.dp)) {
            Field(
                value = skill.level,
                onValueChange = { value ->
                    model.updateCharacterDraft(
                        draft.copy(
                            skills = draft.skills.mapIndexed { i, row -> if (i == index) row.copy(level = value) else row },
                        ),
                    )
                },
                placeholder = "Lv",
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        when {
            skill.name.isBlank() -> Badge("empty", Palette.TextFaint)
            line?.unresolved == true -> Badge("not in dataset", Palette.Danger)
            line?.hit != null -> Badge("extracted", Palette.Extracted)
            else -> Badge("typed", Palette.Unverified)
        }
    }
}

@Composable
private fun LayerRow(
    model: AppModel,
    draft: CharacterSheetJson.Draft,
    index: Int,
    layerId: String,
    row: CharacterSheetJson.NamedLayer,
    line: ResolvedLoadoutLine?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            SuggestField(
                model = model,
                fieldId = "ly:$layerId:$index",
                slot = null,
                layer = layerId,
                value = row.name,
                onValueChange = { value ->
                    model.updateCharacterDraft(
                        draft.copy(
                            buildLayers = draft.buildLayers.mapIndexed { i, item ->
                                if (i == index) item.copy(name = value) else item
                            },
                        ),
                    )
                },
                placeholder = "In-game name",
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(Modifier.width(48.dp)) {
            Field(
                value = row.level,
                onValueChange = { value ->
                    model.updateCharacterDraft(
                        draft.copy(
                            buildLayers = draft.buildLayers.mapIndexed { i, item ->
                                if (i == index) item.copy(level = value) else item
                            },
                        ),
                    )
                },
                placeholder = "Lv",
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        when {
            row.name.isBlank() -> Badge("empty", Palette.TextFaint)
            line?.unresolved == true -> Badge("not in dataset", Palette.Danger)
            line?.hit != null -> Badge("extracted", Palette.Extracted)
            else -> Badge("typed", Palette.Unverified)
        }
    }
}

@Composable
private fun SlotEditor(
    model: AppModel,
    draft: CharacterSheetJson.Draft,
    slot: CharacterSheetJson.NamedSlot,
    line: ResolvedLoadoutLine?,
    isWeapon: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            slot.slot.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
            modifier = Modifier.width(88.dp),
        )
        Column(Modifier.weight(1f)) {
            SuggestField(
                model = model,
                fieldId = if (isWeapon) "w:${slot.slot}" else "e:${slot.slot}",
                slot = slot.slot,
                value = slot.name,
                onValueChange = { value ->
                    model.updateCharacterDraft(
                        if (isWeapon) {
                            draft.copy(weapons = draft.weapons.map { if (it.slot == slot.slot) it.copy(name = value) else it })
                        } else {
                            draft.copy(equipment = draft.equipment.map { if (it.slot == slot.slot) it.copy(name = value) else it })
                        },
                    )
                },
                placeholder = "In-game item name",
            )
            if (line != null && line.stats.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    line.stats.joinToString(" · ") {
                        "${StatKeyLabel.of(it.statKey, null)} ${it.rawValue.format()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
        }
        when {
            slot.name.isBlank() -> Badge("empty", Palette.TextFaint)
            line?.unresolved == true -> Badge("not in dataset", Palette.Danger)
            line?.hit != null -> Badge("extracted", Palette.Extracted)
            else -> Badge("typed", Palette.Unverified)
        }
    }
}

@Composable
private fun InventoryEditor(
    model: AppModel,
    draft: CharacterSheetJson.Draft,
    index: Int,
    stack: CharacterSheetJson.NamedStack,
    line: ResolvedLoadoutLine?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Bag",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
            modifier = Modifier.width(88.dp),
        )
        Box(Modifier.weight(1f)) {
            SuggestField(
                model = model,
                fieldId = "i:$index",
                slot = null,
                value = stack.name,
                onValueChange = { value ->
                    model.updateCharacterDraft(
                        draft.copy(inventory = draft.inventory.mapIndexed { i, row -> if (i == index) row.copy(name = value) else row }),
                    )
                },
                placeholder = "Unequipped item name",
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(Modifier.width(56.dp)) {
            Field(
                value = stack.quantity,
                onValueChange = { value ->
                    model.updateCharacterDraft(
                        draft.copy(inventory = draft.inventory.mapIndexed { i, row -> if (i == index) row.copy(quantity = value) else row }),
                    )
                },
                placeholder = "Qty",
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        when {
            stack.name.isBlank() -> Badge("empty", Palette.TextFaint)
            line?.unresolved == true -> Badge("not in dataset", Palette.Danger)
            line?.hit != null -> Badge("extracted", Palette.Extracted)
            else -> Badge("typed", Palette.Unverified)
        }
    }
}

@Composable
private fun ClassEditor(
    model: AppModel,
    draft: CharacterSheetJson.Draft,
    suggestion: WeaponClassMatch,
) {
    val names = model.knownClassNames()
    val hits = if (draft.className.trim().length >= 2) {
        names.filter { it.contains(draft.className.trim(), ignoreCase = true) }.take(12)
    } else {
        emptyList()
    }
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    val showMenu = menuOpen && hits.isNotEmpty() &&
        hits.none { it.equals(draft.className.trim(), ignoreCase = true) }
    val badgeColor = when (draft.classSource) {
        ClassSource.EXTRACTED -> Palette.Extracted
        else -> Palette.Unverified
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.weight(1f)) {
            Text("Class", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().onGloballyPositioned { size = it.size }) {
                Field(
                    value = draft.className,
                    onValueChange = { value ->
                        menuOpen = true
                        model.updateCharacterDraft(draft.copy(className = value), classEdited = true)
                    },
                    placeholder = suggestion.weaponsLabel ?: "Gladiator",
                    modifier = Modifier
                        .onFocusChanged { state -> menuOpen = state.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                false
                            } else when (event.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    val pick = hits.firstOrNull()
                                    if (pick != null) {
                                        menuOpen = false
                                        model.updateCharacterDraft(draft.copy(className = pick), classEdited = true)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.Escape -> {
                                    menuOpen = false
                                    true
                                }
                                else -> false
                            }
                        },
                )
                if (showMenu) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(0, size.height),
                        onDismissRequest = { menuOpen = false },
                        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
                    ) {
                        Column(
                            Modifier
                                .width(with(density) { size.width.toDp() })
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Palette.SurfaceHigh)
                                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(10.dp))
                                .verticalScroll(rememberScrollState()),
                        ) {
                            hits.forEach { name ->
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.Text,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            menuOpen = false
                                            model.updateCharacterDraft(draft.copy(className = name), classEdited = true)
                                        }
                                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val hint = when {
                ClassSource.isManual(draft.classSource) && suggestion.name != null ->
                    "Suggested from weapons: ${suggestion.name} (${suggestion.source})."
                suggestion.pairResolved && suggestion.name == null ->
                    "${suggestion.weaponsLabel} has no extracted or community class name yet."
                suggestion.weaponsLabel != null ->
                    "From equipped weapons: ${suggestion.weaponsLabel}."
                else ->
                    "Auto-fills from main + offhand when both resolve. New classes come from extract or community."
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (draft.className.isNotBlank() && draft.classSource.isNotBlank()) {
                Badge(
                    ClassSource.badge(draft.classSource),
                    badgeColor,
                    caps = !ClassSource.isManual(draft.classSource),
                )
            }
            if (ClassSource.isManual(draft.classSource) && suggestion.name != null &&
                !suggestion.name.equals(draft.className.trim(), ignoreCase = true)
            ) {
                Spacer(Modifier.height(Spacing.xs))
                ActionButton("Use suggestion", onClick = { model.useSuggestedClass() })
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        Spacer(Modifier.height(4.dp))
        Field(value, onValueChange, placeholder = "—")
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        }
    }
}

@Composable
private fun SuggestField(
    model: AppModel,
    fieldId: String,
    slot: String?,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    layer: String? = null,
) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val active = model.characterSuggestField == fieldId
    val hits = if (active) model.characterSuggestions else emptyList()
    val showMenu = active && value.trim().length >= 2 &&
        (hits.isNotEmpty() || model.characterSuggestionsReady)
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { size = it.size },
    ) {
        Field(
            value = value,
            onValueChange = { next ->
                onValueChange(next)
                model.onGearQuery(fieldId, next, slot, layer)
            },
            placeholder = placeholder,
            modifier = Modifier
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        model.onGearQuery(fieldId, value, slot, layer)
                    } else {
                        model.onGearFieldBlur(fieldId)
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            val pick = hits.firstOrNull()?.name
                            if (pick != null) {
                                onValueChange(pick)
                                model.clearGearSuggestions()
                                true
                            } else {
                                false
                            }
                        }
                        Key.Escape -> {
                            if (active) {
                                model.clearGearSuggestions()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                },
        )
        if (showMenu) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, size.height),
                onDismissRequest = { model.clearGearSuggestions() },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
            ) {
                Column(
                    Modifier
                        .width(with(density) { size.width.toDp() })
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.SurfaceHigh)
                        .border(1.dp, Palette.BorderStrong, RoundedCornerShape(10.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (hits.isEmpty()) {
                        Text(
                            "No catalog matches",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextFaint,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        )
                    } else {
                        hits.forEach { hit ->
                            SuggestionRow(hit) {
                                val name = hit.name ?: return@SuggestionRow
                                onValueChange(name)
                                model.clearGearSuggestions()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(hit: CatalogHit, onPick: () -> Unit) {
    HoverRow(selected = false, onClick = onPick, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) {
        Column(Modifier.weight(1f).padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            Text(
                hit.name ?: hit.sourceRowId,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
            )
            val meta = listOfNotNull(prettyEnum(hit.detail), hit.kind.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
            cursorBrush = SolidColor(Palette.Accent),
            modifier = Modifier.fillMaxWidth().then(modifier),
        )
    }
}

private fun presentScore(value: Long?): String? = value?.takeIf { it > 0L }?.format()

@Composable
private fun LoadoutLine(line: ResolvedLoadoutLine) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            line.label?.replaceFirstChar { it.uppercase() } ?: line.kind.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
            modifier = Modifier.width(88.dp),
        )
        Column(Modifier.weight(1f)) {
            val title = DisplayName.of(line.hit?.name, line.hit?.sourceRowId)
                ?: line.name
                ?: line.sourceRowId
            if (line.empty || title == null) {
                Text("Empty", style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
            } else {
                Bold(title)
            }
        }
        when {
            line.empty -> Badge("empty", Palette.TextFaint)
            line.unresolved -> Badge("not in dataset", Palette.Danger)
            line.hit != null -> Badge("extracted", Palette.Extracted)
        }
    }
}
