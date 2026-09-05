package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path

/** Read-only paths under the Throne and Liberty Saved folder. */
object TlLocalPaths {
    fun savedRoot(env: (String) -> String? = { System.getenv(it) }): Path? {
        val local = env("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: return null
        val root = Path.of(local, "TL", "Saved")
        return root.takeIf { Files.isDirectory(it) }
    }

    fun ncStorageLocalData(env: (String) -> String? = { System.getenv(it) }): Path? {
        val root = savedRoot(env) ?: return null
        val file = root.resolve("Config").resolve("WindowsNoEditor").resolve("NCStorageLocalData.ini")
        return file.takeIf { Files.isRegularFile(it) }
    }

    fun eventBoardIni(env: (String) -> String? = { System.getenv(it) }): Path? {
        val root = savedRoot(env) ?: return null
        val file = root.resolve("Config").resolve("WindowsNoEditor").resolve("EventBoard.ini")
        return file.takeIf { Files.isRegularFile(it) }
    }
}
