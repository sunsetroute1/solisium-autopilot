package com.solisium.core.snapshot

import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.DatasetSnapshot

class SnapshotNotFoundException(ref: String) : IllegalArgumentException("unknown snapshot: $ref")

class SnapshotService(private val db: SolisiumDatabase) {
    fun get(id: String): DatasetSnapshot? {
        val row = db.schemaQueries.selectSnapshot(id).executeAsOneOrNull() ?: return null
        return row.toDomain(aliasesOf(row.id))
    }

    fun list(): List<DatasetSnapshot> =
        db.schemaQueries.selectAllSnapshots().executeAsList().map { it.toDomain(aliasesOf(it.id)) }

    fun active(): DatasetSnapshot? {
        val row = db.schemaQueries.selectActiveSnapshot().executeAsOneOrNull() ?: return null
        return row.toDomain(aliasesOf(row.id))
    }

    fun resolve(idOrAlias: String): DatasetSnapshot? {
        get(idOrAlias)?.let { return it }
        val alias = db.schemaQueries.selectAlias(idOrAlias).executeAsOneOrNull() ?: return null
        return get(alias.snapshot_id)
    }

    /**
     * Marks one snapshot active. Other snapshots and their game rows stay on disk.
     */
    fun activate(idOrAlias: String): DatasetSnapshot {
        val snapshot = resolve(idOrAlias) ?: throw SnapshotNotFoundException(idOrAlias)
        // Clearing the flag and setting the new one must be atomic, or a failure
        // between them leaves the database with no active snapshot.
        db.transaction {
            db.schemaQueries.clearActiveSnapshots()
            db.schemaQueries.activateSnapshot(snapshot.id)
        }
        return get(snapshot.id) ?: throw SnapshotNotFoundException(snapshot.id)
    }

    fun setAlias(alias: String, snapshotId: String): DatasetSnapshot {
        val normalized = alias.trim().lowercase()
        require(normalized.isNotEmpty()) { "alias must not be empty" }
        if (get(snapshotId) == null) throw SnapshotNotFoundException(snapshotId)
        db.schemaQueries.insertAlias(normalized, snapshotId)
        return get(snapshotId)!!
    }

    private fun aliasesOf(snapshotId: String): List<String> =
        db.schemaQueries.selectAliases(snapshotId).executeAsList()
}

private fun com.solisium.core.db.Dataset_snapshot.toDomain(aliases: List<String>) = DatasetSnapshot(
    id = id,
    source = source,
    extractedAt = extracted_at,
    gameBuild = game_build,
    gameVersion = game_version,
    schemaVersion = schema_version,
    sourcePath = source_path,
    sourceHash = source_hash,
    decoderVersion = decoder_version,
    active = active == 1L,
    aliases = aliases,
)
