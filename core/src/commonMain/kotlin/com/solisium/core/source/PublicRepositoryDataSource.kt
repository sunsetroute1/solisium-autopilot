package com.solisium.core.source

import com.solisium.core.db.SolisiumDatabase

class PublicRepositoryDataSource : DataSource {
    override val id: String = "public_repo"

    override fun probe(): SourceCapability = SourceCapability(
        id = id,
        available = false,
        provides = emptyList(),
        notes = "Not an import source. Questlog/TLDB live fetch is a user-initiated overlay on the Build screen.",
    )

    override fun importInto(db: SolisiumDatabase, request: ImportRequest): ImportReceipt {
        return ImportReceipt(
            source = id,
            recordsImported = 0,
            recordsSkipped = 0,
            warnings = listOf(
                "PublicRepositoryDataSource does not import. Use the Build screen Search current meta action, or paste JSON through ManualImportDataSource.",
            ),
        )
    }
}
