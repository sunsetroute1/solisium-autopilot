package com.solisium.core.domain

/** One row inferred from local game files or pasted UI text. */
data class LiveProgressionItem(
    val id: String,
    val title: String,
    val detail: String,
    val completed: Boolean,
    val progress: String? = null,
    val source: LiveProgressionSource,
    val confidence: String,
)

enum class LiveProgressionSource(val label: String) {
    LocalConfig("Game config"),
    Paste("Clipboard paste"),
    CombatLog("Combat log"),
}

data class LiveProgressionSnapshot(
    val items: List<LiveProgressionItem>,
    val completedIds: Set<String>,
    val sources: List<String>,
    val warnings: List<String>,
    val syncedAtEpochMs: Long,
) {
    val itemCount: Int get() = items.size
}
