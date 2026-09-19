package com.solisium.core.talkingwall

import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.source.TalkingWallEnCsvLocator
import java.nio.file.Files

object TalkingWallLocresSync {
    fun supplementFromExtract(
        db: SolisiumDatabase,
        snapshotId: String,
        gameBuild: String?,
    ): TalkingWallImportSummary? {
        val csv = TalkingWallEnCsvLocator.resolve(gameBuild) ?: return null
        val text = Files.readString(csv)
        val parsed = TalkingWallLocresParser.parseEnCsv(text)
        if (parsed.isEmpty()) return null
        return TalkingWallImporter.supplementLocres(
            db,
            snapshotId,
            parsed,
            sourceLabel = "TLDialogue/en.csv@$gameBuild",
        )
    }
}
