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

class DesiredBuildPlannerTest {
    @Test
    fun typedCombatPowerGapIsSubtractionNotAModeledDelta() {
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
                          "schemaVersion": 1,
                          "character": {
                            "id": "gladiator",
                            "name": "Gladiator",
                            "combat_power": 8297,
                            "gear_score": 8000
                          }
                        }
                    """.trimIndent(),
                ),
            )
            val plan = DesiredBuildPlanner(CatalogQuery(db)).plan(
                snapshotId = snapshotId,
                goal = BuildGoal.MeleeDps,
                characterId = "gladiator",
                desiredCombatPower = 10000,
                desiredGearScore = 9000,
                axes = listOf(StatAxis.HitChance, StatAxis.Endurance),
            )
            assertEquals(8297L, plan.currentCombatPower)
            assertEquals(1703L, plan.combatPowerGap)
            assertEquals(1000L, plan.gearScoreGap)
            assertEquals(250L, plan.modeled?.current)
            assertEquals(250L, plan.modeled?.gearScore)
            assertEquals(9750L, plan.modeledCombatPowerGap)
            assertTrue(plan.roadmap.any { it.kind == "typed-cp" })
            assertTrue(plan.roadmap.any { it.kind == "modeled-cp" })
            assertTrue(plan.limits.any { it.contains("does not compute") })
            assertTrue(plan.advice.scoringNote.contains("Not DPS"))
            assertEquals(listOf("Hit chance", "Endurance"), plan.axes)
            assertTrue(plan.influences.any { it.layer == "weapon_mastery_level" })
            assertTrue(plan.influences.any { it.layer == "skill_core" })
            assertTrue(plan.roadmap.any { it.kind == "layers" })
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun classTypeIsIncludedInThePlan() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            val query = CatalogQuery(db)
            val gladiator = query.findBuildClass(snapshotId, name = "Gladiator")!!
            val plan = DesiredBuildPlanner(query).plan(
                snapshotId = snapshotId,
                goal = BuildGoal.MeleeDps,
                classOption = gladiator,
            )
            assertEquals("Gladiator", plan.selectedClass?.name)
            assertEquals("community", plan.selectedClass?.source)
            assertTrue(plan.roadmap.any { it.kind == "class" && it.title.contains("Gladiator") })
            assertTrue(plan.limits.any { it.contains("Class types") })
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun missingDesiredValuesLeaveTheGapNull() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            val plan = DesiredBuildPlanner(CatalogQuery(db)).plan(snapshotId, BuildGoal.RangedDps)
            assertNull(plan.combatPowerGap)
            assertNull(plan.gearScoreGap)
            assertTrue(plan.skillCoverage.note.contains("Presence only"))
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }
}
