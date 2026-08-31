package com.solisium.core.source

import com.solisium.core.json.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads TL-Helper's last update-run report. Solisium does not invent extract
 * success; a missing warehouse stays a wait state.
 */
class TLHelperRunLog(
    private val env: (String) -> String? = { System.getenv(it) },
    private val isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val readText: (Path) -> String = { Files.readString(it) },
) {
    fun latest(buildId: String?): TLHelperRunStatus? {
        val build = buildId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = dataRoot().resolve("reports").resolve(build).resolve("update-runs").resolve("latest.json")
        if (!isFile(path)) return null
        return parse(readText(path))
    }

    fun dataRoot(): Path =
        env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of("D:", "TL_Data")

    companion object {
        fun parse(text: String): TLHelperRunStatus? {
            val root = runCatching { JsonParser.parse(text) }.getOrNull() ?: return null
            val status = root.str("status") ?: return null
            val failed = root.arr("preflight")
                .mapNotNull { check ->
                    if (check.bool("ok") == false) {
                        val name = check.str("name") ?: "check"
                        val detail = check.str("detail") ?: return@mapNotNull name
                        "$name: $detail"
                    } else {
                        null
                    }
                }
            val failedStage = root.arr("stages")
                .firstOrNull { it.str("status")?.contains("fail", ignoreCase = true) == true }
                ?.str("name")
            return TLHelperRunStatus(
                gameBuild = root.str("gameBuild"),
                status = status,
                finishedAtUtc = root.str("finishedAtUtc"),
                failedStage = failedStage,
                failedChecks = failed,
            )
        }
    }
}

data class TLHelperRunStatus(
    val gameBuild: String?,
    val status: String,
    val finishedAtUtc: String?,
    val failedStage: String? = null,
    val failedChecks: List<String> = emptyList(),
) {
    val succeeded: Boolean get() = status == "ok" || status == "succeeded" || status == "completed"

    fun summary(): String = when {
        succeeded -> "Last TL-Helper run finished for build ${gameBuild ?: "unknown"}."
        status == "preflight-failed" && failedChecks.any { it.startsWith("decode:input") } ->
            "Last TL-Helper decode preflight failed for build ${gameBuild ?: "unknown"}. " +
                "The unpacked Table folder is missing, so decode never started and there is no new warehouse."
        status == "preflight-failed" && failedChecks.any { it.startsWith("warehouse:input") } ->
            "Last TL-Helper warehouse preflight failed for build ${gameBuild ?: "unknown"}. " +
                "Decoded tables are missing, so there is no new warehouse yet."
        failedStage != null ->
            "Last TL-Helper run failed at $failedStage for build ${gameBuild ?: "unknown"}."
        else ->
            "Last TL-Helper run status: $status" + (gameBuild?.let { " (build $it)" } ?: "") + "."
    }
}
