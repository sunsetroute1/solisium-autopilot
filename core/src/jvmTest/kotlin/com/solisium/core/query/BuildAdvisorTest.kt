package com.solisium.core.query

import com.solisium.core.db.JvmDatabase
import com.solisium.core.domain.CommunityHit
import com.solisium.core.domain.CommunitySnapshot
import com.solisium.core.source.ImportRequest
import com.solisium.core.source.ManualImportDataSource
import com.solisium.core.source.TLHelperDataSource
import com.solisium.core.testutil.WarehouseFixtures
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildAdvisorTest {
    @Test
    fun rangedGoalRanksTheFixtureBowByExtractedMainBase() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            val query = CatalogQuery(db)
            val advice = BuildAdvisor(query).advise(snapshotId, BuildGoal.RangedDps)
            val weapon = advice.slots.single { it.slot == "bow" }
            val top = weapon.recommended.single()
            assertEquals("Fixture Longbow", top.name)
            // 17 attack power + 550 attack speed from main_base seed 2. Not DPS.
            assertEquals(567L, top.score)
            assertTrue(top.contributions.any { it.statKey == "attack_power_main_hand" && it.rawValue == 17L })
            assertTrue(advice.briefing.any { it.contains("Fixture Longbow") })
            assertTrue(advice.scoringNote.contains("Not DPS"))
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun loadoutGapUsesTheSameExtractedScore() {
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
                          "character": { "id": "archer", "name": "Archer" },
                          "weapons": [
                            {
                              "slot": "main",
                              "source_table": "TLItemLooks_Equip",
                              "source_row_id": "fixture_bow"
                            }
                          ]
                        }
                    """.trimIndent(),
                ),
            )
            val advice = BuildAdvisor(CatalogQuery(db)).advise(snapshotId, BuildGoal.RangedDps, "archer")
            val weapon = advice.slots.single { it.slot == "bow" }
            assertEquals("Archer", advice.characterName)
            assertEquals("Fixture Longbow", weapon.equipped?.name)
            assertEquals(weapon.recommended.first().score, weapon.equipped?.score)
            assertEquals(0L, weapon.gap)
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun communityNameMatchDoesNotChangeTheScore() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            val community = CommunitySnapshot(
                fetchedAt = "now",
                sources = listOf("questlog"),
                patchLabel = "TLDB patch 3.18.0",
                items = listOf(
                    CommunityHit("questlog", "Fixture Longbow", "item · weapons · bow", null, null),
                ),
                skills = emptyList(),
                notes = emptyList(),
                warnings = emptyList(),
            )
            val advice = BuildAdvisor(CatalogQuery(db)).advise(
                snapshotId,
                BuildGoal.RangedDps,
                community = community,
            )
            val top = advice.slots.single { it.slot == "bow" }.recommended.single()
            assertEquals(567L, top.score)
            assertEquals(1, top.communityHits)
            assertTrue(advice.briefing.any { it.contains("Questlog") })
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun rangedKeysDoNotSwallowArmorStats() {
        val keys = BuildGoal.RangedDps.keysOn(
            setOf("attack_power_main_hand", "range_armor", "attack_range_main_hand"),
        )
        assertTrue("attack_power_main_hand" in keys)
        assertTrue("attack_range_main_hand" in keys)
        assertTrue("range_armor" !in keys)
    }

    @Test
    fun unnamedSkillsStayNullInSharesWhenNoCombatLogs() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            val advice = BuildAdvisor(CatalogQuery(db)).advise(snapshotId, BuildGoal.RangedDps)
            assertTrue(advice.skillShares.isEmpty())
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun classTypeLimitsWeaponRanksToThePair() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val snapshotId = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            ).snapshotId!!
            val query = CatalogQuery(db)
            val gladiator = query.findBuildClass(snapshotId, name = "Gladiator")!!
            assertEquals("community", gladiator.source)
            val advice = BuildAdvisor(query).advise(
                snapshotId,
                BuildGoal.RangedDps,
                classOption = gladiator,
            )
            assertEquals("Gladiator", advice.className)
            assertTrue(advice.slots.none { it.slot == "bow" && it.recommended.isNotEmpty() })
            assertTrue(advice.briefing.any { it.contains("Gladiator") })
            val scout = query.findBuildClass(snapshotId, name = "Scout")!!
            val scoutAdvice = BuildAdvisor(query).advise(snapshotId, BuildGoal.MeleeDps, classOption = scout)
            assertEquals("Fixture Longbow", scoutAdvice.slots.single { it.slot == "bow" }.recommended.single().name)
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun extraAxisKeysRankOnlyWarehouseStatsThatExist() {
        val keys = BuildGoal.MeleeDps.keysOn(setOf("attack_power_main_hand")) +
            StatAxis.HitChance.keysOn(setOf("melee_accuracy", "made_up_stat"))
        assertTrue("attack_power_main_hand" in keys)
        assertTrue("melee_accuracy" in keys)
        assertTrue("made_up_stat" !in keys)
    }
}
