package com.solisium.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.solisium.core.db.JvmDatabase
import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.DatasetSnapshot
import com.solisium.core.domain.GameCurvePoint
import com.solisium.core.domain.GameItemCurve
import com.solisium.core.domain.GameItemStat
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.UserCharacter
import com.solisium.core.query.BuildMismatch
import com.solisium.core.query.CatalogQuery
import com.solisium.core.source.CombatLogDataSource
import com.solisium.core.source.CombatLogPaths
import com.solisium.core.source.ImportReceipt
import com.solisium.core.source.ImportRequest
import com.solisium.core.source.InstalledGameDataSource
import com.solisium.core.source.ManualImportDataSource
import com.solisium.core.source.TLHelperDataSource
import com.solisium.core.source.WarehouseLocator
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Async result with the three states every screen has to render honestly. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

enum class Screen(val label: String, val blurb: String) {
    Overview("Overview", "Dataset provenance and catalog coverage"),
    Catalog("Catalog", "Search extracted game data"),
    Character("Character", "Your loadout, resolved against the dataset"),
    Combat("Combat", "Observed damage from official logs"),
    Data("Data", "Snapshots and imports"),
}

/** Outcome of an import, kept so the UI can report exactly what landed. */
data class ImportOutcome(
    val label: String,
    val receipts: List<ImportReceipt>,
    val error: String? = null,
) {
    val imported: Int get() = receipts.sumOf { it.recordsImported }
    val skipped: Int get() = receipts.sumOf { it.recordsSkipped }
    val warnings: List<String> get() = receipts.flatMap { it.warnings }
}

/** A catalog kind rendered as a uniform row, so one list view serves every type. */
enum class CatalogKind(val label: String, val meta: String) {
    Items("Items", "grade"),
    Weapons("Weapons", "type"),
    Armor("Armor", "slot"),
    Accessories("Accessories", "slot"),
    Traits("Traits", ""),
    Runes("Runes", "grade"),
    Skills("Skills", "type"),
    Effects("Effects", "skill"),
    Recipes("Recipes", "kind"),
    Materials("Materials", ""),
    Stats("Stats", ""),
    Formulas("Formulas", "confidence"),
}

data class CatalogRow(
    val name: String,
    val sourceTable: String,
    val sourceRowId: String,
    val meta: String?,
)

/** Everything the detail pane shows for one selected row. */
data class RowDetail(
    val row: CatalogRow,
    val stats: List<GameItemStat>,
    val curves: List<GameItemCurve>,
    val curvePoints: List<GameCurvePoint>,
)

data class Overview(
    val snapshot: DatasetSnapshot?,
    val counts: CatalogCounts?,
    val installedBuild: String?,
    val buildWarning: String?,
)

/**
 * Holds all screen state and owns database access. The UI never touches SQLDelight
 * directly and never computes game values; it only renders what `core` returns.
 */
class AppModel(private val scope: CoroutineScope) {
    /** One JDBC connection shared by every screen, so calls are serialized. */
    private val dbLock = Mutex()
    private var openError: String? = null
    private val database: SolisiumDatabase? by lazy(LazyThreadSafetyMode.NONE) { openDatabase() }
    private val query: CatalogQuery? by lazy(LazyThreadSafetyMode.NONE) { database?.let { CatalogQuery(it) } }

    var screen by mutableStateOf(Screen.Overview)
        private set

    var overview by mutableStateOf<Load<Overview>>(Load.Loading)
        private set

    var kind by mutableStateOf(CatalogKind.Items)
        private set

    var search by mutableStateOf("")
        private set

    var rows by mutableStateOf<Load<List<CatalogRow>>>(Load.Loading)
        private set

    var selected by mutableStateOf<CatalogRow?>(null)
        private set

    var detail by mutableStateOf<Load<RowDetail>?>(null)
        private set

    var combat by mutableStateOf<Load<List<CombatSessionSummary>>>(Load.Loading)
        private set

    var snapshots by mutableStateOf<Load<List<DatasetSnapshot>>>(Load.Loading)
        private set

    var characters by mutableStateOf<Load<List<UserCharacter>>>(Load.Loading)
        private set

    var characterSheet by mutableStateOf<Load<ResolvedCharacterSheet>?>(null)
        private set

    var selectedCharacterId by mutableStateOf<String?>(null)
        private set

