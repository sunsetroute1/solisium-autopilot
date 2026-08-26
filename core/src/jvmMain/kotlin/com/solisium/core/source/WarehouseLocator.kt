package com.solisium.core.source

import com.solisium.core.bootstrap.InstallResources
import com.solisium.core.bootstrap.StarterWarehouse
import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds a local TL-Helper warehouse without assuming one exists.
 * Default research path: `%TL_DATA_ROOT%\warehouse\tl-<build>.sqlite` or `D:\TL_Data\warehouse`.
 */
class WarehouseLocator(
    private val env: (String) -> String? = { System.getenv(it) },
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val listSqlite: (Path) -> List<Path> = { dir ->
        if (!Files.isDirectory(dir)) {
            emptyList()
        } else {
            Files.list(dir).use { stream ->
                stream.toList().filter { path ->
                    val name = path.fileName.toString()
                    Files.isRegularFile(path) && name.startsWith("tl-") && name.endsWith(".sqlite")
                }
            }
        }
    },
    private val fileMeta: (Path) -> Pair<Long, Long> = { path ->
        Files.getLastModifiedTime(path).toMillis() to Files.size(path)
    },
) {
    fun find(): Path? {
        env("SOLISIUM_TL_WAREHOUSE")?.takeIf { it.isNotBlank() }?.let { explicit ->
            val path = Path.of(explicit)
            if (isFile(path)) return path
        }
        return list().maxByOrNull { it.lastModifiedMillis }?.path
    }

    fun findForBuild(buildId: String): WarehouseRef? =
        list().filter { it.buildId == buildId }.maxByOrNull { it.lastModifiedMillis }

    fun list(): List<WarehouseRef> {
        val out = LinkedHashMap<String, WarehouseRef>()
        env("SOLISIUM_TL_WAREHOUSE")?.takeIf { it.isNotBlank() }?.let { explicit ->
            val path = Path.of(explicit)
            if (isFile(path)) out[path.toAbsolutePath().toString()] = toRef(path)
        }
        val root = env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() } ?: "D:\\TL_Data"
        listSqlite(Path.of(root, "warehouse")).forEach { path ->
            if (isFile(path)) out[path.toAbsolutePath().toString()] = toRef(path)
        }
        InstallResources.starter("tl-starter.sqlite")?.let { bundled ->
            if (isFile(bundled)) out[bundled.toAbsolutePath().toString()] = toRef(bundled)
        }
        return out.values.toList()
    }

    fun describe(): String {
        val found = find()
        return if (found != null) {
            "warehouse found at $found"
        } else {
            "no warehouse at SOLISIUM_TL_WAREHOUSE or %TL_DATA_ROOT%\\warehouse\\tl-*.sqlite (default D:\\TL_Data\\warehouse)"
        }
    }

    private fun toRef(path: Path): WarehouseRef {
        val meta = runCatching { fileMeta(path) }.getOrDefault(0L to 0L)
        val fileName = path.fileName.toString()
        val buildId = parseBuildId(fileName)
            ?: if (fileName.equals("tl-starter.sqlite", ignoreCase = true)) StarterWarehouse.BUILD_ID else null
        return WarehouseRef(
            path = path,
            buildId = buildId,
            lastModifiedMillis = meta.first,
            sizeBytes = meta.second,
        )
    }

    companion object {
        fun parseBuildId(fileName: String): String? {
            val match = Regex("""tl-(\d+)\.sqlite""", RegexOption.IGNORE_CASE).find(fileName)
            return match?.groupValues?.get(1)
        }
    }
}
