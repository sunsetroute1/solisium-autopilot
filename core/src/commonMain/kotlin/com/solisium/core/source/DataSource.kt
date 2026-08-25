package com.solisium.core.source

import com.solisium.core.db.SolisiumDatabase

interface DataSource {
    val id: String
    fun probe(): SourceCapability
    fun importInto(db: SolisiumDatabase, request: ImportRequest): ImportReceipt
}

data class SourceCapability(
    val id: String,
    val available: Boolean,
    val provides: List<String>,
    val notes: String,
)

data class ImportRequest(
    val path: String? = null,
    val content: String? = null,
    val activate: Boolean = true,
    val characterId: String? = null,
)

data class ImportReceipt(
    val source: String,
    val snapshotId: String? = null,
    val sessionId: String? = null,
    val characterId: String? = null,
    val recordsImported: Int,
    val recordsSkipped: Int,
    val warnings: List<String> = emptyList(),
)