    /** True while an import runs, so the UI can disable the buttons rather than queue work. */
    var importing by mutableStateOf(false)
        private set

    var lastImport by mutableStateOf<ImportOutcome?>(null)
        private set

    private var searchJob: Job? = null
    private var detailJob: Job? = null

    /** Suggested defaults for the import buttons; null when nothing was detected. */
    val detectedWarehouse: Path? by lazy(LazyThreadSafetyMode.NONE) { runCatching { WarehouseLocator().find() }.getOrNull() }
    val detectedLogFolder: Path? by lazy(LazyThreadSafetyMode.NONE) { runCatching { CombatLogPaths.detect() }.getOrNull() }

    init {
        refreshOverview()
        loadRows()
    }

    fun go(target: Screen) {
        screen = target
        when (target) {
            Screen.Overview -> refreshOverview()
            Screen.Catalog -> if (rows is Load.Loading) loadRows()
            Screen.Character -> loadCharacters()
            Screen.Combat -> loadCombat()
            Screen.Data -> loadSnapshots()
        }
    }

    fun selectCharacter(id: String) {
        selectedCharacterId = id
        characterSheet = Load.Loading
        scope.launch {
            characterSheet = guard {
                val q = requireQuery()
                dbLock.withLock {
                    withContext(Dispatchers.IO) {
                        val snapshotId = q.activeSnapshotId()
                        q.resolveCharacter(id, snapshotId) ?: error("character $id not found")
                    }
                }
            }
        }
    }

    /**
     * Imports run through the same `DataSource` adapters the CLI uses. The UI adds no
     * mapping of its own; it only chooses a file and reports the receipt.
     */
    fun importWarehouse(path: Path) = runImport("Game data") {
        listOf(TLHelperDataSource().importInto(it, ImportRequest(path = path.toString(), activate = true)))
    }

    fun importCombatLogs(path: Path?) = runImport("Combat logs") { db ->
        val selection = CombatLogPaths.selectForImport(path?.toString())
        val source = CombatLogDataSource()
        selection.files.map { file ->
            source.importInto(db, ImportRequest(path = file.toString(), content = Files.readString(file)))
        }
    }

    fun importCharacter(path: Path) = runImport("Character") { db ->
        listOf(
            ManualImportDataSource().importInto(
                db,
                ImportRequest(path = path.toString(), content = Files.readString(path)),
            ),
        )
    }

    private fun runImport(label: String, block: (SolisiumDatabase) -> List<ImportReceipt>) {
        if (importing) return
        importing = true
        lastImport = null
        scope.launch {
            val outcome = try {
                val db = database ?: error(openError ?: "database unavailable")
                val receipts = dbLock.withLock { withContext(Dispatchers.IO) { block(db) } }
                ImportOutcome(label, receipts)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                ImportOutcome(label, emptyList(), t.message ?: t::class.simpleName ?: "import failed")
            }
            lastImport = outcome
            importing = false
            // Everything on screen depends on the active snapshot, so refresh broadly.
            refreshOverview()
            loadSnapshots()
            loadRows()
            loadCombat()
            loadCharacters()
        }
    }

    private fun loadCharacters() {
        scope.launch {
            val loaded = guard {
                val q = requireQuery()
                dbLock.withLock { withContext(Dispatchers.IO) { q.characters() } }
            }
            characters = loaded
            // Most people track one character, so an extra click to see it is pure friction.
            val only = (loaded as? Load.Ok)?.value?.singleOrNull()
            if (only != null && selectedCharacterId == null) selectCharacter(only.id)
        }
    }

    fun selectKind(next: CatalogKind) {
        if (next == kind) return
        kind = next
        selected = null
        detail = null
        loadRows()
    }

