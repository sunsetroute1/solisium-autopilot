package com.solisium.core.snapshot

import com.solisium.core.db.JvmDatabase
import com.solisium.core.query.CatalogQuery
import com.solisium.core.source.ImportRequest
import com.solisium.core.source.TLHelperDataSource
import com.solisium.core.source.WarehouseLocator
import com.solisium.core.testutil.WarehouseFixtures
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotServiceTest {
    @Test
    fun activatingASnapshotPreservesThePreviousDataset() {
        val firstFile = WarehouseFixtures.writeMiniWarehouse("24118850")
        val secondFile = WarehouseFixtures.writeMiniWarehouse("99999999")
        try {
            val db = JvmDatabase.inMemory()
            val importer = TLHelperDataSource()
            val first = importer.importInto(db, ImportRequest(path = firstFile.toString(), activate = true))
            val second = importer.importInto(db, ImportRequest(path = secondFile.toString(), activate = true))
            val service = SnapshotService(db)
            val snapshots = service.list()
            assertEquals(2, snapshots.size)
            assertEquals(second.snapshotId, service.active()?.id)
            assertFalse(service.get(first.snapshotId!!)!!.active)

            val query = CatalogQuery(db)
            assertEquals(6, query.items(first.snapshotId!!).size)
            assertEquals("24118850", service.get(first.snapshotId!!)!!.gameBuild)
            assertEquals("99999999", service.active()?.gameBuild)

            val reactivated = service.activate(first.snapshotId!!)
            assertEquals(first.snapshotId, reactivated.id)
            assertTrue(reactivated.active)
            assertFalse(service.get(second.snapshotId!!)!!.active)
            assertEquals(2, service.list().size)
        } finally {
            Files.deleteIfExists(firstFile)
            Files.deleteIfExists(secondFile)
        }
    }

    @Test
    fun aliasResolvesToSnapshotWithoutUsingAliasAsPrimaryKey() {
        val warehouse = WarehouseFixtures.writeMiniWarehouse()
        try {
            val db = JvmDatabase.inMemory()
            val receipt = TLHelperDataSource().importInto(db, ImportRequest(path = warehouse.toString()))
            val service = SnapshotService(db)
            service.setAlias("t4", receipt.snapshotId!!)
            service.setAlias("nix-4.0.0", receipt.snapshotId!!)
            val resolved = service.resolve("t4")!!
            assertEquals(receipt.snapshotId, resolved.id)
            assertTrue(resolved.aliases.contains("t4"))
            assertTrue(resolved.aliases.contains("nix-4.0.0"))
        } finally {
            Files.deleteIfExists(warehouse)
        }
    }
}

class WarehouseLocatorTest {
    @Test
    fun reportsMissingWarehouseInsteadOfInventingAPath() {
        val locator = WarehouseLocator(
            env = { null },
            isFile = { false },
            listSqlite = { emptyList() },
        )
        assertNull(locator.find())
        assertTrue(locator.describe().contains("no warehouse"))
    }

    @Test
    fun usesExplicitEnvPathWhenTheFileExists() {
        val expected = Path.of("C:\\tmp\\tl-24118850.sqlite")
        val locator = WarehouseLocator(
            env = { key -> if (key == "SOLISIUM_TL_WAREHOUSE") expected.toString() else null },
            isFile = { it == expected },
            listSqlite = { emptyList() },
        )
        assertEquals(expected, locator.find())
    }
}
