package com.solisium.core.source

import com.solisium.core.combat.CombatLogParser
import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.platform.randomUuid
import com.solisium.core.platform.sha256Hex

class CombatLogDataSource : DataSource {
    override val id: String = "combat_log"

    override fun probe(): SourceCapability = SourceCapability(
        id = id,
        available = true,
        provides = listOf("combat_event", "user_combat_session"),
        notes = "Official CombatLogVersion,4 CSV. Unknown LogType values are stored, not dropped. Observed damage is not modeled DPS.",
    )

    override fun importInto(db: SolisiumDatabase, request: ImportRequest): ImportReceipt {
        val text = request.content
            ?: throw IllegalArgumentException("combat log content is required (CLI should read the file)")
        val parsed = CombatLogParser.parse(text)
        val sessionId = randomUuid()
        val hash = sha256Hex(text)
        val started = parsed.events.firstOrNull { !it.timestamp.isNullOrBlank() }?.timestamp
        val ended = parsed.events.lastOrNull { !it.timestamp.isNullOrBlank() }?.timestamp
        db.schemaQueries.insertCombatSession(
            id = sessionId,
            character_id = request.characterId,
            started_at = started,
            ended_at = ended,
            source_path = request.path,
            source_hash = hash,
            log_version = parsed.version,
            notes = if (parsed.errors.isEmpty()) null else parsed.errors.joinToString("; "),
        )
        parsed.events.forEach { event ->
            db.schemaQueries.insertCombatEvent(
                session_id = sessionId,
                timestamp = event.timestamp,
                actor = event.actor,
                target = event.target,
                skill_id = event.skillId,
                skill_name = event.skillName,
                damage = event.damage,
                damage_type = event.hitType,
                critical = event.critical.toFlag(),
                heavy = event.heavy.toFlag(),
                hit = event.hit.toFlag(),
                miss = event.miss.toFlag(),
                log_type = event.logType,
                raw_line = event.rawLine,
                source_hash = hash,
            )
        }
        return ImportReceipt(
            source = id,
            sessionId = sessionId,
            recordsImported = parsed.events.size,
            recordsSkipped = 0,
            warnings = parsed.errors,
        )
    }

    private fun Boolean?.toFlag(): Long? = when (this) {
        true -> 1L
        false -> 0L
        null -> null
    }
}
