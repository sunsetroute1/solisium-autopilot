package com.solisium.core.source

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
) {
    fun find(): Path? {
        env("SOLISIUM_TL_WAREHOUSE")?.takeIf { it.isNotBlank() }?.let { explicit ->
            val path = Path.of(explicit)
            if (isFile(path)) return path
        }
        val root = env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() } ?: "D:\\TL_Data"
        val candidates = listSqlite(Path.of(root, "warehouse"))
        return candidates.maxByOrNull { Files.getLastModifiedTime(it).toMillis() }
    }

    fun describe(): String {
        val found = find()
        return if (found != null) {
            "warehouse found at $found"
        } else {
            "no warehouse at SOLISIUM_TL_WAREHOUSE or %TL_DATA_ROOT%\\warehouse\\tl-*.sqlite (default D:\\TL_Data\\warehouse)"
        }
    }
}
