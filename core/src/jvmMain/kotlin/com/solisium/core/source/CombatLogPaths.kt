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

    /**
     * Which log files an import should read, and anything the user should be told about
     * that choice. Shared by the CLI and the desktop app so both apply the same rules:
     * with no path, only the newest log in the detected folder is imported, because the
     * game appends a fresh file per combat and importing the whole history silently
     * would misrepresent "your last fight".
     */
    data class LogSelection(val files: List<Path>, val warnings: List<String>)

    fun selectForImport(pathArg: String?): LogSelection {
        if (pathArg == null) {
            val folder = detect()
                ?: error("combat log folder not found under %LOCALAPPDATA%\\TL\\Saved\\CombatLogs")
            val files = listLogFiles(folder)
            if (files.isEmpty()) error("no .txt files in $folder")
            val warnings = if (files.size > 1) {
                listOf("importing newest ${files.first().fileName}; ${files.size - 1} older log(s) skipped")
            } else {
                emptyList()
            }
            return LogSelection(listOf(files.first()), warnings)
        }
        val path = Path.of(pathArg)
        return when {
            Files.isDirectory(path) -> {
                val files = listLogFiles(path)
                if (files.isEmpty()) error("no .txt files in $path")
                LogSelection(files, emptyList())
            }
            Files.isRegularFile(path) -> LogSelection(listOf(path), emptyList())
            else -> error("path not found: $path")
        }
    }
}
