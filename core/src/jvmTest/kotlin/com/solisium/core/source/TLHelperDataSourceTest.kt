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
            assertEquals(24, receipt.recordsImported)
            assertEquals(11, receipt.recordsSkipped)
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
                ),
                query.counts(snapshotId),
            )

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
            assertEquals("fixture_effect", query.effects(snapshotId).single().sourceRowId)
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
