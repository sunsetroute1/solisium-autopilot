package com.solisium.core.domain

/**
 * One named row from Questlog `database.getEvents`. Community catalog, not a
 * spawn time and not a warehouse table.
 */
data class CommunityEventEntry(
    val id: String,
    val name: String,
    val category: String?,
    val icon: String? = null,
    val createdAt: String? = null,
)

data class GameServer(
    val key: String,
    val name: String,
    val region: String,
    val regionLabel: String,
    val zoneId: String,
    val source: String = "community",
)

data class TimelineSlot(
    val startsAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val hour: Int,
    val minute: Int,
    val kind: EventKind,
    val title: String,
    val detail: String,
    val occurring: Boolean,
    val source: String,
)

data class EventRosterGroup(
    val kind: EventKind,
    val names: List<String>,
    val source: String,
)

data class EventDayPlan(
    val server: GameServer,
    val dayEpochMs: Long,
    val dayLabel: String,
    val zoneLabel: String,
    val slots: List<TimelineSlot>,
    val upcoming: List<TimelineSlot>,
    val roster: List<EventRosterGroup>,
    val notes: List<String>,
    val warnings: List<String>,
    val fetchedAt: String?,
    val catalogCount: Int,
)

enum class EventKind(
    val id: String,
    val label: String,
) {
    Dynamic("dynamic", "Dynamic events"),
    FieldBoss("field_boss", "Field bosses"),
    WorldBoss("world_boss", "World bosses"),
    Archboss("archboss", "Archboss"),
    Boonstone("boonstone", "Boonstones"),
    Riftstone("riftstone", "Riftstones"),
    Carrier("carrier", "Map carriers"),
    Other("other", "Other"),
    ;

    companion object {
        fun fromCategory(raw: String?): EventKind {
            val token = raw?.lowercase().orEmpty()
            return when {
                token.contains("bossstone") || token.contains("rift") -> Riftstone
                token.contains("regionstone") || token.contains("boon") -> Boonstone
                token.contains("carrier") || token.contains("gigantrite") -> Carrier
                token.contains("arch") || token.contains("tevent") || token.contains("bellandir") -> Archboss
                token.contains("world") -> WorldBoss
                token.contains("field") || token.contains("boss") -> FieldBoss
                token.contains("dynamic") || token.contains("event") -> Dynamic
                else -> Other
            }
        }
    }
}
