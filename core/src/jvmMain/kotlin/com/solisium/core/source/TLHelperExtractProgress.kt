package com.solisium.core.source

import com.solisium.core.json.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * Honest extract progress from artifacts on disk plus an optional stage marker
 * Solisium writes while collector → decode → warehouse runs.
 */
class TLHelperExtractProgress(
    private val env: (String) -> String? = { System.getenv(it) },
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val isDir: (Path) -> Boolean = { Files.isDirectory(it) },
    private val readText: (Path) -> String = { Files.readString(it) },
    private val fileSize: (Path) -> Long = { Files.size(it) },
    private val listFiles: (Path) -> List<Path> = { dir ->
        if (!Files.isDirectory(dir)) {
            emptyList()
        } else {
            Files.walk(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.limit(4_000).toList()
            }
        }
    },
    private val solisiumHome: Path = Path.of(System.getProperty("user.home"), ".solisium"),
) {
    fun inspect(buildId: String?): Snapshot? {
        val build = buildId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val root = dataRoot()
        val marker = readMarker()
        val rawDir = root.resolve("raw").resolve(build)
        val decodedDir = root.resolve("decoded").resolve(build).resolve("tables")
        val manifest = root.resolve("manifests").resolve(build).resolve("manifest.json")
        val warehouse = root.resolve("warehouse").resolve("tl-$build.sqlite")

        val expectedRaw = expectedRawScale(root, build)
        val expectedDecoded = expectedDecodedScale(root, build)
        val expectedWarehouse = expectedWarehouseScale(root, build)

        val rawFiles = if (isDir(rawDir)) listFiles(rawDir) else emptyList()
        val rawBytes = rawFiles.sumOf { runCatching { fileSize(it) }.getOrDefault(0L) }
        val decodedFiles = if (isDir(decodedDir)) {
            listFiles(decodedDir).count { it.fileName.toString().endsWith(".json", ignoreCase = true) }
        } else {
            0
        }
        val warehouseBytes = if (isFile(warehouse)) runCatching { fileSize(warehouse) }.getOrDefault(0L) else 0L
        val collectorDone = isFile(manifest)
        val decodeDone = decodedFiles > 0 && decodedFiles >= expectedDecoded
        val warehouseDone = warehouseBytes > 0L && warehouseBytes >= (expectedWarehouse * 8 / 10)

        val collectorPct = when {
            warehouseDone || collectorDone -> 100
            rawFiles.isEmpty() -> if (marker?.stage == "collector") 2 else 0
            else -> ((rawBytes * 100) / expectedRaw.coerceAtLeast(1)).toInt().coerceIn(3, 98)
        }
        val decodePct = when {
            warehouseDone || decodeDone -> 100
            decodedFiles == 0 -> if (marker?.stage == "decode") 2 else 0
            else -> ((decodedFiles * 100) / expectedDecoded.coerceAtLeast(1)).coerceIn(3, 98)
        }
        val warehousePct = when {
            warehouseDone -> 100
            warehouseBytes == 0L -> if (marker?.stage == "warehouse") 2 else 0
            else -> ((warehouseBytes * 100) / expectedWarehouse.coerceAtLeast(1)).toInt().coerceIn(3, 98)
        }

        val failed = marker?.stage == "failed"
        val active = when {
            failed -> marker.at ?: "failed"
            warehousePct > 0 || marker?.stage == "warehouse" -> "warehouse"
            decodePct > 0 || collectorDone || marker?.stage == "decode" -> "decode"
            collectorPct > 0 || marker?.stage == "collector" -> "collector"
            else -> marker?.stage ?: "collector"
        }
        val overall = (collectorPct * COLLECTOR_WEIGHT + decodePct * DECODE_WEIGHT + warehousePct * WAREHOUSE_WEIGHT) / 100
        return Snapshot(
            buildId = build,
            activeStage = active,
            overallPercent = overall.coerceIn(0, 100),
            failed = failed,
            stages = listOf(
                Stage("collector", collectorPct, if (rawFiles.isEmpty()) null else "${rawFiles.size} files"),
                Stage("decode", decodePct, if (decodedFiles == 0) null else "$decodedFiles / $expectedDecoded tables"),
                Stage("warehouse", warehousePct, if (warehouseBytes == 0L) null else "${warehouseBytes / 1_000_000} MB"),
            ),
        )
    }

    fun writeMarker(stage: String, buildId: String?, at: String? = null) {
        runCatching {
            Files.createDirectories(solisiumHome)
            val build = buildId?.let { "\"build\":\"$it\"," } ?: ""
            val failedAt = at?.let { "\"at\":\"$it\"," } ?: ""
            Files.writeString(markerFile(), """{$build$failedAt"stage":"$stage"}""")
        }
    }

    fun clearMarker() {
        runCatching { Files.deleteIfExists(markerFile()) }
    }

    fun dataRoot(): Path =
        env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of("D:", "TL_Data")

    fun markerFile(): Path = solisiumHome.resolve("tl-extract.json")

    private fun readMarker(): Marker? {
        val file = markerFile()
        if (!isFile(file)) return null
        val root = runCatching { JsonParser.parse(readText(file)) }.getOrNull() ?: return null
        val stage = root.str("stage") ?: return null
        return Marker(stage = stage, build = root.str("build"), at = root.str("at"))
    }

    private fun expectedRawScale(root: Path, build: String): Long {
        siblingBuilds(root.resolve("raw"), build).forEach { other ->
            val files = listFiles(other)
            val bytes = files.sumOf { runCatching { fileSize(it) }.getOrDefault(0L) }
            if (bytes > 0L) return bytes
        }
        return DEFAULT_RAW_BYTES
    }

    private fun expectedDecodedScale(root: Path, build: String): Int {
        siblingBuilds(root.resolve("decoded"), build).forEach { other ->
            val tables = other.resolve("tables")
            val count = if (isDir(tables)) {
                listFiles(tables).count { it.fileName.toString().endsWith(".json", ignoreCase = true) }
            } else {
                0
            }
            if (count > 0) return count
        }
        return DEFAULT_DECODED_TABLES
    }

    private fun expectedWarehouseScale(root: Path, build: String): Long {
        val dir = root.resolve("warehouse")
        if (isDir(dir)) {
            listFiles(dir)
                .filter { it.fileName.toString().startsWith("tl-") && it.fileName.toString().endsWith(".sqlite") }
                .filter { !it.fileName.toString().contains(build) }
                .maxOfOrNull { runCatching { fileSize(it) }.getOrDefault(0L) }
                ?.takeIf { it > 0L }
                ?.let { return it }
        }
        return DEFAULT_WAREHOUSE_BYTES
    }

    private fun siblingBuilds(parent: Path, current: String): List<Path> {
        if (!isDir(parent)) return emptyList()
        return runCatching {
            Files.list(parent).use { stream ->
                stream.filter { isDir(it) && it.fileName.toString() != current }.toList()
            }
        }.getOrDefault(emptyList())
    }

    data class Snapshot(
        val buildId: String,
        val activeStage: String,
        val overallPercent: Int,
        val failed: Boolean,
        val stages: List<Stage>,
    ) {
        val label: String
            get() = if (failed) {
                "Extract failed at $activeStage"
            } else {
                "${activeStage.replaceFirstChar { it.uppercase() }} · $overallPercent%"
            }
    }

    data class Stage(val name: String, val percent: Int, val counts: String?)

    private data class Marker(val stage: String, val build: String?, val at: String?)

    companion object {
        const val COLLECTOR_WEIGHT = 50
        const val DECODE_WEIGHT = 30
        const val WAREHOUSE_WEIGHT = 20
        const val DEFAULT_DECODED_TABLES = 30
        const val DEFAULT_RAW_BYTES = 200_000_000L
        const val DEFAULT_WAREHOUSE_BYTES = 250_000_000L
    }
}
