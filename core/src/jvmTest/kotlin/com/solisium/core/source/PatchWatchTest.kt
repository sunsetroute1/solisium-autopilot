package com.solisium.core.source

import com.solisium.core.domain.DatasetSnapshot
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PatchWatchTest {
    @Test
    fun matchingWarehouseHashIsCurrent() {
        val state = PatchWatch.decide(
            install = install("24829515"),
            warehouse = warehouse("24829515", "aaa"),
            active = snapshot("24829515", "aaa"),
            pakChanged = false,
        )
        assertEquals(PatchWatchState.CURRENT, state)
    }

    @Test
    fun newWarehouseHashIsImportReady() {
        val state = PatchWatch.decide(
            install = install("24829515"),
            warehouse = warehouse("24829515", "bbb"),
            active = snapshot("24829515", "aaa"),
            pakChanged = false,
        )
        assertEquals(PatchWatchState.IMPORT_READY, state)
    }

    @Test
    fun patchedGameWithoutMatchingWarehouseWaits() {
        val state = PatchWatch.decide(
            install = install("25999999"),
            warehouse = null,
            active = snapshot("24829515", "aaa"),
            pakChanged = false,
        )
        assertEquals(PatchWatchState.WAITING_FOR_WAREHOUSE, state)
    }

    @Test
    fun doesNotImportAnOlderWarehouseOverTheInstalledBuild() {
        val state = PatchWatch.decide(
            install = install("25999999"),
            warehouse = warehouse("24118850", "old"),
            active = snapshot("24829515", "aaa"),
            pakChanged = false,
        )
        assertEquals(PatchWatchState.WAITING_FOR_WAREHOUSE, state)
    }

    @Test
    fun pickPrefersTheInstalledBuildFile() {
        val picked = PatchWatch.pickWarehouse(
            "24829515",
            listOf(
                warehouse("24118850", "old", 9),
                warehouse("24829515", "new", 1),
            ),
        )
        assertEquals("24829515", picked?.buildId)
    }

    @Test
    fun warehouseBuildIdParsesFromFilename() {
        assertEquals("24829515", WarehouseLocator.parseBuildId("tl-24829515.sqlite"))
        assertNull(WarehouseLocator.parseBuildId("notes.txt"))
    }

    private fun install(build: String) = InstalledGameDataSource.DetectedInstall(
        installPath = Path.of("D:", "game"),
        buildId = build,
        pakFingerprint = "pak",
    )

    private fun warehouse(build: String, hash: String, mtime: Long = 1) = WarehouseRef(
        path = Path.of("D:", "TL_Data", "warehouse", "tl-$build.sqlite"),
        buildId = build,
        lastModifiedMillis = mtime,
        sizeBytes = 10,
        sha256 = hash,
    )

    private fun snapshot(build: String, hash: String) = DatasetSnapshot(
        id = "snap",
        source = "tl_helper",
        extractedAt = "2026-08-25T00:00:00Z",
        gameBuild = build,
        gameVersion = "unknown",
        schemaVersion = 8,
        sourcePath = "D:\\TL_Data\\warehouse\\tl-$build.sqlite",
        sourceHash = hash,
        decoderVersion = "0.2.0",
        active = true,
    )
}
