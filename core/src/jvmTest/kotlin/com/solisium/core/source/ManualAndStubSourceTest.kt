package com.solisium.core.source

import com.solisium.core.db.JvmDatabase
import com.solisium.core.query.CatalogQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualAndStubSourceTest {
    @Test
    fun manualCharacterJsonRoundTrip() {
        val json = javaClass.getResource("/manual-character.json")!!.readText()
        val db = JvmDatabase.inMemory()
        val receipt = ManualImportDataSource().importInto(db, ImportRequest(content = json))
        assertTrue(receipt.recordsImported > 1)
        assertTrue(receipt.warnings.any { it.contains("no verified local file source") })
        val sheet = CatalogQuery(db).characterSheet("fixture-character")!!
        assertEquals("Fixture Hero", sheet.character.name)
        assertEquals(60L, sheet.character.level)
        assertEquals(7128L, sheet.character.combatPower)
        assertEquals(6400L, sheet.character.gearScore)
        assertEquals("head", sheet.equipment.single().slot)
        assertEquals(9L, sheet.equipment.single().itemLevel)
        assertEquals("main", sheet.weapons.single().slot)
        assertEquals(4L, sheet.traits.single().rank)
        assertEquals("weapon_1", sheet.runes.single().slot)
        assertEquals("pve", sheet.skills.single().loadout)
        assertEquals(12L, sheet.inventory.single().quantity)
        assertEquals(40L, sheet.materials.single().quantity)
        assertEquals(1500L, sheet.currency.first { it.currency == "lucent" }.amount)
        assertEquals(18L, sheet.cookingLevel)
        assertEquals("upgrade", sheet.goals.single().goalType)
        assertEquals("PVE", sheet.builds.single().name)
    }

    @Test
    fun reimportReplacesLoadoutRows() {
        val first = javaClass.getResource("/manual-character.json")!!.readText()
        val db = JvmDatabase.inMemory()
        ManualImportDataSource().importInto(db, ImportRequest(content = first))
        val replacement = """
            {
              "schema": "solisium.manual-character",
              "schemaVersion": 1,
              "character": {
                "id": "fixture-character",
                "name": "Fixture Hero Reloaded",
                "level": 61,
                "updated_at": "2026-08-24T12:00:00Z"
              },
              "weapons": [
                {
                  "slot": "main",
                  "source_table": "TLItemLooks_Equip",
                  "source_row_id": "new_sword",
                  "item_level": 10
                }
              ]
            }
        """.trimIndent()
        val receipt = ManualImportDataSource().importInto(db, ImportRequest(content = replacement))
        assertEquals(0, receipt.recordsSkipped)
        val sheet = CatalogQuery(db).characterSheet("fixture-character")!!
        assertEquals("Fixture Hero Reloaded", sheet.character.name)
        assertEquals(61L, sheet.character.level)
        assertEquals("2026-08-24T00:00:00Z", db.schemaQueries.selectCharacter("fixture-character").executeAsOne().created_at)
        assertEquals("new_sword", sheet.weapons.single().sourceRowId)
        assertEquals(10L, sheet.weapons.single().itemLevel)
        assertTrue(sheet.equipment.isEmpty())
        assertTrue(sheet.inventory.isEmpty())
        assertTrue(sheet.currency.isEmpty())
        assertEquals(null, sheet.cookingLevel)
        assertEquals(1, CatalogQuery(db).characters().size)
    }

    @Test
    fun skipsTraitNamesWithoutWarehouseKeys() {
        val json = """
            {
              "schema": "solisium.manual-character",
              "character": { "id": "namer", "name": "Namer" },
              "traits": [ { "name": "Attack Speed", "rank": 1 } ]
            }
        """.trimIndent()
        val db = JvmDatabase.inMemory()
        val receipt = ManualImportDataSource().importInto(db, ImportRequest(content = json))
        assertEquals(1, receipt.recordsSkipped)
        assertTrue(receipt.warnings.any { it.contains("not a warehouse key") })
        val sheet = CatalogQuery(db).characterSheet("namer")!!
        assertTrue(sheet.traits.isEmpty())
    }

    @Test
    fun publicRepositorySourceDoesNotScrape() {
        val capability = PublicRepositoryDataSource().probe()
        assertFalse(capability.available)
        val receipt = PublicRepositoryDataSource().importInto(
            JvmDatabase.inMemory(),
            ImportRequest(),
        )
        assertEquals(0, receipt.recordsImported)
        assertTrue(receipt.warnings.isNotEmpty())
    }

    @Test
    fun parseSteamBuildIdFromAppmanifest() {
        val acf = """
            "AppState"
            {
            	"appid"		"2429640"
            	"buildid"		"24118850"
            }
        """.trimIndent()
        assertEquals("24118850", InstalledGameDataSource.parseBuildId(acf))
    }
}
