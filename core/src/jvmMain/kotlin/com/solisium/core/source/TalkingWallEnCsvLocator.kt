package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path

/** TL-Helper collector writes English strings here after extract. */
object TalkingWallEnCsvLocator {
    fun resolve(gameBuild: String?, dataRoot: Path = defaultDataRoot()): Path? {
        val build = gameBuild?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = dataRoot.resolve("raw").resolve(build)
            .resolve("extracted").resolve("localization").resolve("csv").resolve("en.csv")
        return path.takeIf { Files.isRegularFile(it) }
    }

    fun defaultDataRoot(): Path {
        System.getenv("TL_DATA_ROOT")?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
        return Path.of("D:", "TL_Data")
    }
}
