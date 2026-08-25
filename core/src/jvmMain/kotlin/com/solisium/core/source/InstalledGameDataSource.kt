package com.solisium.core.source

import com.solisium.core.db.SchemaVersion
import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.platform.randomUuid
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Detects a Throne and Liberty install. Does not decrypt paks or extract tables.
 */
class InstalledGameDataSource(
    private val steamRootOverrides: List<Path> = emptyList(),
) : DataSource {
    override val id: String = "installed_game"

    override fun probe(): SourceCapability {
        val detected = detect()
        return SourceCapability(
            id = id,
            available = detected != null,
            provides = listOf("game_build"),
            notes = detected?.let {
                "Found install at ${it.installPath}; Steam build ${it.buildId ?: "unknown"}"
            } ?: "Steam install for app 2429640 not found",
        )
    }

    override fun importInto(db: SolisiumDatabase, request: ImportRequest): ImportReceipt {
        val detected = detect()
            ?: return ImportReceipt(
                source = id,
                recordsImported = 0,
                recordsSkipped = 0,
                warnings = listOf("Throne and Liberty install not found"),
            )
        val snapshotId = randomUuid()
        // Deactivating the old snapshot and inserting the new one must be atomic,
        // otherwise a failure between them leaves no snapshot active at all.
        db.transaction {
            if (request.activate) {
                db.schemaQueries.clearActiveSnapshots()
            }
            db.schemaQueries.insertSnapshot(
                id = snapshotId,
                source = id,
                extracted_at = Instant.now().toString(),
                game_build = detected.buildId ?: "unknown",
                game_version = "unknown",
                schema_version = SchemaVersion.CURRENT.toLong(),
                source_path = detected.installPath.toString(),
                source_hash = detected.pakFingerprint,
                decoder_version = null,
                active = if (request.activate) 1L else 0L,
            )
        }
        return ImportReceipt(
            source = id,
            snapshotId = snapshotId,
            recordsImported = 0,
            recordsSkipped = 0,
            warnings = listOf("detection only; import a TL-Helper warehouse for game tables"),
        )
    }

    data class DetectedInstall(
        val installPath: Path,
        val buildId: String?,
        val pakFingerprint: String?,
    )

    fun detect(): DetectedInstall? {
        val envInstall = System.getenv("SOLISIUM_TL_INSTALL")
        val candidates = buildList {
            if (!envInstall.isNullOrBlank()) add(Path.of(envInstall))
            steamRootOverrides.forEach { add(it) }
            add(Path.of("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Throne and Liberty"))
            add(Path.of("D:\\SteamLibrary\\steamapps\\common\\Throne and Liberty"))
            System.getenv("SOLISIUM_STEAM_LIBRARY")?.let { library ->
                add(Path.of(library, "steamapps", "common", "Throne and Liberty"))
            }
        }
        val install = candidates.firstOrNull { Files.isDirectory(it) } ?: return null
        val steamapps = install.parent?.parent
        val manifest = steamapps?.resolve("appmanifest_2429640.acf")
        val buildId = if (manifest != null && Files.isRegularFile(manifest)) {
            parseBuildId(Files.readString(manifest))
        } else {
            null
        }
        val paks = install.resolve("TL").resolve("Content").resolve("Paks")
        val fingerprint = if (Files.isDirectory(paks)) pakFingerprint(paks) else null
        return DetectedInstall(installPath = install, buildId = buildId, pakFingerprint = fingerprint)
    }

    companion object {
        internal fun parseBuildId(acf: String): String? {
            val match = Regex(""""buildid"\s+"(\d+)"""").find(acf)
            return match?.groupValues?.get(1)
        }

        private fun pakFingerprint(paks: Path): String {
            val names = Files.list(paks).use { stream ->
                stream.toList()
                    .filter { Files.isRegularFile(it) }
                    .map { "${it.fileName}:${Files.getLastModifiedTime(it).toMillis()}" }
                    .sorted()
            }
            return names.take(32).joinToString("|").hashCode().toUInt().toString(16)
        }
    }
}
