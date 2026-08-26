package com.solisium.core.query

import com.solisium.core.db.JvmDatabase
import com.solisium.core.source.ImportRequest
import com.solisium.core.source.ManualImportDataSource
import com.solisium.core.source.TLHelperDataSource
import com.solisium.core.testutil.WarehouseFixtures
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModeledCombatPowerTest {
    @Test
    fun warehouseMappedItemUsesExtractedWeightsAndPotential() {
        val warehouse = WarehouseFixtures.withCombatPower(WarehouseFixtures.writeMiniWarehouse())
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
                          "schemaVersion": 1,
                          "character": { "id": "gladiator", "name": "Gladiator" },
                          "weapons": [
                            {
                              "slot": "main",
                              "source_table": "TLItemLooks_Equip",
                              "source_row_id": "bow_aa_t2_fixture",
                              "item_level": 1
                            },
                            {
                              "slot": "offhand",
                              "source_table": "TLItemLooks_Equip",
                              "source_row_id": "sword_a_t1_fixture"
                            }
                          ],
                          "skills": [
                            {
                              "source_table": "TLSkill",
                              "source_row_id": "fixture_skill",
                              "skill_level": 5
                            }
                          ]
                        }
                    """.trimIndent(),
                ),
            )
            val query = CatalogQuery(db)
            val sheet = query.resolveCharacter("gladiator", snapshotId)!!
            val modeled = ModeledCombatPower(query).estimate(snapshotId, sheet)
            val bow = modeled.items.single { it.itemId == "bow_aa_t2_fixture" }
            assertEquals(72L, bow.current)
            assertEquals(102L, bow.potential)
            assertEquals("warehouse", bow.source)
            val unresolved = modeled.items.single { it.itemId == "sword_a_t1_fixture" }
            assertEquals(0L, unresolved.current)
            assertEquals(0L, unresolved.potential)
            assertEquals("unresolved", unresolved.source)
            assertEquals(1, modeled.unresolvedCount)
            assertEquals(250L, modeled.equipmentBase)
            assertEquals(10L, modeled.skillPower)
            assertEquals(0L, modeled.masteryPower)
            assertEquals(322L, modeled.gearScore)
            assertEquals(352L, modeled.potentialGearScore)
            assertEquals(332L, modeled.current)
            assertEquals(362L, modeled.potential)
            assertTrue(modeled.note.contains("Not live window CP"))
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }
}

class QuestlogCombatPowerTest {
    @Test
    fun masteryThresholdsMatchCapturedQuestlogConstants() {
        val none = QuestlogCombatPower.masteryPower(emptyList())
        assertEquals(0L, none.power)
        val term = QuestlogCombatPower.masteryPower(
            listOf(
                com.solisium.core.domain.UserWeaponMastery("greatsword", 167),
                com.solisium.core.domain.UserWeaponMastery("spear", 151),
            ),
        )
        assertEquals(318L, term.levels)
        assertEquals(318L * 3 + 40L, term.power)
        assertEquals(10L, QuestlogCombatPower.skillPower(
            listOf(com.solisium.core.domain.UserSkill(null, "s", null, skillLevel = 5)),
        ))
    }
}
