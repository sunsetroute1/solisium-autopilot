package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds already-extracted English locres. Solisium never unpacks paks.
 */
class LocresLocator(
    private val env: (String) -> String? = { System.getenv(it) },
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val listGameLocres: (Path) -> List<Path> = { rawRoot ->
        if (!Files.isDirectory(rawRoot)) {
            emptyList()
        } else {
            Files.list(rawRoot).use { builds ->
                builds.toList().mapNotNull { buildDir ->
                    val locres = buildDir.resolve("collector").resolve("localization").resolve("en").resolve("Game.locres")
                    locres.takeIf { Files.isRegularFile(it) }
                }
            }
        }
    },
    private val lastModified: (Path) -> Long = { Files.getLastModifiedTime(it).toMillis() },
) {
    fun find(gameBuild: String? = null): Path? {
        env("SOLISIUM_LOCRES")?.takeIf { it.isNotBlank() }?.let { explicit ->
            val path = Path.of(explicit)
            if (isFile(path)) return path
        }
        val root = Path.of(env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() } ?: "D:\\TL_Data")
        val build = gameBuild?.trim().orEmpty()
        if (build.isNotEmpty()) {
            val forBuild = root.resolve("raw").resolve(build)
                .resolve("collector").resolve("localization").resolve("en").resolve("Game.locres")
            if (isFile(forBuild)) return forBuild
        }
        val cached = root.resolve("cache").resolve("game-locres.json")
        if (isFile(cached)) return cached
        return listGameLocres(root.resolve("raw")).maxByOrNull { lastModified(it) }
    }
}
