package com.solisium.core.db

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationTest {
    @Test
    fun schemaVersionMatchesGeneratedSchema() {
        assertEquals(SchemaVersion.CURRENT.toLong(), SolisiumDatabase.Schema.version)
    }

    /**
     * Simulates a database written before the newer tables existed: user_version 0 and
     * none of them present. Opening it must apply every migration rather than leave holes.
     */
    @Test
    fun openingAPreMigrationDatabaseAppliesEveryMigration() {
        val dir = Files.createTempDirectory("solisium-migration")
        val file = dir.resolve("solisium.db")
        val migrated = listOf(
            "game_item_stat", "game_stat_curve", "game_item_curve", "game_class",
            "game_combat_power", "game_item_power",
            "user_weapon_mastery", "user_build_layer",
            "game_boss", "game_item_drop",
        )
        JvmDatabase.openOrCreate(file)
        jdbc(file) { statement ->
            migrated.forEach { statement.execute("DROP TABLE $it") }
            revertCharacterSheetAlter(statement)
            statement.execute("PRAGMA user_version = 0")
        }
        migrated.forEach { assertFalse(hasTable(file, it), "$it should be gone before migrating") }

        JvmDatabase.openOrCreate(file)

        migrated.forEach { assertTrue(hasTable(file, it), "$it should be restored by migration") }
        assertTrue(hasColumn(file, "user_character", "gear_score"))
        assertTrue(hasColumn(file, "user_character", "strength"))
        assertTrue(hasColumn(file, "user_character", "fortitude"))
        assertTrue(hasColumn(file, "user_character", "class_name"))
        assertTrue(hasColumn(file, "user_character", "class_source"))
        assertTrue(hasTable(file, "game_class"))
        assertTrue(hasColumn(file, "user_equipment", "name"))
        assertTrue(hasColumn(file, "user_inventory", "name"))
        assertTrue(hasColumn(file, "game_skill", "family"))
        assertTrue(hasColumn(file, "user_skills", "name"))
        jdbc(file) { statement ->
            statement.executeQuery("PRAGMA user_version").use { rs ->
                rs.next()
                assertEquals(SolisiumDatabase.Schema.version, rs.getLong(1))
            }
        }
        Files.deleteIfExists(file)
        Files.deleteIfExists(dir)
    }

    /**
     * A stale .sqm would give long-lived databases a different table shape than fresh
     * ones. This drops the migration-created tables from a full database, lets the
     * migrations rebuild them, and compares their definitions against a fresh database.
     */
    @Test
    fun migrationsRebuildTablesIdenticallyToAFreshCreate() {
        val dir = Files.createTempDirectory("solisium-schema-parity")
        val fresh = dir.resolve("fresh.db")
        val rebuilt = dir.resolve("rebuilt.db")
        val migrated = listOf(
            "game_item_stat", "game_stat_curve", "game_item_curve", "game_class",
            "game_combat_power", "game_item_power",
            "user_weapon_mastery", "user_build_layer",
            "game_boss", "game_item_drop",
        )

        JvmDatabase.openOrCreate(fresh)
        JvmDatabase.openOrCreate(rebuilt)
        jdbc(rebuilt) { statement ->
            migrated.forEach { statement.execute("DROP TABLE $it") }
            revertCharacterSheetAlter(statement)
            statement.execute("PRAGMA user_version = 0")
        }
        JvmDatabase.openOrCreate(rebuilt)

        assertEquals(definitionsOf(fresh, migrated), definitionsOf(rebuilt, migrated))
        Files.deleteIfExists(fresh)
        Files.deleteIfExists(rebuilt)
        Files.deleteIfExists(dir)
    }

    /** Table definitions normalised so formatting differences do not count as drift. */
    private fun definitionsOf(file: java.nio.file.Path, names: List<String>): List<String> {
        val out = mutableListOf<String>()
        jdbc(file) { statement ->
            val list = names.joinToString(",") { "'$it'" }
            statement.executeQuery(
                "SELECT name, IFNULL(sql, '') FROM sqlite_master WHERE name IN ($list) ORDER BY name",
            ).use { rs ->
                while (rs.next()) {
                    out.add("${rs.getString(1)}|${rs.getString(2).replace(Regex("\\s+"), " ").trim()}")
                }
            }
        }
        check(out.size == names.size) { "expected ${names.size} tables in $file, found ${out.size}" }
        return out
    }

    /** A database already at the current version must not be migrated again. */
    @Test
    fun reopeningAnUpToDateDatabaseIsANoOp() {
        val dir = Files.createTempDirectory("solisium-migration-noop")
        val file = dir.resolve("solisium.db")
        JvmDatabase.openOrCreate(file)
        JvmDatabase.openOrCreate(file)
        assertTrue(hasTable(file, "game_stat_curve"))
        Files.deleteIfExists(file)
        Files.deleteIfExists(dir)
    }

    /**
     * `3.sqm`, `4.sqm`, and `5.sqm` include ALTER TABLE. A freshly created
     * database already has those columns, so replaying every migration requires
     * dropping them first. `5.sqm` also creates `game_class`. `6.sqm` creates
     * the combat-power tables. `7.sqm` adds skill families and typed mastery
     * / build-layer tables; those columns must be dropped before replaying.
     */
    private fun revertCharacterSheetAlter(statement: java.sql.Statement) {
        statement.execute("DROP TABLE IF EXISTS game_item_drop")
        statement.execute("DROP TABLE IF EXISTS game_boss")
        statement.execute("DROP TABLE IF EXISTS user_build_layer")
        statement.execute("DROP TABLE IF EXISTS user_weapon_mastery")
        statement.execute("DROP TABLE IF EXISTS game_item_power")
        statement.execute("DROP TABLE IF EXISTS game_combat_power")
        statement.execute("DROP TABLE IF EXISTS game_class")
        statement.execute("ALTER TABLE game_skill DROP COLUMN family")
        statement.execute("ALTER TABLE game_skill DROP COLUMN weapon_token")
        statement.execute("ALTER TABLE game_skill DROP COLUMN family_confidence")
        statement.execute("ALTER TABLE user_skills DROP COLUMN name")
        statement.execute("ALTER TABLE user_skills DROP COLUMN skill_level")
        statement.execute("ALTER TABLE user_skills DROP COLUMN family")
        statement.execute("ALTER TABLE user_character DROP COLUMN class_name")
        statement.execute("ALTER TABLE user_character DROP COLUMN class_source")
        statement.execute("ALTER TABLE user_character DROP COLUMN gear_score")
        statement.execute("ALTER TABLE user_character DROP COLUMN strength")
        statement.execute("ALTER TABLE user_character DROP COLUMN dexterity")
        statement.execute("ALTER TABLE user_character DROP COLUMN wisdom")
        statement.execute("ALTER TABLE user_character DROP COLUMN perception")
        statement.execute("ALTER TABLE user_character DROP COLUMN fortitude")
        statement.execute("ALTER TABLE user_equipment DROP COLUMN name")
        statement.execute("ALTER TABLE user_weapon DROP COLUMN name")
        statement.execute("ALTER TABLE user_inventory DROP COLUMN name")
    }

    private fun hasColumn(file: java.nio.file.Path, table: String, column: String): Boolean {
        var found = false
        jdbc(file) { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { rs ->
                while (rs.next()) {
                    if (rs.getString("name") == column) found = true
                }
            }
        }
        return found
    }

    private fun hasTable(file: java.nio.file.Path, name: String): Boolean {
        var found = false
        jdbc(file) { statement ->
            statement.executeQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$name'",
            ).use { rs -> found = rs.next() }
        }
        return found
    }

    private fun jdbc(file: java.nio.file.Path, block: (java.sql.Statement) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            connection.createStatement().use(block)
        }
    }
}
