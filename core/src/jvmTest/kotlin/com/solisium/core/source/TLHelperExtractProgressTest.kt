package com.solisium.core.source

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TLHelperExtractProgressTest {
    @Test
    fun decodePercentTracksJsonTablesAgainstThePreviousBuild() {
        val data = Files.createTempDirectory("solisium-extract-data")
        val home = Files.createTempDirectory("solisium-extract-home")
        val prev = data.resolve("decoded").resolve("111").resolve("tables")
        Files.createDirectories(prev)
        repeat(10) { Files.writeString(prev.resolve("T$it.json"), "{}") }
        val current = data.resolve("decoded").resolve("222").resolve("tables")
        Files.createDirectories(current)
        Files.writeString(current.resolve("A.json"), "{}")
        Files.writeString(current.resolve("B.json"), "{}")
        Files.writeString(current.resolve("C.json"), "{}")
        val monitor = TLHelperExtractProgress(
            env = { if (it == "TL_DATA_ROOT") data.toString() else null },
            solisiumHome = home,
        )
        val snap = requireNotNull(monitor.inspect("222"))
        assertEquals("decode", snap.activeStage)
        val decode = snap.stages.first { it.name == "decode" }
        assertEquals(30, decode.percent)
        assertTrue(snap.overallPercent in 1..80)
    }

    @Test
    fun bomPrefixedFailedMarkerIsStillRead() {
        val data = Files.createTempDirectory("solisium-extract-bom")
        val home = Files.createTempDirectory("solisium-extract-bom-home")
        Files.writeString(
            home.resolve("tl-extract.json"),
            "\uFEFF{\"stage\":\"failed\",\"build\":\"222\",\"at\":\"decode\"}",
        )
        val monitor = TLHelperExtractProgress(
            env = { if (it == "TL_DATA_ROOT") data.toString() else null },
            solisiumHome = home,
        )
        val snap = requireNotNull(monitor.inspect("222"))
        assertTrue(snap.failed)
        assertEquals("decode", snap.activeStage)
        assertTrue(snap.label.contains("failed", ignoreCase = true))
    }

    @Test
    fun warehouseFileCompletesTheBar() {
        val data = Files.createTempDirectory("solisium-extract-wh")
        val home = Files.createTempDirectory("solisium-extract-home")
        Files.createDirectories(data.resolve("warehouse"))
        Files.write(data.resolve("warehouse").resolve("tl-old.sqlite"), ByteArray(100))
        Files.write(data.resolve("warehouse").resolve("tl-222.sqlite"), ByteArray(90))
        val monitor = TLHelperExtractProgress(
            env = { if (it == "TL_DATA_ROOT") data.toString() else null },
            solisiumHome = home,
        )
        val snap = requireNotNull(monitor.inspect("222"))
        assertEquals(100, snap.stages.first { it.name == "warehouse" }.percent)
        assertEquals(100, snap.overallPercent)
    }
}
