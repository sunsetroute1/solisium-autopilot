package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CombatLogPathsTest {
    @Test
    fun listsTxtFilesNewestFirstAndIgnoresSav() {
        val dir = Files.createTempDirectory("solisium-logs")
        val older = dir.resolve("older.txt")
        val newer = dir.resolve("newer.txt")
        val sav = dir.resolve("not-a-log.sav")
        Files.writeString(older, "old")
        Files.writeString(newer, "new")
        Files.writeString(sav, "ignored")
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000))
        val listed = CombatLogPaths.listLogFiles(dir)
        assertEquals(listOf(newer, older), listed)
        assertEquals(newer, CombatLogPaths.newestLog(dir))
        assertTrue(listed.none { it.fileName.toString().endsWith(".sav") })
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
        Files.writeString(a, "a")
        Files.writeString(b, "b")
        Files.setLastModifiedTime(a, FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(b, FileTime.fromMillis(2_000))

        val fromDir = CombatLogPaths.selectForImport(dir.toString())
        assertEquals(listOf(b, a), fromDir.files)
        assertTrue(fromDir.warnings.isEmpty(), "an explicit directory imports everything, so nothing is skipped")

        val fromFile = CombatLogPaths.selectForImport(a.toString())
        assertEquals(listOf(a), fromFile.files)
        assertTrue(fromFile.warnings.isEmpty())
    }

    @Test
    fun selectingAMissingPathFails() {
        val missing = Files.createTempDirectory("solisium-missing").resolve("nope.txt")
        val error = assertFailsWith<IllegalStateException> { CombatLogPaths.selectForImport(missing.toString()) }
        assertTrue(error.message!!.contains("path not found"), "got: ${error.message}")
    }

    @Test
    fun anEmptyDirectoryFailsRatherThanImportingNothing() {
        val dir = Files.createTempDirectory("solisium-empty")
        val error = assertFailsWith<IllegalStateException> { CombatLogPaths.selectForImport(dir.toString()) }
        assertTrue(error.message!!.contains("no .txt files"), "got: ${error.message}")
    }
}
