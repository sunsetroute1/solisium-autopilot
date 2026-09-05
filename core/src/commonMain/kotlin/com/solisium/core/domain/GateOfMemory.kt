package com.solisium.core.domain

/**
 * Gate of Memory (Talking Wall / Earth's Memory) schedule row.
 * Cadence matches [MetaForge's community timer](https://metaforge.app/throne-and-liberty/timer):
 * ~3h 17m between openings, ~4 minutes active.
 */
data class GateOfMemoryWindow(
    val startsAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val active: Boolean,
    val startsInMs: Long,
)

data class GateOfMemoryPlan(
    val region: GateOfMemoryRegion,
    val zoneLabel: String,
    val activeNow: Boolean,
    /** When active, ms until the window closes; otherwise ms until the next opening. */
    val countdownMs: Long,
    val nextStartEpochMs: Long,
    val nextEndEpochMs: Long,
    val nextStartLabel: String,
    val upcoming: List<GateOfMemoryWindow>,
    val notes: List<String>,
)

enum class GateOfMemoryRegion(
    val id: String,
    val label: String,
    val zoneId: String,
) {
    NA("na", "NA", "America/Denver"),
    EU("eu", "EU", "Europe/Berlin"),
    ASIA("asia", "Asia", "Asia/Seoul"),
    ;

    companion object {
        fun fromId(raw: String?): GateOfMemoryRegion? =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) }

        fun fromGameServerRegion(region: String?): GateOfMemoryRegion = when (region?.lowercase()) {
            "eu", "sa" -> EU
            "kr", "jp", "sea" -> ASIA
            else -> NA
        }
    }
}
