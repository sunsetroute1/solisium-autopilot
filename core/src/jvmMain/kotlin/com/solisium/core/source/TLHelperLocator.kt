package com.solisium.core.source

import com.solisium.core.bootstrap.InstallResources
import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds a local TL-Helper checkout so Solisium can launch its extract.
 *
 * Looks at folders the operator already has, the copy the installer placed
 * under `%LOCALAPPDATA%\Programs\TL-Helper`, the checkout bundled in the app
 * image, and `vendor/tl-helper` in a source tree.
 */
class TLHelperLocator(
    private val env: (String) -> String? = { System.getenv(it) },
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val solisiumHome: Path = Path.of(System.getProperty("user.home"), ".solisium"),
    private val userHome: Path? = System.getProperty("user.home")?.let { Path.of(it) },
    private val localAppData: Path? = System.getenv("LOCALAPPDATA")
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it) },
    private val bundledCheckouts: List<Path> = listOfNotNull(InstallResources.tlHelper()),
    private val workingDir: Path = Path.of("").toAbsolutePath().normalize(),
    private val wellKnownRoots: List<Path>? = null,
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

    fun solisiumHome(): Path = solisiumHome

    fun defaultInstallRoot(): Path =
        localAppData?.resolve("Programs")?.resolve(INSTALL_DIR_NAME)
            ?: solisiumHome.resolve(INSTALL_DIR_NAME)

    fun vendorCandidates(): List<Path> = listOf(
        workingDir.resolve("vendor").resolve("tl-helper"),
        workingDir.resolve("..").resolve("vendor").resolve("tl-helper").normalize(),
    )

    fun candidates(): List<Path> {
        val out = LinkedHashSet<Path>()
        env("SOLISIUM_TL_HELPER")?.takeIf { it.isNotBlank() }?.let { out.add(Path.of(it)) }
        env("TL_HELPER_ROOT")?.takeIf { it.isNotBlank() }?.let { out.add(Path.of(it)) }
        readRemembered()?.let { out.add(it) }
        out.add(defaultInstallRoot())
        out.addAll(bundledCheckouts)
        out.addAll(vendorCandidates())
        out.addAll(wellKnownRoots ?: defaultWellKnownRoots())
        return out.toList()
    }

    internal fun resolveCheckout(path: Path): Path? {
        if (isFile(path) && path.fileName.toString().equals(UPDATE_SCRIPT, ignoreCase = true)) {
            return path.parent?.parent?.takeIf { isCheckout(it) }
        }
        return path.takeIf { isCheckout(it) }
    }

    private fun defaultWellKnownRoots(): List<Path> {
        val out = mutableListOf(Path.of("D:", "TL_Helper"))
        userHome?.let { home ->
            out.add(home.resolve("projects").resolve("tl-helper"))
            out.add(home.resolve("projects").resolve("TL_Helper"))
            out.add(home.resolve("tl-helper"))
        }
        return out
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
        const val INSTALL_DIR_NAME = "TL-Helper"
    }
}
