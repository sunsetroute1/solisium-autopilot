package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds a local TL-Helper checkout so Solisium can launch its extract.
 * Never vendors TL-Helper; this only looks at folders the operator already has.
 */
class TLHelperLocator(
    private val env: (String) -> String? = { System.getenv(it) },
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val solisiumHome: Path = Path.of(System.getProperty("user.home"), ".solisium"),
    private val userHome: Path? = System.getProperty("user.home")?.let { Path.of(it) },
) {
    fun find(): Path? = candidates().firstNotNullOfOrNull { resolveCheckout(it) }

    fun isCheckout(path: Path): Boolean = isFile(updateScript(path))

    fun updateScript(checkout: Path): Path =
        checkout.resolve("scripts").resolve(UPDATE_SCRIPT)

    fun remember(path: Path) {
        runCatching {
            Files.createDirectories(solisiumHome)
            Files.writeString(rememberFile(), path.toAbsolutePath().normalize().toString())
        }
    }

    fun candidates(): List<Path> {
        val out = LinkedHashSet<Path>()
        env("SOLISIUM_TL_HELPER")?.takeIf { it.isNotBlank() }?.let { out.add(Path.of(it)) }
        env("TL_HELPER_ROOT")?.takeIf { it.isNotBlank() }?.let { out.add(Path.of(it)) }
        readRemembered()?.let { out.add(it) }
        out.add(Path.of("D:", "TL_Helper"))
        userHome?.let { home ->
            out.add(home.resolve("projects").resolve("tl-helper"))
            out.add(home.resolve("projects").resolve("TL_Helper"))
            out.add(home.resolve("tl-helper"))
        }
        return out.toList()
    }

    internal fun resolveCheckout(path: Path): Path? {
        if (isFile(path) && path.fileName.toString().equals(UPDATE_SCRIPT, ignoreCase = true)) {
            return path.parent?.parent?.takeIf { isCheckout(it) }
        }
        return path.takeIf { isCheckout(it) }
    }

    private fun rememberFile(): Path = solisiumHome.resolve("tl-helper-root.txt")

    private fun readRemembered(): Path? {
        val file = rememberFile()
        if (!isFile(file)) return null
        val raw = runCatching { Files.readString(file).trim() }.getOrNull()
        if (raw.isNullOrBlank()) return null
        return Path.of(raw)
    }

    companion object {
        const val UPDATE_SCRIPT = "update-tl-helper.mjs"
        const val CHECKOUT_URL = "https://github.com/sunsetroute1/tl-helper"
    }
}
