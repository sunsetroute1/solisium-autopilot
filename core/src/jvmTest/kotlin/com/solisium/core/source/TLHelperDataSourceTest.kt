package com.solisium.core.source

import com.solisium.core.db.JvmDatabase
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.query.CatalogQuery
import com.solisium.core.testutil.WarehouseFixtures
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TLHelperDataSourceTest {
    /**
     * A failed import must not disturb the dataset that was already there. Before the
     * snapshot insert moved inside the transaction, a mid-import failure committed a new
     * active snapshot with no game rows and left the previous snapshot deactivated,
     * which silently emptied every query.
     */
    @Test
    fun aFailedImportLeavesThePreviousSnapshotActive() {
        val good = WarehouseFixtures.writeMiniWarehouse()
        val broken = WarehouseFixtures.writeBrokenWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val source = TLHelperDataSource()
            source.importInto(db, ImportRequest(path = good.toString(), activate = true))
            val query = CatalogQuery(db)
            val original = query.activeSnapshotId()!!
            val itemsBefore = query.items(original).size

            val failure = runCatching {
                source.importInto(db, ImportRequest(path = broken.toString(), activate = true))
            }.exceptionOrNull()
            assertTrue(failure != null, "the duplicate item key should have failed the import")

            assertEquals(original, query.activeSnapshotId(), "active snapshot must not change")
            assertEquals(1, query.snapshots().size, "the failed import must not leave a snapshot row")
            assertEquals(itemsBefore, query.items(original).size)
        } finally {
            Files.deleteIfExists(good)
            Files.deleteIfExists(broken)
        }
    }

    @Test
    fun mapsWarehouseRecordsIntoSolisiumTables() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val receipt = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            )

            assertEquals("tl_helper", receipt.source)
            assertEquals(27, receipt.recordsImported)
            assertEquals(12, receipt.recordsSkipped)
            assertTrue(receipt.warnings.any { it.contains("did not resolve") })
            assertTrue(receipt.warnings.any { it.contains("no matching item row") })
            assertTrue(receipt.snapshotId != null)
            assertEquals("24118850", db.schemaQueries.selectActiveSnapshot().executeAsOne().game_build)
            assertEquals("0.2.0", db.schemaQueries.selectActiveSnapshot().executeAsOne().decoder_version)

            val query = CatalogQuery(db)
            val snapshotId = query.activeSnapshotId()!!
            assertEquals(
                CatalogCounts(
                    items = 6,
                    runes = 1,
                    skills = 1,
                    recipes = 1,
                    weapons = 1,
                    armor = 0,
                    accessories = 0,
                    effects = 1,
                    synergies = 1,
                    stats = 1,
                    traits = 1,
                    materials = 2,
                    formulas = 1,
                    itemStats = 2,
                    itemsWithStats = 1,
                    curvePoints = 4,
                    itemCurveLinks = 2,
                    monsters = 1,
                ),
                query.counts(snapshotId),
            )

            val talus = query.monsters(snapshotId, term = null).single()
            assertEquals("Golem Talus", talus.displayName)
            assertEquals("FD_L03_M_Golem_Talus_001", talus.sourceRowId)

            val item = query.items(snapshotId).single { it.name == "Fixture Longbow" && it.sourceTable == "TLItemLooks_Equip" }
            assertEquals("fixture_bow", item.sourceRowId)
            assertEquals("Epic", item.grade)

            val equipItem = query.items(snapshotId).single { it.sourceTable == "TLItemEquip" }
            assertEquals("Fixture Longbow", equipItem.name)
            assertEquals("EItemGrade::kAA", equipItem.grade)
            assertEquals("EItemCategory::kBow", equipItem.category)

            val weapon = query.weapons(snapshotId).single()
            assertEquals("kBow", weapon.weaponType)
            assertEquals("Fixture Longbow", weapon.name)
            assertEquals("weapon", query.lookup(snapshotId, "TLItemEquip", "fixture_bow")?.kind)

            val recipe = query.recipes(snapshotId).single()
            assertEquals("cooking", recipe.recipeKind)
            assertEquals("Fixture Stew", recipe.name)

            assertEquals("ESkillCategory::kFo", query.skills(snapshotId).single().skillType)
            assertEquals("other", query.skills(snapshotId).single().family)
            assertEquals("extracted", query.skills(snapshotId).single().familyConfidence)
            assertEquals("fixture_effect", query.effects(snapshotId).single().sourceRowId)
            assertNull(query.effects(snapshotId).single().name)
            assertEquals("Fixture Skill", query.skills(snapshotId).single().name)
            assertEquals("Fixture Synergy", query.synergies(snapshotId).single().name)
            assertEquals("Strength", query.stats(snapshotId).single().name)
            assertEquals("Accuracy", query.traits(snapshotId).single().name)
            assertEquals("trait", query.lookup(snapshotId, "TLItemTraits", "kAllAccuracy")?.kind)

            assertEquals(1, query.formulas(snapshotId, rowIdContains = "fixture_formula").size)
            val formula = query.formulas(snapshotId).single()
            assertEquals("EFormulaType::kAmountFromMinMax", formula.expression)
            assertEquals("extracted", formula.confidence)
            assertNull(formula.skillSourceRowId)

            assertEquals(
                listOf("Fixture Herb", "Fixture Ore"),
                query.materials(snapshotId).map { it.name },
            )

            // Seed 2 must win over seed 1 for the same base id, and zero values are not
            // stored. The shared M8_Extra_Stat group must not contribute anything.
            val itemStats = query.itemStats(snapshotId, "fixture_bow")
            assertEquals(
                listOf(
                    Triple("main_base", "attack_power_main_hand", 17L),
                    Triple("main_base", "attack_speed_main_hand", 550L),
                ),
                itemStats.map { Triple(it.scope, it.statKey, it.rawValue) },
            )
            assertTrue(itemStats.none { it.scope == "extra_base" })

            assertEquals(
                listOf("enchant" to 2L, "item_level" to null),
                query.itemCurves(snapshotId, "fixture_bow").map { it.curveKind to it.maxLevel },
            )
            // The kBow curve defines L1..L3 but this item caps at +2, so L3 must not appear.
            assertEquals(
                listOf(
                    Triple("enchant", 1L, 5L),
                    Triple("enchant", 2L, 10L),
                    Triple("item_level", 1L, 7L),
                ),
                query.itemCurvePoints(snapshotId, "fixture_bow")
                    .map { Triple(it.curveKind, it.level, it.rawValue) },
            )
            assertTrue(itemStats.all { it.confidence == "extracted" })
            assertTrue(itemStats.all { it.sourceTable == "TLItemLooks_Equip" })
            assertTrue(query.itemStats(snapshotId, "fixture_orphan").isEmpty())
            assertTrue(query.runes(snapshotId).none { it.sourceRowId == "fixture_growth" })
            assertNull(query.runes(snapshotId).single().let { if (it.name == "Ignored Handle") it else null })
            // Looks, equip, and stats rows all answer to fixture_bow and inherit its name.
            assertEquals(3, query.items(snapshotId, nameContains = "Longbow").size)
            assertEquals(0, query.items(snapshotId, nameContains = "Spear").size)
            assertNull(TLHelperDataSource.peekJsonString("{not json", "grade"))
            assertEquals("Epic", TLHelperDataSource.peekJsonString("""{"grade":"Epic","other":1}""", "grade"))
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun mapsPcClassRowsAndResolvesGladiatorFromWeapons() {
        val warehouse = WarehouseFixtures.withPcClass(WarehouseFixtures.writeMiniWarehouse())
        try {
            val db = JvmDatabase.inMemory()
            TLHelperDataSource().importInto(db, ImportRequest(path = warehouse.toString(), activate = true))
            val query = CatalogQuery(db)
            val snapshotId = query.activeSnapshotId()!!
            assertEquals(1, query.classes(snapshotId).size)
            val row = query.classes(snapshotId).single()
            assertEquals("Gladiator", row.name)
            assertEquals("kSpear", row.weaponA)
            assertEquals("kSword2h", row.weaponB)
            val match = query.suggestClass(snapshotId, "Fixture Greatsword", "Fixture Spear")
            assertEquals("Gladiator", match.name)
            assertEquals("extracted", match.source)
            val communityOnly = query.suggestClass(snapshotId, "Fixture Longbow", "Fixture Greatsword")
            assertEquals("Ranger", communityOnly.name)
            assertEquals("community", communityOnly.source)
            val option = query.findBuildClass(snapshotId, name = "Gladiator")
            assertEquals("extracted", option?.source)
            assertTrue(query.buildClassOptions(snapshotId).any { it.name == "Scout" && it.source == "community" })
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun mapsExtractedMonsterDropsFromLotteryUnit() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            TLHelperDataSource().importInto(db, ImportRequest(path = warehouse.toString(), activate = true))
            val query = CatalogQuery(db)
            val snapshotId = query.activeSnapshotId()!!
            val stats = query.dropCacheStats(snapshotId)
            assertEquals(2L, stats.extractedDropRows)
            val bowSources = query.itemDropSources(snapshotId, "fixture_bow")
            assertEquals(
                0.25,
                bowSources.single { it.confidence == "extracted" }.probability,
            )
            val monsterDrops = query.monsterDrops(snapshotId, "FD_L03_M_Golem_Talus_001")
            assertEquals(2, monsterDrops.size)
            assertTrue(monsterDrops.all { it.confidence == "extracted" })
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun mapsCombatPowerRowsAndDerivesItemLinks() {
        val warehouse = WarehouseFixtures.withCombatPower(WarehouseFixtures.writeMiniWarehouse())
        try {
            val db = JvmDatabase.inMemory()
            val receipt = TLHelperDataSource().importInto(
                db,
                ImportRequest(path = warehouse.toString(), activate = true),
            )
            assertTrue(receipt.warnings.any { it.contains("derived; not live character CP") })
            val query = CatalogQuery(db)
            val snapshotId = query.activeSnapshotId()!!
            assertEquals(2, query.counts(snapshotId).combatPowerRows)
            val power = query.itemPowerByRow(snapshotId)
            assertEquals(64L, power["bow_aa_t2_fixture"]?.basePower)
            assertEquals("item-id-tier", power["bow_aa_t2_fixture"]?.evidence)
            assertEquals("derived", power["bow_aa_t2_fixture"]?.confidence)
            assertEquals(80L, power["sword_aaa_unambiguous"]?.basePower)
            assertEquals("source-unambiguous-grade", power["sword_aaa_unambiguous"]?.evidence)
            assertTrue("sword_a_t1_fixture" !in power)
            assertTrue("fixture_bow" !in power)
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }

    @Test
    fun mapsSkillFamiliesFromRowIdPrefixes() {
        val warehouse = WarehouseFixtures.withSkillFamilies(WarehouseFixtures.writeMiniWarehouse())
        try {
            val db = JvmDatabase.inMemory()
            TLHelperDataSource().importInto(db, ImportRequest(path = warehouse.toString(), activate = true))
            val query = CatalogQuery(db)
            val snapshotId = query.activeSnapshotId()!!
            val byId = query.skills(snapshotId).associateBy { it.sourceRowId }
            assertEquals("weapon_skill", byId["WP_SW2_Slam"]?.family)
            assertEquals("kSword2h", byId["WP_SW2_Slam"]?.weaponToken)
            assertEquals("mastery", byId["WM_GT_Unstoppable"]?.family)
            assertEquals("kGauntlet", byId["WM_GT_Unstoppable"]?.weaponToken)
            assertEquals("equipment_skill", byId["WP_Item_core"]?.family)
            assertEquals("gemstone", byId["Gem_Attack_01"]?.family)
            assertEquals("other", byId["fixture_skill"]?.family)
            val hits = query.suggestBuildLayer(snapshotId, "Talus", "skill_core")
            assertTrue(hits.any { it.name?.contains("Skill Core") == true })
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }
}

class EquipCategoryTest {
    @Test
    fun classifiesObservedEquipCategoriesOnly() {
        assertEquals(EquipCategory.Kind.WEAPON, EquipCategory.kind("EItemCategory::kBow"))
        assertEquals(EquipCategory.Kind.ARMOR, EquipCategory.kind("EItemCategory::kHead"))
        assertEquals(EquipCategory.Kind.ACCESSORY, EquipCategory.kind("EItemCategory::kRing"))
        assertEquals("kGauntlet", EquipCategory.token("EItemCategory::kGauntlet"))
        assertNull(EquipCategory.kind("EItemCategory::kAmmo"))
        assertNull(EquipCategory.kind(null))
    }
}
