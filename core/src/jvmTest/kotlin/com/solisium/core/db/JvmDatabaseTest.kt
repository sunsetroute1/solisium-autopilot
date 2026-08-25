package com.solisium.core.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files

class JvmDatabaseTest {
    @Test
    fun openOrCreateAppliesSchemaOnAWindowsStylePath() {
        val dir = Files.createTempDirectory("solisium-db")
        val path = dir.resolve("solisium.sqlite")
        val db = JvmDatabase.openOrCreate(path)
        db.schemaQueries.insertCharacter(
            id = "file-db",
            name = "File Hero",
            level = 1,
            combat_power = null,
            server = null,
            notes = null,
            created_at = "1970-01-01T00:00:00Z",
            updated_at = "1970-01-01T00:00:00Z",
        )
        assertEquals("File Hero", db.schemaQueries.selectCharacter("file-db").executeAsOne().name)
        assertTrue(Files.size(path) > 0)
    }
}
