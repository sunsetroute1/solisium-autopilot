package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path

object CombatLogPaths {
    fun detect(env: (String) -> String? = { System.getenv(it) }): Path? {
        val local = env("LOCALAPPDATA") ?: return null
        val candidates = listOf(
            Path.of(local, "TL", "Saved", "CombatLogs"),
            Path.of(local, "TL", "Saved", "COMBATLOGS"),
        )
        return candidates.firstOrNull { Files.isDirectory(it) }
    }

    fun saveGamesDir(env: (String) -> String? = { System.getenv(it) }): Path? {
        val local = env("LOCALAPPDATA") ?: return null
        val dir = Path.of(local, "TL", "Saved", "SaveGames")
        return dir.takeIf { Files.isDirectory(it) }
    }

    fun listLogFiles(directory: Path): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.newDirectoryStream(directory).use { entries ->
            entries
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".txt", ignoreCase = true) }
                .sortedByDescending { Files.getLastModifiedTime(it).toMillis() }
        }
    }

    fun newestLog(directory: Path): Path? = listLogFiles(directory).firstOrNull()
}
