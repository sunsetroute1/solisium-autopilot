package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path

enum class CombatLogFolderStatus {
    /** At least one CombatLogVersion file was found. */
    FOUND_WITH_LOGS,
    /** CombatLogs folder exists but has no .txt/.log files yet. */
    FOUND_EMPTY,
    /** TL Saved exists; CombatLogs not created until logging is enabled and a fight ends. */
    MISSING_BUT_SAVED_EXISTS,
    /** No %LOCALAPPDATA%\\TL\\Saved tree. */
    TL_NOT_INSTALLED,
}

data class CombatLogDiscovery(
    val status: CombatLogFolderStatus,
    /** Standard path: …\\TL\\Saved\\CombatLogs */
    val primaryFolder: Path?,
    val savedRoot: Path?,
    /** Newest first — from CombatLogs folders plus a shallow Saved scan. */
    val logFiles: List<Path>,
    val scannedFolders: List<Path>,
) {
    fun hint(): String = when (status) {
        CombatLogFolderStatus.FOUND_WITH_LOGS ->
            "${logFiles.size} log file(s) ready to import."
        CombatLogFolderStatus.FOUND_EMPTY ->
            "CombatLogs folder exists but is empty. " + CombatLogSetupGuide.steps.first() +
                " — then finish an eligible fight. T&L writes the file when combat ends."
        CombatLogFolderStatus.MISSING_BUT_SAVED_EXISTS ->
            "No CombatLogs folder yet. " + CombatLogSetupGuide.steps.first() +
                " — the folder is created when the first log is written."
        CombatLogFolderStatus.TL_NOT_INSTALLED ->
            "Throne and Liberty save folder not found under %LOCALAPPDATA%\\TL\\Saved."
    }
}

object CombatLogPaths {
    fun detect(env: (String) -> String? = { System.getenv(it) }): Path? {
        val discovery = discover(env = env)
        return when (discovery.status) {
            CombatLogFolderStatus.TL_NOT_INSTALLED -> null
            else -> discovery.primaryFolder
        }
    }

    fun discover(env: (String) -> String? = { System.getenv(it) }): CombatLogDiscovery {
        val local = env("LOCALAPPDATA")
            ?: return CombatLogDiscovery(
                CombatLogFolderStatus.TL_NOT_INSTALLED,
                null,
                null,
                emptyList(),
                emptyList(),
            )
        val saved = Path.of(local, "TL", "Saved")
        if (!Files.isDirectory(saved)) {
            return CombatLogDiscovery(
                CombatLogFolderStatus.TL_NOT_INSTALLED,
                null,
                null,
                emptyList(),
                listOf(saved),
            )
        }
        val expected = saved.resolve("CombatLogs")
        val folders = combatLogFolders(saved).ifEmpty { listOf(expected) }
        val fromFolders = folders.flatMap { listLogFiles(it) }.distinct()
        val discovered = if (fromFolders.isNotEmpty()) {
            fromFolders
        } else {
            scanForLogs(saved, maxDepth = 5)
        }.sortedByDescending { Files.getLastModifiedTime(it).toMillis() }

        val status = when {
            discovered.isNotEmpty() -> CombatLogFolderStatus.FOUND_WITH_LOGS
            folders.any { Files.isDirectory(it) } -> CombatLogFolderStatus.FOUND_EMPTY
            else -> CombatLogFolderStatus.MISSING_BUT_SAVED_EXISTS
        }
        return CombatLogDiscovery(
            status = status,
            primaryFolder = expected,
            savedRoot = saved,
            logFiles = discovered,
            scannedFolders = folders,
        )
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
                .filter { Files.isRegularFile(it) && isLogExtension(it) }
                .sortedByDescending { Files.getLastModifiedTime(it).toMillis() }
        }
    }

    fun newestLog(directory: Path): Path? = listLogFiles(directory).firstOrNull()

    data class LogSelection(val files: List<Path>, val warnings: List<String>)

    fun selectForImport(pathArg: String?, env: (String) -> String? = { System.getenv(it) }): LogSelection {
        if (pathArg == null) {
            val discovery = discover(env = env)
            if (discovery.logFiles.isEmpty()) {
                error("${discovery.hint()} Path: ${discovery.primaryFolder ?: discovery.savedRoot}")
            }
            val files = discovery.logFiles
            val warnings = buildList {
                if (discovery.status == CombatLogFolderStatus.FOUND_WITH_LOGS &&
                    discovery.scannedFolders.none { Files.isDirectory(it) }
                ) {
                    add("found logs outside CombatLogs folder via scan")
                }
                add("importing ${files.size} log file(s); duplicates are skipped automatically")
            }
            return LogSelection(files, warnings)
        }
        val path = Path.of(pathArg)
        return when {
            Files.isDirectory(path) -> {
                val files = listLogFiles(path)
                if (files.isEmpty()) {
                    val scanned = scanForLogs(path, maxDepth = 3)
                    if (scanned.isEmpty()) error("no combat logs in $path")
                    LogSelection(scanned, emptyList())
                } else {
                    LogSelection(files, emptyList())
                }
            }
            Files.isRegularFile(path) -> LogSelection(listOf(path), emptyList())
            else -> error("path not found: $path")
        }
    }

    internal fun combatLogFolders(saved: Path): List<Path> {
        if (!Files.isDirectory(saved)) return emptyList()
        return Files.newDirectoryStream(saved).use { entries ->
            entries
                .filter { Files.isDirectory(it) && it.fileName.toString().equals("CombatLogs", ignoreCase = true) }
                .sortedBy { it.fileName.toString().lowercase() }
        }
    }

    internal fun isLogExtension(path: Path): Boolean {
        val name = path.fileName.toString()
        return name.endsWith(".txt", ignoreCase = true) || name.endsWith(".log", ignoreCase = true)
    }

    internal fun scanForLogs(root: Path, maxDepth: Int): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.walk(root, maxDepth).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && isLogExtension(it) }
                .filter { looksLikeCombatLog(it) }
                .sorted { a, b ->
                    Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a))
                }
                .toList()
        }
    }

    internal fun looksLikeCombatLog(path: Path): Boolean = runCatching {
        Files.newBufferedReader(path).use { reader ->
            reader.readLine()?.trim()?.startsWith("CombatLogVersion", ignoreCase = true) == true
        }
    }.getOrDefault(false)
}
