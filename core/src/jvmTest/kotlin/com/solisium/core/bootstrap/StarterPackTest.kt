package com.solisium.core.bootstrap

import com.solisium.core.db.JvmDatabase
import com.solisium.core.query.CatalogQuery
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterPackBuilderTest {
    @Test
    fun buildsCatalogCharacterAndCombatSession() {
        val dir = Files.createTempDirectory("starter-pack")
        StarterPackBuilder.build(dir)
        val db = JvmDatabase.openOrCreate(dir.resolve("solisium.sqlite"))
        val query = CatalogQuery(db)
        val snapshot = query.snapshotService().active()
        assertTrue(snapshot != null, "starter snapshot should be active")
        assertTrue(query.counts(snapshot!!.id).items > 0)
        assertTrue(query.characters().any { it.id == "starter" })
        assertTrue(query.combatSessions().isNotEmpty())
    }
}

class StarterBootstrapTest {
    @Test
    fun seedsEmptyDatabaseFromBuiltStarterPack() {
        val root = Files.createTempDirectory("starter-resources")
        val starterDir = root.resolve("starter")
        StarterPackBuilder.build(starterDir)
        System.setProperty("compose.application.resources.dir", root.toString())
        try {
            val userDb = root.resolve("user").resolve("solisium.sqlite")
            val result = StarterBootstrap.seedIfNeeded(userDb)
            assertEquals(StarterSeedResult.COPIED, result)
            assertTrue(StarterBootstrap.snapshotCount(userDb) > 0)
            assertEquals(StarterSeedResult.SKIPPED, StarterBootstrap.seedIfNeeded(userDb))
        } finally {
            System.clearProperty("compose.application.resources.dir")
        }
    }
}
