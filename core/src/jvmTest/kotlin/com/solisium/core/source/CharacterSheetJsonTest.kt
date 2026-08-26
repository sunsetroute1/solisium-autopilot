package com.solisium.core.source

import com.solisium.core.db.JvmDatabase
import com.solisium.core.domain.CharacterSlots
import com.solisium.core.query.CatalogQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterSheetJsonTest {
    @Test
    fun writtenNamesRoundTripThroughManualImport() {
        val draft = CharacterSheetJson.Draft(
            id = "sheet-hero",
            name = "Sheet Hero",
            level = "55",
            combatPower = "7128",
            gearScore = "6400",
            server = "west",
            weapons = CharacterSlots.weapons.map { slot ->
                CharacterSheetJson.NamedSlot(slot, if (slot == "main") "Fixture Longbow" else "")
            },
            equipment = (CharacterSlots.body + CharacterSlots.accessories).map { slot ->
                CharacterSheetJson.NamedSlot(slot, if (slot == "head") "Frost Helm" else "")
            },
            strength = "30",
            dexterity = "12",
            wisdom = "9",
            perception = "5",
            fortitude = "3",
            className = "Gladiator",
            classSource = "community",
            inventory = listOf(
                CharacterSheetJson.NamedStack("Bag Bow", "2"),
                CharacterSheetJson.NamedStack("", "9"),
            ),
        )
        val json = CharacterSheetJson.write(draft)
        val parsed = ManualImportDataSource.parseDocument(json)
        assertEquals("sheet-hero", parsed.id)
        assertEquals("Sheet Hero", parsed.name)
        assertEquals(55L, parsed.level)
        assertEquals(7128L, parsed.combatPower)
        assertEquals(6400L, parsed.gearScore)
        assertEquals(30L, parsed.strength)
        assertEquals(12L, parsed.dexterity)
        assertEquals(9L, parsed.wisdom)
        assertEquals(5L, parsed.perception)
        assertEquals(3L, parsed.fortitude)
        assertEquals("Gladiator", parsed.className)
        assertEquals("community", parsed.classSource)
        assertEquals("Fixture Longbow", parsed.weapons.single { it.slot == "main" }.name)
        assertEquals("Frost Helm", parsed.equipment.single { it.slot == "head" }.name)
        assertEquals("Bag Bow", parsed.inventory.single().name)
        assertEquals(2L, parsed.inventory.single().quantity)

        val db = JvmDatabase.inMemory()
        ManualImportDataSource().importInto(db, ImportRequest(content = json))
        val sheet = CatalogQuery(db).characterSheet("sheet-hero")!!
        assertEquals(7128L, sheet.character.combatPower)
        assertEquals(6400L, sheet.character.gearScore)
        assertEquals(30L, sheet.character.strength)
        assertEquals(59L, sheet.character.statPoints.allocated)
        assertEquals("Gladiator", sheet.character.className)
        assertEquals("community", sheet.character.classSource)
        assertEquals("Fixture Longbow", sheet.weapons.single { it.slot == "main" }.name)
        assertEquals("Frost Helm", sheet.equipment.single { it.slot == "head" }.name)
        assertEquals(CharacterSlots.body.size + CharacterSlots.accessories.size, sheet.equipment.size)
        assertEquals(CharacterSlots.weapons.size, sheet.weapons.size)
        assertEquals("Bag Bow", sheet.inventory.single().name)
        val resolved = CatalogQuery(db).resolveCharacter("sheet-hero", null)!!
        assertTrue(resolved.lines.any { it.kind == "weapon" && it.label == "offhand" && it.empty })
        assertEquals(1, resolved.lines.count { it.kind == "inventory" })
    }

    @Test
    fun masteryAndNamedLayersRoundTrip() {
        val draft = CharacterSheetJson.Draft(
            id = "layer-hero",
            name = "Layer Hero",
            level = "55",
            combatPower = "8297",
            gearScore = "8000",
            server = "west",
            weapons = CharacterSlots.weapons.map { CharacterSheetJson.NamedSlot(it, "") },
            equipment = (CharacterSlots.body + CharacterSlots.accessories).map { CharacterSheetJson.NamedSlot(it, "") },
            strength = "106",
            dexterity = "66",
            wisdom = "21",
            perception = "50",
            fortitude = "41",
            inventory = emptyList(),
            skills = listOf(CharacterSheetJson.NamedSkill("Gauntlet Slam", "PvE Grind", "5", "weapon_skill")),
            weaponMastery = listOf(
                CharacterSheetJson.NamedMastery("kSword2h", "167"),
                CharacterSheetJson.NamedMastery("spear", "151"),
            ),
            buildLayers = listOf(
                CharacterSheetJson.NamedLayer("skill_core", "1", "Talus's Transcendent Barrier", ""),
                CharacterSheetJson.NamedLayer("gemstone", "1", "Gemstone Attack", ""),
            ),
        )
        val json = CharacterSheetJson.write(draft)
        val parsed = ManualImportDataSource.parseDocument(json)
        assertEquals(167L, parsed.weaponMastery.single { it.weapon == "kSword2h" }.level)
        assertEquals(151L, parsed.weaponMastery.single { it.weapon == "spear" }.level)
        assertEquals("Gauntlet Slam", parsed.skills.single().name)
        assertEquals(5L, parsed.skills.single().skillLevel)
        assertEquals("skill_core", parsed.buildLayers.single { it.name?.contains("Talus") == true }.layer)

        val db = JvmDatabase.inMemory()
        ManualImportDataSource().importInto(db, ImportRequest(content = json))
        val sheet = CatalogQuery(db).characterSheet("layer-hero")!!
        assertEquals(167L, sheet.weaponMastery.single { it.weapon == "kSword2h" }.level)
        assertEquals("Gauntlet Slam", sheet.skills.single().name)
        assertEquals("skill_core", sheet.buildLayers.single { it.name?.contains("Talus") == true }.layer)
    }

    @Test
    fun nameOnlySkillsAreKept() {
        val json = """
            {
              "schema": "solisium.manual-character",
              "schemaVersion": 1,
              "character": { "id": "named-skill", "name": "Named" },
              "skills": [ { "name": "Gauntlet Slam", "level": 5, "loadout": "PvE Grind" } ]
            }
        """.trimIndent()
        val parsed = ManualImportDataSource.parseDocument(json)
        assertEquals(0, parsed.skipped)
        assertEquals("Gauntlet Slam", parsed.skills.single().name)
        assertEquals(5L, parsed.skills.single().skillLevel)
    }
}
