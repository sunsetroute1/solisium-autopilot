package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
