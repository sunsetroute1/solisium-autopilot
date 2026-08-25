package com.solisium.core.query

/**
 * Installed Steam build vs imported snapshot build. Does not assume they match.
 */
object BuildMismatch {
    fun warning(installedBuild: String?, snapshotBuild: String?): String? {
        if (installedBuild.isNullOrBlank() || snapshotBuild.isNullOrBlank()) return null
        if (snapshotBuild == "unknown") return null
        if (installedBuild == snapshotBuild) return null
        return "installed Steam build $installedBuild does not match snapshot build $snapshotBuild"
    }
}
