package com.solisium.core.bootstrap

import java.nio.file.Files
import java.nio.file.Path

/** Compose Desktop app resources shipped beside the installer (see appResourcesRootDir). */
object InstallResources {
    fun root(): Path? {
        System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let { raw ->
            return Path.of(raw).takeIf { Files.isDirectory(it) }
        }
        val cwd = Path.of("").toAbsolutePath().normalize()
        return sequenceOf(
            cwd.resolve("desktopApp/appResources/windows"),
            cwd.resolve("appResources/windows"),
            cwd.resolve("appResources/common"),
        ).firstOrNull { Files.isDirectory(it) }
    }

    fun starter(name: String): Path? =
        root()?.resolve("starter")?.resolve(name)?.takeIf { Files.isRegularFile(it) }

    /** Bundled extract checkout copied into the app image from `vendor/tl-helper`. */
    fun tlHelper(): Path? {
        val dir = root()?.resolve("tl-helper") ?: return null
        val script = dir.resolve("scripts").resolve("update-tl-helper.mjs")
        return dir.takeIf { Files.isRegularFile(script) }
    }
}