    /** Debounced so typing does not queue a query per keystroke against 60k rows. */
    fun onSearch(text: String) {
        search = text
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(180)
            loadRows()
        }
    }

    fun select(row: CatalogRow) {
        selected = row
        detailJob?.cancel()
        detail = Load.Loading
        detailJob = scope.launch {
            detail = read { q, snapshotId ->
                RowDetail(
                    row = row,
                    stats = q.itemStats(snapshotId, row.sourceRowId),
                    curves = q.itemCurves(snapshotId, row.sourceRowId),
                    curvePoints = q.itemCurvePoints(snapshotId, row.sourceRowId),
                )
            }
        }
    }

    fun activate(snapshotId: String) {
        scope.launch {
            val result = read { q, _ -> q.snapshotService().activate(snapshotId) }
            if (result is Load.Err) {
                snapshots = Load.Err(result.message)
                return@launch
            }
            refreshOverview()
            loadSnapshots()
            loadRows()
        }
    }

    fun refreshOverview() {
        scope.launch {
            overview = guard {
                val q = requireQuery()
                val snapshot = dbLock.withLock { withContext(Dispatchers.IO) { q.snapshotService().active() } }
                val installed = withContext(Dispatchers.IO) { InstalledGameDataSource().detect()?.buildId }
                val counts = snapshot?.let {
                    dbLock.withLock { withContext(Dispatchers.IO) { q.counts(it.id) } }
                }
                Overview(
                    snapshot = snapshot,
                    counts = counts,
                    installedBuild = installed,
                    buildWarning = BuildMismatch.warning(installed, snapshot?.gameBuild),
                )
            }
        }
    }

    private fun loadCombat() {
        scope.launch { combat = read { q, _ -> q.combatSessions() } }
    }

    private fun loadSnapshots() {
        scope.launch { snapshots = read { q, _ -> q.snapshots() } }
    }

    private fun loadRows() {
        val term = search.takeIf { it.isNotBlank() }
        val target = kind
        scope.launch {
            rows = Load.Loading
            rows = read { q, snapshotId -> fetch(q, snapshotId, target, term) }
        }
    }

    private fun fetch(
        q: CatalogQuery,
        snapshotId: String,
        target: CatalogKind,
        term: String?,
    ): List<CatalogRow> = when (target) {
        CatalogKind.Items -> q.items(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, it.grade)
        }
        CatalogKind.Weapons -> q.weapons(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, it.weaponType)
        }
        CatalogKind.Armor -> q.armor(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, it.slot)
        }
        CatalogKind.Accessories -> q.accessories(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, it.slot)
        }
        CatalogKind.Traits -> q.traits(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, null)
        }
        CatalogKind.Runes -> q.runes(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, it.grade)
        }
        CatalogKind.Skills -> q.skills(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, it.skillType)
        }
        CatalogKind.Effects -> q.effects(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, it.skillSourceRowId)
        }
        CatalogKind.Recipes -> q.recipes(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, it.recipeKind)
        }
        CatalogKind.Materials -> q.materials(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, null)
        }
        CatalogKind.Stats -> q.stats(snapshotId, term).map {
            CatalogRow(it.name ?: it.sourceRowId, it.sourceTable, it.sourceRowId, null)
        }
        CatalogKind.Formulas -> q.formulas(snapshotId, term).map {
            CatalogRow(it.sourceRowId, it.sourceTable, it.sourceRowId, it.confidence)
        }
    }

    /** Runs a read that needs the active snapshot, mapping failures into [Load.Err]. */
    private suspend fun <T> read(block: (CatalogQuery, String) -> T): Load<T> = guard {
        val q = requireQuery()
        val snapshotId = dbLock.withLock { withContext(Dispatchers.IO) { q.activeSnapshotId() } }
            ?: error("No active snapshot. Import a TL-Helper warehouse first.")
        dbLock.withLock { withContext(Dispatchers.IO) { block(q, snapshotId) } }
    }

    private suspend fun <T> guard(block: suspend () -> T): Load<T> = try {
        Load.Ok(block())
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        Load.Err(t.message ?: t::class.simpleName ?: "unknown error")
    }

    private fun requireQuery(): CatalogQuery = query ?: error(openError ?: "database unavailable")

    private fun openDatabase(): SolisiumDatabase? = try {
        JvmDatabase.openOrCreate(databasePath())
    } catch (t: Throwable) {
        openError = "Could not open ${databasePath()}: ${t.message}"
        null
    }

    companion object {
        /** Matches the CLI so both surfaces read the same database. */
        fun databasePath(): Path {
            val override = System.getenv("SOLISIUM_DB")
            return if (override.isNullOrBlank()) {
                Path.of(System.getProperty("user.home"), ".solisium", "solisium.sqlite")
            } else {
                Path.of(override)
            }
        }

        fun databaseExists(): Boolean = Files.isRegularFile(databasePath())
    }
}
