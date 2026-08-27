package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CombatLogPathsTest {
    @Test
    fun listsTxtAndLogFilesNewestFirst() {
        val dir = Files.createTempDirectory("solisium-logs")
        val older = dir.resolve("older.txt")
        val newer = dir.resolve("newer.log")
        val sav = dir.resolve("not-a-log.sav")
        Files.writeString(older, "CombatLogVersion,4\n")
        Files.writeString(newer, "CombatLogVersion,4\n")
        Files.writeString(sav, "ignored")
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000))
        val listed = CombatLogPaths.listLogFiles(dir)
        assertEquals(listOf(newer, older), listed)
        assertEquals(newer, CombatLogPaths.newestLog(dir))
    }

    @Test
    fun discoverFindsLogsOutsideCombatLogsFolder() {
        val local = Files.createTempDirectory("localappdata")
        val saved = local.resolve("TL").resolve("Saved")
        val nested = saved.resolve("SomeOther").also { Files.createDirectories(it) }
        val log = nested.resolve("fight.txt")
        Files.writeString(log, "CombatLogVersion,4\n2026-01-01T00:00:00,DamageDone,A,1,1,0,0,kNormalHit,Me,Target\n")
        val discovery = CombatLogPaths.discover { if (it == "LOCALAPPDATA") local.toString() else null }
        assertEquals(CombatLogFolderStatus.FOUND_WITH_LOGS, discovery.status)
        assertTrue(discovery.logFiles.contains(log))
    }

    @Test
    fun discoverReportsMissingFolderWhenSavedExists() {
        val local = Files.createTempDirectory("localappdata")
        val saved = local.resolve("TL").resolve("Saved")
        Files.createDirectories(saved)
        val discovery = CombatLogPaths.discover { if (it == "LOCALAPPDATA") local.toString() else null }
        assertEquals(CombatLogFolderStatus.MISSING_BUT_SAVED_EXISTS, discovery.status)
        assertTrue(discovery.logFiles.isEmpty())
        assertTrue(discovery.hint().contains("CombatLogs"))
    }

    @Test
    fun detectReturnsNullWhenLocalAppDataMissing() {
        assertEquals(null, CombatLogPaths.detect { null })
        assertEquals(null, CombatLogPaths.saveGamesDir { null })
    }

    @Test
    fun selectingADirectoryTakesEveryLogAndAFileTakesOnlyThatOne() {
        val dir = Files.createTempDirectory("solisium-select")
        val a = dir.resolve("a.txt")
        val b = dir.resolve("b.txt")
        Files.writeString(a, "CombatLogVersion,4\n")
        Files.writeString(b, "CombatLogVersion,4\n")
        Files.setLastModifiedTime(a, FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(b, FileTime.fromMillis(2_000))

        val fromDir = CombatLogPaths.selectForImport(dir.toString())
        assertEquals(listOf(b, a), fromDir.files)

        val fromFile = CombatLogPaths.selectForImport(a.toString())
        assertEquals(listOf(a), fromFile.files)
    }

    @Test
    fun selectingAMissingPathFails() {
        val missing = Files.createTempDirectory("solisium-missing").resolve("nope.txt")
        val error = assertFailsWith<IllegalStateException> { CombatLogPaths.selectForImport(missing.toString()) }
        assertTrue(error.message!!.contains("path not found"), "got: ${error.message}")
    }

    @Test
    fun defaultImportSelectsAllDiscoveredLogs() {
        val local = Files.createTempDirectory("localappdata")
        val saved = local.resolve("TL").resolve("Saved").resolve("CombatLogs")
        Files.createDirectories(saved)
        val a = saved.resolve("a.txt")
        val b = saved.resolve("b.txt")
        Files.writeString(a, "CombatLogVersion,4\n")
        Files.writeString(b, "CombatLogVersion,4\n")
        Files.setLastModifiedTime(a, FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(b, FileTime.fromMillis(2_000))

        val selection = CombatLogPaths.selectForImport(null) { if (it == "LOCALAPPDATA") local.toString() else null }
        assertEquals(listOf(b, a), selection.files)
        assertTrue(selection.warnings.any { it.contains("2 log file") })
    }

    @Test
    fun anEmptyDirectoryFailsRatherThanImportingNothing() {
        val dir = Files.createTempDirectory("solisium-empty")
        val error = assertFailsWith<IllegalStateException> { CombatLogPaths.selectForImport(dir.toString()) }
        assertTrue(error.message!!.contains("no combat logs"), "got: ${error.message}")
    }
}
