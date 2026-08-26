package com.solisium.core.source

import com.solisium.core.domain.DatasetSnapshot
import java.nio.file.Path
import java.time.Instant

/**
 * Decides whether a newer TL-Helper warehouse should be imported. Never unpacks
 * paks; a patched game without a matching warehouse stays a wait state.
 */
class PatchWatch(
    private val detectInstall: () -> InstalledGameDataSource.DetectedInstall? = { InstalledGameDataSource().detect() },
    private val listWarehouses: () -> List<WarehouseRef> = { WarehouseLocator().list() },
    private val hashFile: (Path) -> String = { TLHelperDataSource.sha256File(it) },
    private val lastPakFingerprint: () -> String? = { null },
) {
    fun inspect(active: DatasetSnapshot?): PatchWatchReport {
        val install = detectInstall()
        val warehouses = listWarehouses()
        val candidate = pickWarehouse(install?.buildId, warehouses)
        val hash = candidate?.let { it.sha256 ?: runCatching { hashFile(it.path) }.getOrNull() }
        val hashed = candidate?.copy(sha256 = hash)
        val previousPak = lastPakFingerprint()
        val pakChanged = !install?.pakFingerprint.isNullOrBlank() &&
            !previousPak.isNullOrBlank() &&
            install?.pakFingerprint != previousPak &&
            (hashed == null || hashed.sha256 == active?.sourceHash)
        val state = decide(install, hashed, active, pakChanged)
        return PatchWatchReport(
            checkedAt = Instant.now().toString(),
            state = state,
            installedBuild = install?.buildId,
            pakFingerprint = install?.pakFingerprint,
            activeBuild = active?.gameBuild,
            activeSourceHash = active?.sourceHash,
            warehouse = hashed,
            reason = reason(state, install, hashed, active),
            canImport = state == PatchWatchState.IMPORT_READY && hashed != null,
        )
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 15 * 60 * 1000

        internal fun pickWarehouse(
            installedBuild: String?,
            warehouses: List<WarehouseRef>,
        ): WarehouseRef? {
            if (warehouses.isEmpty()) return null
            if (!installedBuild.isNullOrBlank()) {
                return warehouses.filter { it.buildId == installedBuild }
                    .maxByOrNull { it.lastModifiedMillis }
            }
            return warehouses.maxByOrNull { it.lastModifiedMillis }
        }

        internal fun decide(
            install: InstalledGameDataSource.DetectedInstall?,
            warehouse: WarehouseRef?,
            active: DatasetSnapshot?,
            pakChanged: Boolean,
        ): PatchWatchState {
            val warehouseHash = warehouse?.sha256
            if (warehouse != null && warehouseHash != null && warehouseHash != active?.sourceHash) {
                val installed = install?.buildId
                if (installed.isNullOrBlank() || warehouse.buildId == null || warehouse.buildId == installed) {
                    return PatchWatchState.IMPORT_READY
                }
            }
            if (install?.buildId == null && warehouse == null) {
                return if (active == null) PatchWatchState.NO_INSTALL else PatchWatchState.CURRENT
            }
            if (install?.buildId != null && warehouse == null && install.buildId != active?.gameBuild) {
                return PatchWatchState.WAITING_FOR_WAREHOUSE
            }
            if (pakChanged) return PatchWatchState.WAITING_FOR_WAREHOUSE
            if (install?.buildId != null && active?.gameBuild != null && install.buildId != active.gameBuild) {
                return PatchWatchState.WAITING_FOR_WAREHOUSE
            }
            if (warehouse == null && active == null) return PatchWatchState.NO_WAREHOUSE
            if (install?.buildId == null && warehouse == null) return PatchWatchState.NO_INSTALL
            return PatchWatchState.CURRENT
        }

        private fun reason(
            state: PatchWatchState,
            install: InstalledGameDataSource.DetectedInstall?,
            warehouse: WarehouseRef?,
            active: DatasetSnapshot?,
        ): String = when (state) {
            PatchWatchState.CURRENT ->
                "Catalog build ${active?.gameBuild ?: "none"} matches the install" +
                    (install?.buildId?.let { " ($it)" } ?: "") + "."
            PatchWatchState.IMPORT_READY ->
                "A TL-Helper warehouse for build ${warehouse?.buildId ?: "unknown"} is ready to import."
            PatchWatchState.WAITING_FOR_WAREHOUSE ->
                "Steam build ${install?.buildId ?: "unknown"} is ahead of catalog " +
                    "${active?.gameBuild ?: "none"}. Run TL-Helper's extract, then Solisium will import " +
                    "tl-${install?.buildId}.sqlite. Solisium does not unpack paks."
            PatchWatchState.NO_INSTALL ->
                "Throne and Liberty install was not found. Set SOLISIUM_TL_INSTALL if it lives outside the usual Steam folders."
            PatchWatchState.NO_WAREHOUSE ->
                "No tl-*.sqlite warehouse is on disk. Extract with TL-Helper first."
        }
    }
}

enum class PatchWatchState {
    CURRENT,
    IMPORT_READY,
    WAITING_FOR_WAREHOUSE,
    NO_INSTALL,
    NO_WAREHOUSE,
}

data class PatchWatchReport(
    val checkedAt: String,
    val state: PatchWatchState,
    val installedBuild: String?,
    val pakFingerprint: String?,
    val activeBuild: String?,
    val activeSourceHash: String?,
    val warehouse: WarehouseRef?,
    val reason: String,
    val canImport: Boolean,
)

data class WarehouseRef(
    val path: Path,
    val buildId: String?,
    val lastModifiedMillis: Long,
    val sizeBytes: Long,
    val sha256: String? = null,
)
