package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves a warehouse `IconPath.assetPath` to a locally extracted image, if the
 * user has one. Solisium never ships decoded game icons; this only reads folders
 * the operator already extracted (`SOLISIUM_ICONS`, `%TL_DATA_ROOT%\icons`,
 * `~/.solisium/icons`).
 */
class WarehouseIconLocator(
    private val env: (String) -> String? = { System.getenv(it) },
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val solisiumHome: Path = Path.of(System.getProperty("user.home"), ".solisium"),
) {
    fun find(iconPath: String?): Path? {
        val raw = iconPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = assetName(raw) ?: return null
        val relative = relativeAsset(raw)
        for (dir in searchDirs()) {
            candidate(dir.resolve(name))?.let { return it }
            if (relative != null) {
                candidate(dir.resolve(relative))?.let { return it }
            }
        }
        return null
    }

    private fun candidate(base: Path): Path? {
        if (hasImageExtension(base.fileName.toString()) && isFile(base)) return base
        EXTENSIONS.forEach { ext ->
            val path = Path.of(base.toString() + ext)
            if (isFile(path)) return path
        }
        return null
    }

    private fun searchDirs(): List<Path> {
        val out = LinkedHashSet<Path>()
        env("SOLISIUM_ICONS")?.takeIf { it.isNotBlank() }?.let { out.add(Path.of(it)) }
        val root = env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() } ?: "D:\\TL_Data"
        out.add(Path.of(root, "icons"))
        out.add(Path.of(root, "warehouse", "icons"))
        out.add(solisiumHome.resolve("icons"))
        return out.toList()
    }

    companion object {
        private val EXTENSIONS = listOf(".png", ".webp", ".jpg", ".jpeg")

        fun assetName(iconPath: String): String? {
            val last = iconPath.trim().replace('\\', '/').substringAfterLast('/')
                .substringBefore('?')
                .trim()
            return last.takeIf { it.isNotEmpty() }
        }

        fun relativeAsset(iconPath: String): String? {
            val trimmed = iconPath.trim().replace('\\', '/').trimStart('/')
            if (trimmed.isEmpty()) return null
            return trimmed.removePrefix("Game/").takeIf { it.isNotEmpty() && it.contains('/') }
        }

        private fun hasImageExtension(name: String): Boolean {
            val lower = name.lowercase()
            return EXTENSIONS.any { lower.endsWith(it) }
        }
    }
}
