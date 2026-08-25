package com.solisium.core.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Path

object JvmDatabase {
    const val SCHEMA_VERSION: Int = com.solisium.core.db.SchemaVersion.CURRENT

    fun inMemory(): SolisiumDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SolisiumDatabase.Schema.create(driver)
        setUserVersion(driver, SolisiumDatabase.Schema.version)
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        return SolisiumDatabase(driver)
    }

    fun openOrCreate(path: Path): SolisiumDatabase {
        path.parent?.toFile()?.mkdirs()
        val absolute = path.toAbsolutePath().normalize().toString().replace('\\', '/')
        val driver = JdbcSqliteDriver("jdbc:sqlite:$absolute")
        val target = SolisiumDatabase.Schema.version
        if (!hasTable(driver, "dataset_snapshot")) {
            SolisiumDatabase.Schema.create(driver)
            setUserVersion(driver, target)
        } else {
            // Databases written before migrations existed report user_version 0;
            // SQLDelight still applies every migration at or above that version.
            val current = userVersion(driver)
            if (current < target) {
                SolisiumDatabase.Schema.migrate(driver, current, target)
                setUserVersion(driver, target)
            }
        }
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        return SolisiumDatabase(driver)
    }

    private fun userVersion(driver: JdbcSqliteDriver): Long =
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
            parameters = 0,
        ).value ?: 0L

    private fun setUserVersion(driver: JdbcSqliteDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version;", 0)
    }

    private fun hasTable(driver: JdbcSqliteDriver, name: String): Boolean {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            mapper = { cursor -> QueryResult.Value(cursor.next().value) },
            parameters = 1,
        ) {
            bindString(0, name)
        }.value
    }
}
