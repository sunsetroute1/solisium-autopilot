package com.solisium.core.source

import com.solisium.core.db.SolisiumDatabase

class PublicRepositoryDataSource : DataSource {
    override val id: String = "public_repo"

    override fun probe(): SourceCapability = SourceCapability(
        id = id,
        available = false,
        provides = emptyList(),
        notes = "Stub. No license-clear public dataset is wired. Questlog live fetch is off by default.",
    )

    override fun importInto(db: SolisiumDatabase, request: ImportRequest): ImportReceipt {
        return ImportReceipt(
            source = id,
            recordsImported = 0,
            recordsSkipped = 0,
            warnings = listOf("PublicRepositoryDataSource is not implemented; refusing to scrape third-party APIs"),
        )
    }
}
