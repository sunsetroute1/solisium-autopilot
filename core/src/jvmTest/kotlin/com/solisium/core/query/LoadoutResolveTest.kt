package com.solisium.core.query

import com.solisium.core.db.JvmDatabase
import com.solisium.core.domain.CharacterSlots
import com.solisium.core.source.ImportRequest
import com.solisium.core.source.ManualImportDataSource
import com.solisium.core.source.TLHelperDataSource
import com.solisium.core.testutil.WarehouseFixtures
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadoutResolveTest {
    @Test
    fun resolvesKnownKeysAndLeavesUnknownKeysUnresolved() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            val json = """
                {
                  "schema": "solisium.manual-character",
                  "schemaVersion": 1,
                  "character": { "id": "resolver", "name": "Resolver" },
                  "weapons": [
                    {
                      "slot": "main",
                      "source_table": "TLItemLooks_Equip",
                      "source_row_id": "fixture_bow",
                      "item_level": 9
                    }
                  ],
                  "runes": [
                    {
                      "slot": "weapon_1",
                      "source_table": "TLRuneInfo",
                      "source_row_id": "fixture_rune"
                    }
                  ],
                  "skills": [
                    {
                      "source_table": "TLSkill",
                      "source_row_id": "fixture_skill",
                      "loadout": "pve"
                    }
                  ],
                  "equipment": [
                    {
                      "slot": "head",
                      "source_table": "TLItemLooks_Equip",
                      "source_row_id": "missing_helm"
                    }
                  ]
                }
            """.trimIndent()
            ManualImportDataSource().importInto(db, ImportRequest(content = json))
            val query = CatalogQuery(db)
            assertEquals("Fixture Longbow", query.lookup(snapshotId, "TLItemLooks_Equip", "fixture_bow")?.name)
            assertNull(query.lookup(snapshotId, "TLItemLooks_Equip", "missing_helm"))

            val resolved = query.resolveCharacter("resolver", snapshotId)!!
            assertEquals(1, resolved.unresolvedCount)
            val weapon = resolved.lines.single { it.kind == "weapon" && it.label == "main" }
            assertEquals("Fixture Longbow", weapon.hit?.name)
            assertEquals("Epic", weapon.hit?.detail)
            val missing = resolved.lines.single { it.kind == "equipment" && it.label == "head" }
            assertTrue(missing.unresolved)
            assertNull(missing.hit)
            assertTrue(resolved.lines.any { it.kind == "equipment" && it.label == "chest" && it.empty })
            assertEquals(CharacterSlots.weapons.size, resolved.lines.count { it.kind == "weapon" })
            assertEquals(
                CharacterSlots.body.size + CharacterSlots.accessories.size,
                resolved.lines.count { it.kind == "equipment" },
            )
            assertEquals("Fixture Attack Rune", resolved.lines.single { it.kind == "rune" }.hit?.name)
            assertEquals("Fixture Skill", resolved.lines.single { it.kind == "skill" }.hit?.name)
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun doesNotInventNamesWithoutASnapshot() {
        val db = JvmDatabase.inMemory()
        ManualImportDataSource().importInto(
            db,
            ImportRequest(
                content = """
                    {
                      "schema": "solisium.manual-character",
                      "character": { "id": "bare", "name": "Bare" },
                      "weapons": [
                        { "slot": "main", "source_table": "TLItemLooks_Equip", "source_row_id": "fixture_bow" }
                      ]
                    }
                """.trimIndent(),
            ),
        )
            val resolved = CatalogQuery(db).resolveCharacter("bare", null)!!
            assertNull(resolved.snapshotId)
            val weapon = resolved.lines.single { it.kind == "weapon" && it.label == "main" }
            assertNull(weapon.hit)
            assertTrue(weapon.unresolved)
    }

    @Test
    fun inGameNameFillsASlotFromTheWarehouse() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            ManualImportDataSource().importInto(
                db,
                ImportRequest(
                    content = """
                        {
                          "schema": "solisium.manual-character",
                          "character": {
                            "id": "named",
                            "name": "Named",
                            "combat_power": 7128,
                            "gear_score": 6400
                          },
                          "weapons": [
                            { "slot": "main", "name": "Fixture Longbow" }
                          ],
                          "inventory": [
                            { "name": "Fixture Longbow", "quantity": 2 }
                          ]
                        }
                    """.trimIndent(),
                ),
            )
            val sheet = CatalogQuery(db).characterSheet("named")!!
            assertEquals(7128L, sheet.character.combatPower)
            assertEquals(6400L, sheet.character.gearScore)
            val resolved = CatalogQuery(db).resolveCharacter("named", snapshotId)!!
            val weapon = resolved.lines.single { it.kind == "weapon" && it.label == "main" }
            assertEquals("Fixture Longbow", weapon.hit?.name)
            assertTrue(weapon.stats.isNotEmpty())
            val bag = resolved.lines.single { it.kind == "inventory" }
            assertEquals("Fixture Longbow", bag.hit?.name)
            assertEquals("qty=2", bag.extra)
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }
}

class GearSuggestTest {
    @Test
    fun typeaheadFindsNamedGearAndIgnoresShortQueries() {
        val warehouse = WarehouseFixtures.withCalanthiaGear(WarehouseFixtures.writeMiniWarehouse())
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            val query = CatalogQuery(db)
            assertTrue(query.suggestGear(snapshotId, "L", "main").isEmpty())
            assertTrue(query.suggestGear(snapshotId, "x", "main").isEmpty())
            val main = query.suggestGear(snapshotId, "Long", "main")
            assertTrue(main.any { it.name == "Fixture Longbow" })
            assertTrue(query.suggestGear(snapshotId, "Long", "head").none { it.name == "Fixture Longbow" })
            val bag = query.suggestGear(snapshotId, "Fixt", null)
            assertTrue(bag.any { it.name == "Fixture Longbow" })
            val misspelled = query.suggestGear(snapshotId, "Calenthia", "head")
            assertTrue(misspelled.any { it.name == "Calanthia's Visage" })
            assertTrue(misspelled.none { it.name?.contains("Chest", ignoreCase = true) == true })
            val calanthiaBag = query.suggestGear(snapshotId, "Calanthia", null)
            val visageAt = calanthiaBag.indexOfFirst { it.name == "Calanthia's Visage" }
            val chestAt = calanthiaBag.indexOfFirst { it.name?.contains("Chest", ignoreCase = true) == true }
            assertTrue(visageAt >= 0)
            assertTrue(chestAt < 0 || visageAt < chestAt)
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }
}

class BuildMismatchTest {
    @Test
    fun warnsOnlyWhenBothBuildsAreKnownAndDifferent() {
        assertEquals(
            "installed Steam build 24829515 does not match snapshot build 24118850",
            BuildMismatch.warning("24829515", "24118850"),
        )
        assertNull(BuildMismatch.warning("24829515", "24829515"))
        assertNull(BuildMismatch.warning("24829515", "unknown"))
        assertNull(BuildMismatch.warning("24829515", null))
        assertNull(BuildMismatch.warning(null, "24118850"))
    }
}
