package com.solisium.core.meta

import com.solisium.core.domain.GameServer

/**
 * Community server list with IANA zones so the timetable can be server-selectable.
 * Names and merges change; this is not extracted from the client.
 */
object GameServers {
    val all: List<GameServer> = listOf(
        server("na-east-adentus", "Adentus", "na-east", "NA East", "America/New_York"),
        server("na-east-junobote", "Junobote", "na-east", "NA East", "America/New_York"),
        server("na-east-generic", "NA East (any)", "na-east", "NA East", "America/New_York"),
        server("na-west-generic", "NA West (any)", "na-west", "NA West", "America/Los_Angeles"),
        server("eu-generic", "Europe (any)", "eu", "Europe", "Europe/Berlin"),
        server("sa-generic", "South America (any)", "sa", "South America", "America/Sao_Paulo"),
        server("kr-syleus", "Syleus", "kr", "Korea", "Asia/Seoul"),
        server("kr-elowen", "Elowen", "kr", "Korea", "Asia/Seoul"),
        server("kr-generic", "Korea (any)", "kr", "Korea", "Asia/Seoul"),
        server("jp-generic", "Japan (any)", "jp", "Japan", "Asia/Tokyo"),
        server("sea-generic", "SEA (any)", "sea", "Southeast Asia", "Asia/Singapore"),
    )

    val default: GameServer = all.first { it.key == "na-east-generic" }

    fun regions(): List<String> = all.map { it.region }.distinct()

    fun inRegion(region: String): List<GameServer> = all.filter { it.region == region }

    fun find(keyOrName: String?): GameServer? {
        val raw = keyOrName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        all.firstOrNull { it.key.equals(raw, ignoreCase = true) }?.let { return it }
        all.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it }
        val folded = raw.lowercase()
        return all.firstOrNull { server ->
            folded.contains(server.name.lowercase()) ||
                server.name.lowercase().contains(folded) ||
                folded.contains(server.region) ||
                folded.contains(server.regionLabel.lowercase())
        }
    }

    fun custom(name: String, region: GameServer = default): GameServer =
        region.copy(
            key = "custom-${name.trim().lowercase().replace(' ', '-')}",
            name = name.trim(),
        )

    private fun server(
        key: String,
        name: String,
        region: String,
        regionLabel: String,
        zoneId: String,
    ) = GameServer(key, name, region, regionLabel, zoneId, source = "community")
}
