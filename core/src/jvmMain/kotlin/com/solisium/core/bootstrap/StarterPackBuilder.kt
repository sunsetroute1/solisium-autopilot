package com.solisium.core.bootstrap

import com.solisium.core.db.JvmDatabase
import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.source.CombatLogDataSource
import com.solisium.core.source.ImportRequest
import com.solisium.core.source.ManualImportDataSource
import com.solisium.core.source.TLHelperDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.DriverManager

object StarterPackBuilder {
    fun build(outputDir: Path) {
        Files.createDirectories(outputDir)
        val warehousePath = outputDir.resolve("tl-starter.sqlite")
        val databasePath = outputDir.resolve("solisium.sqlite")
        if (Files.exists(databasePath)) Files.delete(databasePath)
        StarterWarehouse.write(warehousePath)
        val db = JvmDatabase.openOrCreate(databasePath)
        importStarter(db, warehousePath, readResource("starter-character.json"), readResource("starter-combat-log.txt"))
    }

    fun importStarter(
        db: SolisiumDatabase,
        warehousePath: Path,
        characterJson: String,
        combatLog: String,
    ) {
        TLHelperDataSource().importInto(
            db,
            ImportRequest(path = warehousePath.toString(), activate = true),
        )
        ManualImportDataSource().importInto(
            db,
            ImportRequest(path = "starter-character.json", content = characterJson),
        )
        CombatLogDataSource().importInto(
            db,
            ImportRequest(path = "starter-combat-log.txt", content = combatLog),
        )
    }

    internal fun readResource(name: String): String =
        StarterPackBuilder::class.java.getResourceAsStream("/$name")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("missing classpath resource /$name")
}

enum class StarterSeedResult {
    SKIPPED,
    COPIED,
    IMPORTED,
    NO_BUNDLE,
}

object StarterBootstrap {
    fun isStarterWarehousePath(path: String?): Boolean =
        path?.contains("tl-starter.sqlite", ignoreCase = true) == true

    fun seedIfNeeded(userDatabase: Path): StarterSeedResult {
        if (snapshotCount(userDatabase) > 0) return StarterSeedResult.SKIPPED
        val bundledDb = InstallResources.starter("solisium.sqlite")
        if (bundledDb != null) {
            Files.createDirectories(userDatabase.parent)
            Files.copy(bundledDb, userDatabase, StandardCopyOption.REPLACE_EXISTING)
            return StarterSeedResult.COPIED
        }
        val bundledWarehouse = InstallResources.starter("tl-starter.sqlite") ?: return StarterSeedResult.NO_BUNDLE
        val db = JvmDatabase.openOrCreate(userDatabase)
        StarterPackBuilder.importStarter(
            db,
            bundledWarehouse,
            StarterPackBuilder.readResource("starter-character.json"),
            StarterPackBuilder.readResource("starter-combat-log.txt"),
        )
        return StarterSeedResult.IMPORTED
    }

    internal fun snapshotCount(database: Path): Long {
        if (!Files.isRegularFile(database)) return 0
        return runCatching {
            DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    val result = statement.executeQuery("SELECT COUNT(*) FROM dataset_snapshot")
                    if (result.next()) result.getLong(1) else 0L
                }
            }
        }.getOrDefault(0L)
    }
}
