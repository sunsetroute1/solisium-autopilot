package com.solisium.core.db

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** v8 databases may already have a bare game_boss table from warehouse import. */
class GameBossMigrationTest {
    @Test
    fun migratesWhenGameBossAlreadyExistsAtVersion8() {
        val home = System.getenv("USERPROFILE") ?: return
        val live = java.nio.file.Path.of(home, ".solisium", "solisium.sqlite")
        if (!Files.isRegularFile(live)) return

        val dir = Files.createTempDirectory("solisium-v8-boss")
        val copy = dir.resolve("solisium.db")
        Files.copy(live, copy, StandardCopyOption.REPLACE_EXISTING)
        val versionBefore = userVersion(copy)
        if (versionBefore != 8L) {
            Files.deleteIfExists(copy)
            Files.deleteIfExists(dir)
            return
        }

        JvmDatabase.openOrCreate(copy)

        assertEquals(SolisiumDatabase.Schema.version, userVersion(copy))
        assertTrue(hasColumn(copy, "game_boss", "synced_at"))
        assertTrue(hasTable(copy, "game_item_drop"))
        Files.deleteIfExists(copy)
        Files.deleteIfExists(dir)
    }

    private fun userVersion(file: java.nio.file.Path): Long {
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { rs ->
                    rs.next()
                    return rs.getLong(1)
                }
            }
        }
    }

    private fun hasTable(file: java.nio.file.Path, name: String): Boolean {
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$name'",
                ).use { rs -> return rs.next() }
            }
        }
    }

    private fun hasColumn(file: java.nio.file.Path, table: String, column: String): Boolean {
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info($table)").use { rs ->
                    while (rs.next()) {
                        if (rs.getString("name") == column) return true
                    }
                }
            }
        }
        return false
    }
}
