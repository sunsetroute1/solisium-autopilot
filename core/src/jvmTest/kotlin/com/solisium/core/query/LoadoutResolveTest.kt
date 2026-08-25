package com.solisium.core.query

import com.solisium.core.db.JvmDatabase
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
            val weapon = resolved.lines.single { it.kind == "weapon" }
            assertEquals("Fixture Longbow", weapon.hit?.name)
            assertEquals("Epic", weapon.hit?.detail)
            val missing = resolved.lines.single { it.kind == "equipment" }
            assertTrue(missing.unresolved)
            assertNull(missing.hit)
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
        assertNull(resolved.lines.single().hit)
        assertTrue(resolved.lines.single().unresolved)
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
