package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path

/**
 * Opens TL-Helper's extract in a new console. Solisium does not unpack paks;
 * this only starts the sibling tool the operator already installed.
 */
class TLHelperLauncher(
    private val locator: TLHelperLocator = TLHelperLocator(),
    private val env: (String) -> String? = { System.getenv(it) },
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val start: (LaunchSpec) -> Unit = { spec ->
        ProcessBuilder(spec.command)
            .directory(spec.workingDir.toFile())
            .apply { environment().putAll(spec.environment) }
            .start()
    },
    private val isWindows: Boolean = System.getProperty("os.name").orEmpty()
        .contains("win", ignoreCase = true),
    private val solisiumHome: Path = Path.of(System.getProperty("user.home"), ".solisium"),
    private val progress: TLHelperExtractProgress = TLHelperExtractProgress(env = env, solisiumHome = solisiumHome),
) {
    fun launch(checkout: Path? = locator.find(), buildId: String? = null): Result<Path> {
        val root = checkout?.let { locator.resolveCheckout(it) ?: it.takeIf { locator.isCheckout(it) } }
            ?: return Result.failure(IllegalStateException(MISSING_CHECKOUT))
        if (!locator.isCheckout(root)) {
            return Result.failure(IllegalStateException(MISSING_CHECKOUT))
        }
        val node = resolveNode()
            ?: return Result.failure(IllegalStateException(MISSING_NODE))
        progress.writeMarker("collector", buildId)
        val spec = if (isWindows) {
            val script = writeWindowsScript(root, dataRoot(), node, buildId)
            LaunchSpec(
                listOf("cmd.exe", "/c", startVisibleConsole("\"${script.toAbsolutePath()}\"")),
                root,
                mapOf("TL_DATA_ROOT" to dataRoot().toString()),
            )
        } else {
            spec(root, dataRoot(), node, windows = false)
        }
        return runCatching {
            start(spec)
            root
        }
    }

    internal fun writeWindowsScript(checkout: Path, dataRoot: Path, node: Path, buildId: String?): Path {
        Files.createDirectories(solisiumHome)
        val script = solisiumHome.resolve("run-tl-extract.cmd")
        val nodeArg = if (node.toString().equals("node", ignoreCase = true)) {
            "node"
        } else {
            node.toAbsolutePath().toString()
        }
        val marker = progress.markerFile().toAbsolutePath().toString()
        val update = "scripts\\${TLHelperLocator.UPDATE_SCRIPT}"
        val buildJson = buildId?.let { ",\"build\":\"$it\"" } ?: ""
        fun mark(stage: String, at: String? = null): String {
            val extra = at?.let { ",\"at\":\"$it\"" } ?: ""
            return "> \"$marker\" echo {\"stage\":\"$stage\"$buildJson$extra}"
        }
        val body = """
            @echo off
            setlocal
            set TL_DATA_ROOT=${dataRoot.toAbsolutePath()}
            ${buildId?.let { "set TL_STEAM_BUILD=$it" } ?: "rem TL_STEAM_BUILD from Steam"}
            set TL_EXTRACT_ROOT=${dataRoot.toAbsolutePath()}\\raw\\${buildId ?: "%TL_STEAM_BUILD%"}\\extracted
            cd /d "${checkout.toAbsolutePath()}"
            echo Collector, then decode, then warehouse.
            ${mark("collector")}
            "$nodeArg" $update --only collector
            if errorlevel 1 (
              ${mark("failed", "collector")}
              echo Collector failed.
              exit /b 1
            )
            ${mark("decode")}
            "$nodeArg" $update --only decode
            if errorlevel 1 (
              ${mark("failed", "decode")}
              echo Decode failed.
              exit /b 1
            )
            ${mark("warehouse")}
            "$nodeArg" scripts\prepare-warehouse-inputs.mjs
            if errorlevel 1 (
              ${mark("failed", "warehouse")}
              echo Warehouse input prepare failed.
              exit /b 1
            )
            "$nodeArg" $update --only warehouse
            if errorlevel 1 (
              ${mark("failed", "warehouse")}
              echo Warehouse failed.
              exit /b 1
            )
            ${mark("done")}
            echo Extract finished.
        """.trimIndent()
        Files.writeString(script, body + "\n")
        return script
    }

    fun dataRoot(): Path =
        env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of("D:", "TL_Data")

    fun resolveNode(): Path? {
        env("SOLISIUM_NODE")?.takeIf { it.isNotBlank() }?.let { Path.of(it).takeIf(isFile) }?.let { return it }
        env("TL_NODE")?.takeIf { it.isNotBlank() }?.let { Path.of(it).takeIf(isFile) }?.let { return it }
        NODE_CANDIDATES.firstOrNull(isFile)?.let { return it }
        return Path.of("node")
    }

    data class LaunchSpec(
        val command: List<String>,
        val workingDir: Path,
        val environment: Map<String, String>,
    )

    companion object {
        const val MISSING_CHECKOUT =
            "TL-Helper was not found. Download it from ${TLHelperLocator.CHECKOUT_URL}, " +
                "then pick the folder that contains scripts\\update-tl-helper.mjs. " +
                "Extract also needs Node.js and the .NET SDK. A key is found with Data → Find my key " +
                "if you already have source-manifest.json or aes.txt on this PC."
        const val MISSING_NODE =
            "Node.js was not found. Install it, or set SOLISIUM_NODE to node.exe."

        private val NODE_CANDIDATES = listOf(
            Path.of("C:", "Program Files", "nodejs", "node.exe"),
            Path.of("C:", "Program Files (x86)", "nodejs", "node.exe"),
        )

        /**
         * `start` must get a quoted window title. An unquoted first token is the
         * program name, which is why `start TL-Helper` made Windows report it
         * could not be found. A GUI jpackage app also has no console, so
         * `cmd /k` alone stays hidden.
         */
        fun startVisibleConsole(keepOpenCommand: String): String =
            "start \"$WINDOW_TITLE\" cmd.exe /k $keepOpenCommand"

        fun spec(checkout: Path, dataRoot: Path, node: Path, windows: Boolean): LaunchSpec {
            val nodeArg = if (node.toString().equals("node", ignoreCase = true)) {
                "node"
            } else {
                node.toAbsolutePath().toString()
            }
            val quotedNode = if (nodeArg.contains(' ')) "\"$nodeArg\"" else nodeArg
            val script = "scripts\\${TLHelperLocator.UPDATE_SCRIPT}"
            // Full pipeline preflight-fails on a new Steam build because decode/warehouse
            // inputs do not exist yet. Collector has to run first.
            val pipeline = "$quotedNode $script --only collector" +
                " && $quotedNode $script --only decode" +
                " && $quotedNode scripts\\prepare-warehouse-inputs.mjs" +
                " && $quotedNode $script --only warehouse"
            val command = if (windows) {
                listOf("cmd.exe", "/c", startVisibleConsole("\"$pipeline\""))
            } else {
                listOf(
                    "sh", "-lc",
                    "$nodeArg scripts/${TLHelperLocator.UPDATE_SCRIPT} --only collector" +
                        " && $nodeArg scripts/${TLHelperLocator.UPDATE_SCRIPT} --only decode" +
                        " && $nodeArg scripts/${TLHelperLocator.UPDATE_SCRIPT} --only warehouse",
                )
            }
            return LaunchSpec(
                command = command,
                workingDir = checkout.toAbsolutePath(),
                environment = mapOf("TL_DATA_ROOT" to dataRoot.toAbsolutePath().toString()),
            )
        }

        const val WINDOW_TITLE = "Solisium extract"
    }
}
