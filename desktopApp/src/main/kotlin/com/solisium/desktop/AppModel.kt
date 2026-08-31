package com.solisium.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.solisium.core.bootstrap.StarterBootstrap
import com.solisium.core.db.JvmDatabase
import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.BuildAdvice
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.domain.CatalogHit
import com.solisium.core.domain.ClassSource
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.CommunityEventEntry
import com.solisium.core.domain.CommunitySnapshot
import com.solisium.core.domain.DatasetSnapshot
import com.solisium.core.domain.EventDayPlan
import com.solisium.core.domain.GameServer
import com.solisium.core.domain.DesiredBuildPlan
import com.solisium.core.domain.DiscoveredInfluence
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.GameCurvePoint
import com.solisium.core.domain.GameItemCurve
import com.solisium.core.domain.GameItemPower
import com.solisium.core.domain.GameItemStat
import com.solisium.core.domain.GameItem
import com.solisium.core.domain.DropCacheStats
import com.solisium.core.domain.ItemDropSource
import com.solisium.core.domain.MonsterProfile
import com.solisium.core.domain.TalkingWallCoverage
import com.solisium.core.domain.TalkingWallSnapshotDelta
import com.solisium.core.domain.TalkingWallStatement
import com.solisium.core.talkingwall.TalkingWallResources
import com.solisium.core.domain.QuestlogItemOverlay
import com.solisium.core.domain.QuestlogNpcDetail
import com.solisium.core.meta.DropCacheSync
import com.solisium.core.meta.DropSyncProgress
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.StatKeyLabel
import com.solisium.core.domain.UserCharacter
import com.solisium.core.domain.BuildClassOption
import com.solisium.core.domain.WeaponTypeLabel
import com.solisium.core.domain.WeaponClassMatch
import com.solisium.core.meta.CommunityMetaClient
import com.solisium.core.meta.CommunityOverlay
import com.solisium.core.meta.GameServers
import com.solisium.core.meta.CommunityWeaponClasses
import com.solisium.core.meta.OllamaNarrator
import com.solisium.core.meta.TextNorm
import com.solisium.core.query.BuildGoal
import com.solisium.core.query.BuildMismatch
import com.solisium.core.query.CatalogQuery
import com.solisium.core.query.DesiredBuildPlanner
import com.solisium.core.query.EventTimelineBuilder
import com.solisium.core.query.StatAxis
import com.solisium.core.query.WeaponClassResolver
import com.solisium.core.secret.AesKey
import com.solisium.core.secret.KeyCandidate
import com.solisium.core.secret.SecretRef
import com.solisium.core.secret.SecretScanner
import com.solisium.core.secret.SecretStore
import com.solisium.core.source.CharacterLocator
import com.solisium.core.source.CharacterSheetJson
import com.solisium.core.source.CombatLogDataSource
import com.solisium.core.source.CombatLogDiscovery
import com.solisium.core.source.CombatLogFolderStatus
import com.solisium.core.source.CombatLogPaths
import com.solisium.core.source.GearCatalogFilter
import com.solisium.core.source.ImportReceipt
import com.solisium.core.source.ImportProgress
import com.solisium.core.source.ImportRequest
import com.solisium.core.source.InstalledGameDataSource
import com.solisium.core.source.ManualImportDataSource
import com.solisium.core.source.PatchWatch
import com.solisium.core.source.PatchWatchReport
import com.solisium.core.source.PatchWatchState
import com.solisium.core.source.TLHelperDataSource
import com.solisium.core.source.TLHelperExtractProgress
import com.solisium.core.source.TLHelperInstaller
import com.solisium.core.source.TLHelperLauncher
import com.solisium.core.source.TLHelperLocator
import com.solisium.core.source.TLHelperRunLog
import com.solisium.core.source.TLHelperRunStatus
import com.solisium.core.source.WarehouseLocator
import com.solisium.core.source.WarehouseRef
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
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
    Overview("Home", "What to do next"),
    Build("Build", "What you have, and what you want"),
    Catalog("Gear", "Search by the name you see in game"),
    Drops("Drops", "Monsters, loot tables, farm spots"),
    Events("Events", "Boss and event timetable"),
    Wall("Wall", "Talking Wall true/false answers"),
    Character("Character", "Your loadout"),
    Combat("Combat", "Damage from official logs"),
    Data("Data", "Import and keys"),
}

enum class DropLookupMode { Item, Monster }

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

/** First-run warehouse setup shown after install when real TL-Helper data is available. */
data class WarehouseSetupOffer(
    val warehousePath: Path?,
    val warehouseBuild: String?,
    val demoActive: Boolean,
    val canImport: Boolean,
    val reason: String?,
)

/** Shown after warehouse import when offline drop cache needs a Questlog pull. */
data class DropSyncOffer(
    val monstersTotal: Long,
    val dropRows: Long,
)

/**
 * State of the key finder.
 *
 * [candidates] carry key material, so this type is never logged and the UI only ever
 * renders a candidate's fingerprint and where it came from.
 */
data class KeyState(
    val stored: List<SecretRef> = emptyList(),
    val candidates: List<KeyCandidate> = emptyList(),
    val searchedRoots: List<String> = emptyList(),
    val scanning: Boolean = false,
    val scanned: Boolean = false,
    val message: String? = null,
    /** Set when a key was found on first run and the user has not answered yet. */
    val offer: KeyCandidate? = null,
    /** Set when several keys were found on first run; [candidates] holds them. */
    val offerChoice: Boolean = false,
) {
    override fun toString(): String =
        "KeyState(stored=$stored, candidates=${candidates.size}, scanning=$scanning, offer=${offer != null}, offerChoice=$offerChoice)"
}

/** A catalog kind rendered as a uniform row, so one list view serves every type. */
enum class CatalogKind(val label: String, val meta: String) {
    Items("Items", "rarity"),
    Weapons("Weapons", "type"),
    Armor("Armor", "slot"),
    Accessories("Accessories", "slot"),
    Traits("Traits", ""),
    Runes("Runes", "type"),
    Skills("Skills", "type"),
    Effects("Effects", ""),
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
    val grade: String? = null,
    val named: Boolean = true,
)

/** Everything the detail pane shows for one selected row. */
data class RowDetail(
    val row: CatalogRow,
    val stats: List<GameItemStat>,
    val curves: List<GameItemCurve>,
    val curvePoints: List<GameCurvePoint>,
    val combatPower: GameItemPower? = null,
    val questlog: QuestlogItemOverlay? = null,
    val questlogWarning: String? = null,
    val category: String? = null,
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

    var patchWatch by mutableStateOf<PatchWatchReport?>(null)
        private set

    var tlHelperMessage by mutableStateOf<String?>(null)
    var tlHelperCheckout by mutableStateOf<Path?>(TLHelperLocator().find())
        private set

    var catalogSyncNote by mutableStateOf<String?>(null)
        private set

    var tlHelperLastRun by mutableStateOf<TLHelperRunStatus?>(null)
        private set

    var extractProgress by mutableStateOf<TLHelperExtractProgress.Snapshot?>(null)
        private set

    var discoveredInfluences by mutableStateOf<List<DiscoveredInfluence>>(emptyList())
        private set

    private var pendingCatalogConfirm = false
    private var warehouseWatchJob: Job? = null

    var kind by mutableStateOf(CatalogKind.Items)
        private set

    var search by mutableStateOf("")
        private set

    var rows by mutableStateOf<Load<List<CatalogRow>>>(Load.Loading)
        private set

    /** Total named matches before the browse cap, so the list can say "first N of M". */
    var browseTotal by mutableStateOf(0)
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

    var characterDraft by mutableStateOf<CharacterSheetJson.Draft?>(null)
        private set

    private var characterDraftDirty = false
    private var attemptedCharacterAutoImport = false

    var characterSuggestField by mutableStateOf<String?>(null)
        private set

    var characterSuggestions by mutableStateOf<List<CatalogHit>>(emptyList())
        private set

    var characterSuggestionsReady by mutableStateOf(false)
        private set

    private var gearSuggestJob: Job? = null
    private var gearSuggestSeq = 0

    var goal by mutableStateOf(BuildGoal.RangedDps)
        private set

    var buildClasses by mutableStateOf<List<BuildClassOption>>(emptyList())
        private set

    var selectedClassKey by mutableStateOf<String?>(null)
        private set

    var classQuery by mutableStateOf("")
        private set

    private var classChosenByUser = false

    var axes by mutableStateOf(setOf<StatAxis>())
        private set

    var extraStatKeys by mutableStateOf(setOf<String>())
        private set

    var extraStatQuery by mutableStateOf("")
        private set

    var availableStatKeys by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    var desiredCombatPowerText by mutableStateOf("")
        private set

    var desiredGearScoreText by mutableStateOf("")
        private set

    var plan by mutableStateOf<Load<DesiredBuildPlan>>(Load.Loading)
        private set

    var advice by mutableStateOf<Load<BuildAdvice>>(Load.Loading)
        private set

    var community by mutableStateOf<Load<CommunitySnapshot>?>(null)
        private set

    var narration by mutableStateOf<String?>(null)
        private set

    var metaRefreshing by mutableStateOf(false)
        private set

    var questlogSlug by mutableStateOf("")
        private set

    var characterFetching by mutableStateOf(false)
        private set

    private val metaByGoal = mutableMapOf<BuildGoal, CommunitySnapshot>()

    /** True while an import runs, so the UI can disable the buttons rather than queue work. */
    var importing by mutableStateOf(false)
        private set

    var importProgress by mutableStateOf<ImportProgress?>(null)
        private set

    var warehouseSetup by mutableStateOf<WarehouseSetupOffer?>(null)
        private set

    var dropSyncOffer by mutableStateOf<DropSyncOffer?>(null)
        private set

    var dropMode by mutableStateOf(DropLookupMode.Item)
        private set

    var dropSearch by mutableStateOf("")
        private set

    var dropItems by mutableStateOf<Load<List<GameItem>>>(Load.Loading)
        private set

    var dropMonsters by mutableStateOf<Load<List<MonsterProfile>>>(Load.Loading)
        private set

    var selectedDropItem by mutableStateOf<GameItem?>(null)
        private set

    var selectedDropMonster by mutableStateOf<MonsterProfile?>(null)
        private set

    var dropItemDetail by mutableStateOf<Load<QuestlogItemOverlay>?>(null)
        private set

    var dropItemSources by mutableStateOf<Load<List<ItemDropSource>>?>(null)
        private set

    var dropMonsterSources by mutableStateOf<Load<List<ItemDropSource>>?>(null)
        private set

    var dropNpcDetail by mutableStateOf<Load<QuestlogNpcDetail>?>(null)
        private set

    var dropCacheStats by mutableStateOf<DropCacheStats?>(null)
        private set

    var dropSyncProgress by mutableStateOf<DropSyncProgress?>(null)
        private set

    var dropSyncRunning by mutableStateOf(false)
        private set

    var dropSyncMessage by mutableStateOf<String?>(null)
        private set

    var wallSearch by mutableStateOf("")
        private set

    var wallCategory by mutableStateOf<String?>(null)
        private set

    var wallStatements by mutableStateOf<Load<List<TalkingWallStatement>>>(Load.Loading)
        private set

    var wallCoverage by mutableStateOf<TalkingWallCoverage?>(null)
        private set

    var wallDelta by mutableStateOf<TalkingWallSnapshotDelta?>(null)
        private set

    var selectedWallStatement by mutableStateOf<TalkingWallStatement?>(null)
        private set

    private var wallSearchJob: Job? = null

    var eventServer by mutableStateOf(readStoredEventServer())
        private set

    var eventServerQuery by mutableStateOf("")
        private set

    var eventDayOffset by mutableStateOf(0)
        private set

    var eventPlan by mutableStateOf<Load<EventDayPlan>>(Load.Loading)
        private set

    var eventRefreshing by mutableStateOf(false)
        private set

    private var eventCatalog = emptyList<CommunityEventEntry>()
    private var eventCatalogWarnings = emptyList<String>()
    private var eventFetchedAt: String? = null

    var dropFetching by mutableStateOf(false)
        private set

    var lastImport by mutableStateOf<ImportOutcome?>(null)
        private set

    var keys by mutableStateOf(KeyState())
        private set

    private val secrets = SecretStore()

    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var desiredJob: Job? = null
    private var dropSearchJob: Job? = null

    /** Suggested defaults for the import buttons; null when nothing was detected. */
    val detectedWarehouse: Path? by lazy(LazyThreadSafetyMode.NONE) { runCatching { WarehouseLocator().find() }.getOrNull() }
    var combatLogDiscovery by mutableStateOf(CombatLogPaths.discover())
        private set

    /** Expected CombatLogs folder (shown even before first log is written). */
    val detectedLogFolder: Path? get() = combatLogDiscovery.primaryFolder ?: combatLogDiscovery.savedRoot

    fun refreshCombatLogDiscovery() {
        combatLogDiscovery = CombatLogPaths.discover()
    }

    var detectedCharacterFiles by mutableStateOf<List<Path>>(emptyList())
        private set

    val detectedCharacter: Path? get() = detectedCharacterFiles.firstOrNull()

    val characterPickerDirectory: Path
        get() = runCatching { CharacterLocator().pickerDirectory() }.getOrElse {
            Path.of(System.getProperty("user.home"), ".solisium", "characters")
        }

    init {
        StarterBootstrap.seedIfNeeded(databasePath())
        refreshOverview()
        loadRows()
        bootstrapExtractAndKeys()
        refreshCharacterDetection()
        if (detectedCharacter != null) {
            importDetectedCharacters()
        } else {
            loadCharacters()
        }
        startPatchWatch()
    }

    fun go(target: Screen) {
        if (target != Screen.Character) clearGearSuggestions()
        screen = target
        when (target) {
            Screen.Overview -> refreshOverview()
            Screen.Build -> {
                loadCharacters()
                refreshAdvice()
            }
            Screen.Catalog -> if (rows is Load.Loading) loadRows()
            Screen.Drops -> loadDropBrowse()
            Screen.Events -> {
                loadCharacters()
                suggestEventServerFromCharacter()
                refreshEventPlan()
                if (eventCatalog.isEmpty()) refreshEventCatalog()
            }
            Screen.Wall -> loadTalkingWall()
            Screen.Character -> loadCharacters()
            Screen.Combat -> {
                refreshCombatLogDiscovery()
                loadCombat()
                refreshAdvice()
            }
            Screen.Data -> {
                loadSnapshots()
                loadStoredKeys()
            }
        }
    }

    // ---- Key finder -------------------------------------------------------------
    //
    // Nothing here writes a key anywhere except the store under %LOCALAPPDATA%, and
    // nothing puts key material into a message shown on screen.

    val secretStorePath: Path get() = secrets.path

    /**
     * Records that the user said no, so a decision made once is not asked again on
     * every launch. A plain marker file rather than an entry in the secret store,
     * because it is not a secret.
     */
    private val offerDeclinedMarker: Path
        get() = secrets.path.parent.resolve("key-offer.declined")

    /**
     * After installing, put the bundled TL-Helper on disk if it is not already
     * here, then look for a key the operator already has and ask before storing it.
     */
    private fun bootstrapExtractAndKeys() {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (TLHelperLocator().find() == null) {
                    TLHelperInstaller().install()
                }
            }
            tlHelperCheckout = TLHelperLocator().find()
            offerFoundKey()
        }
    }

    /**
     * After installing, the key is usually already sitting on disk from whatever
     * extracted the game data. Rather than expecting the user to know that and go
     * looking for a settings screen, find it once and ask.
     *
     * Only runs when nothing is stored and the user has not already declined, so a
     * normal launch does no scanning at all.
     */
    private fun offerFoundKey() {
        scope.launch {
            val shouldAsk = withContext(Dispatchers.IO) {
                runCatching { secrets.list().isEmpty() && !Files.exists(offerDeclinedMarker) }
                    .getOrDefault(false)
            }
            if (!shouldAsk) return@launch
            val found = withContext(Dispatchers.IO) {
                runCatching { SecretScanner().scan().candidates }.getOrDefault(emptyList())
            }
            when (found.size) {
                0 -> Unit
                1 -> keys = keys.copy(offer = found.single())
                else -> keys = keys.copy(candidates = found, offerChoice = true)
            }
        }
    }

    fun acceptFoundKey() {
        val offered = keys.offer ?: return
        keys = keys.copy(offer = null)
        storeKey(offered)
    }

    fun chooseFoundKeysOnData() {
        keys = keys.copy(offerChoice = false)
        go(Screen.Data)
    }

    fun declineFoundKey() {
        keys = keys.copy(offer = null, offerChoice = false)
        runCatching {
            Files.createDirectories(offerDeclinedMarker.parent)
            Files.writeString(
                offerDeclinedMarker,
                "The offer to store a found archive key was declined. Delete this file to be asked again.\n",
            )
        }
    }

    private fun loadStoredKeys() {
        keys = keys.copy(stored = runCatching { secrets.list() }.getOrDefault(emptyList()))
    }

    /**
     * Looks for a key already on this machine. [root] narrows the search to one folder,
     * which is how a user resolves an ambiguous result.
     */
    fun scanForKeys(root: Path? = null) {
        if (keys.scanning) return
        keys = keys.copy(scanning = true, message = null)
        scope.launch {
            val next = try {
                val report = withContext(Dispatchers.IO) {
                    SecretScanner(useDefaultRoots = root == null).scan(listOfNotNull(root))
                }
                keys.copy(
                    candidates = report.candidates,
                    searchedRoots = report.searchedRoots.map { it.toString() },
                    scanning = false,
                    scanned = true,
                    message = if (report.candidates.isEmpty()) {
                        "No key found in the folders searched. Choose the folder your key is in."
                    } else {
                        null
                    },
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                keys.copy(scanning = false, scanned = true, message = "Search failed: ${t.message}")
            }
            keys = next
            loadStoredKeys()
        }
    }

    fun storeKey(candidate: KeyCandidate, name: String = "archive") {
        keys = try {
            val ref = secrets.put(name, candidate.keyHex)
            // Drop the candidate list once something is stored, so key material is not
            // held in UI state any longer than the choice needs it.
            keys.copy(
                candidates = emptyList(),
                stored = secrets.list(),
                message = "Saved as \"${ref.name}\" (fingerprint ${ref.fingerprint}).",
            )
        } catch (t: Throwable) {
            keys.copy(message = "Could not save: ${t.message}")
        }
    }

    /** Accepts a pasted key, rejecting anything that is not a 32-byte hex value. */
    fun storeTypedKey(raw: String, name: String = "archive") {
        val normalized = AesKey.normalize(raw)
        if (normalized == null) {
            keys = keys.copy(message = "That is not a 32-byte hex key (64 hex characters).")
            return
        }
        storeKey(KeyCandidate("entered by hand", normalized, "typed in"), name)
    }

    fun forgetKey(name: String) {
        keys = try {
            val removed = secrets.remove(name)
            keys.copy(
                stored = secrets.list(),
                message = if (removed) "Removed \"$name\"." else "No key named \"$name\".",
            )
        } catch (t: Throwable) {
            keys.copy(message = "Could not remove: ${t.message}")
        }
    }

    fun dismissKeyMessage() {
        keys = keys.copy(message = null)
    }

    fun pickDropMode(mode: DropLookupMode) {
        if (mode == dropMode) return
        dropMode = mode
        loadDropBrowse()
    }

    fun onDropSearch(text: String) {
        dropSearch = text
        dropSearchJob?.cancel()
        dropSearchJob = scope.launch {
            delay(180)
            loadDropBrowse()
        }
    }

    /** Warehouse grade for loot rows; falls back to row-id tokens when grade is missing. */
    fun itemGrade(sourceRowId: String, explicit: String? = null): String? {
        val q = query ?: return com.solisium.core.domain.ItemGradeHints.resolve(explicit, sourceRowId)
        val snapshotId = q.activeSnapshotId()
            ?: return com.solisium.core.domain.ItemGradeHints.resolve(explicit, sourceRowId)
        return runCatching {
            com.solisium.core.domain.ItemGradeHints.resolve(explicit, sourceRowId)
                ?: q.resolveItemGrade(snapshotId, sourceRowId)
        }.getOrNull()
    }

    fun selectDropItem(item: GameItem) {
        selectedDropItem = item
        dropItemDetail = null
        dropItemSources = Load.Loading
        scope.launch {
            val offline = read { q, snapshotId ->
                q.itemDropSources(snapshotId, item.sourceRowId, item.name)
            }
            dropItemSources = when (offline) {
                is Load.Ok -> offline
                else -> Load.Ok(emptyList())
            }
            if (offline is Load.Ok && offline.value.isNotEmpty()) return@launch
            dropFetching = true
            dropItemDetail = Load.Loading
            val loaded = withContext(Dispatchers.IO) {
                runCatching { resolveItemDropOverlay(item) }
            }
            dropFetching = false
            dropItemDetail = loaded.fold(
                onSuccess = { overlay ->
                    if (overlay == null) {
                        Load.Err("No offline or Questlog data for ${item.name ?: item.sourceRowId}")
                    } else {
                        Load.Ok(overlay)
                    }
                },
                onFailure = { Load.Err(it.message ?: "drop lookup failed") },
            )
        }
    }

    fun selectDropMonster(monster: MonsterProfile) {
        selectedDropMonster = monster
        dropNpcDetail = null
        dropMonsterSources = Load.Loading
        scope.launch {
            val offline = read { q, snapshotId -> q.monsterDrops(snapshotId, monster.sourceRowId) }
            dropMonsterSources = when (offline) {
                is Load.Ok -> offline
                else -> Load.Ok(emptyList())
            }
            if (offline is Load.Ok && offline.value.isNotEmpty()) return@launch
            dropFetching = true
            dropNpcDetail = Load.Loading
            val loaded = withContext(Dispatchers.IO) {
                runCatching { CommunityMetaClient().fetchNpc(monster.sourceRowId) }
            }
            dropFetching = false
            dropNpcDetail = loaded.fold(
                onSuccess = { npc ->
                    if (npc == null) {
                        Load.Err("No offline or Questlog loot table for ${monster.sourceRowId}")
                    } else {
                        Load.Ok(npc)
                    }
                },
                onFailure = { Load.Err(it.message ?: "loot table fetch failed") },
            )
        }
    }

    private fun resolveItemDropOverlay(item: GameItem): QuestlogItemOverlay? {
        val client = CommunityMetaClient()
        client.fetchItem(item.sourceRowId)?.let { return it }
        val name = item.name?.trim().orEmpty()
        if (name.isEmpty()) return null
        val hit = client.searchEntities(name, extendSearch = false)
            .firstOrNull { it.detail?.startsWith("item") == true && TextNorm.likelySame(it.name, name) }
        val questlogId = hit?.entityId?.takeIf { it.isNotBlank() }
        return questlogId?.let { client.fetchItem(it) }
    }

    fun syncDropCache() {
        if (dropSyncRunning) return
        dropSyncRunning = true
        dropSyncMessage = null
        dropSyncProgress = DropSyncProgress("Starting", 0, 1)
        scope.launch {
            try {
                val db = database ?: error(openError ?: "database unavailable")
                val ids = dbLock.withLock {
                    withContext(Dispatchers.IO) {
                        val q = requireQuery()
                        val snapshotId = q.activeSnapshotId()
                            ?: error("No active snapshot. Import a warehouse first.")
                        snapshotId to q.monsterIds(snapshotId)
                    }
                }
                val (snapshotId, monsterIds) = ids
                val result = dbLock.withLock {
                    withContext(Dispatchers.IO) {
                        DropCacheSync().sync(
                            db,
                            snapshotId,
                            monsterIds,
                            onProgress = { progress ->
                                scope.launch(Dispatchers.Main.immediate) { dropSyncProgress = progress }
                            },
                        )
                    }
                }
                refreshDropCacheStats()
                loadDropBrowse()
                dropSyncMessage = "Synced ${result.monstersSynced} monsters, ${result.dropRows} drop rows."
                if (result.warnings.isNotEmpty()) {
                    dropSyncMessage = (dropSyncMessage ?: "") + " " + result.warnings.first()
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                dropSyncMessage = t.message ?: "Drop sync failed"
            }
            dropSyncRunning = false
            dropSyncProgress = null
        }
    }

    private fun refreshDropCacheStats() {
        scope.launch {
            dropCacheStats = when (val loaded = read { q, snapshotId -> q.dropCacheStats(snapshotId) }) {
                is Load.Ok -> loaded.value
                else -> null
            }
        }
    }

    private fun loadDropBrowse() {
        refreshDropCacheStats()
        scope.launch {
            when (dropMode) {
                DropLookupMode.Item -> {
                    dropItems = Load.Loading
                    dropItems = read { q, snapshotId ->
                        q.dropSearchItems(snapshotId, dropSearch.takeIf { it.isNotBlank() })
                    }
                }
                DropLookupMode.Monster -> {
                    dropMonsters = Load.Loading
                    dropMonsters = read { q, snapshotId ->
                        q.monsters(snapshotId, dropSearch.takeIf { it.isNotBlank() })
                    }
                }
            }
        }
    }

    fun selectCharacter(id: String) {
        clearGearSuggestions()
        selectedCharacterId = id
        characterSheet = Load.Loading
        scope.launch {
            val loaded = guard {
                val q = requireQuery()
                dbLock.withLock {
                    withContext(Dispatchers.IO) {
                        val snapshotId = q.activeSnapshotId()
                        q.resolveCharacter(id, snapshotId) ?: error("character $id not found")
                    }
                }
            }
            characterSheet = loaded
            val resolved = (loaded as? Load.Ok)?.value
            if (resolved != null) {
                val next = CharacterSheetJson.fromResolved(resolved)
                if (characterDraft?.id != next.id || !characterDraftDirty) {
                    characterDraft = next
                    characterDraftDirty = false
                }
                if (!classChosenByUser) {
                    selectedClassKey = matchClassKey(resolved.weaponClass)
                }
            }
            if (screen == Screen.Build) refreshAdvice()
        }
    }

    fun updateCharacterDraft(next: CharacterSheetJson.Draft, classEdited: Boolean = false) {
        characterDraft = applyClass(next, classEdited)
        characterDraftDirty = true
    }

    fun classSuggestion(draft: CharacterSheetJson.Draft): WeaponClassMatch {
        val q = query ?: return WeaponClassResolver.resolve(emptyList(), null, null)
        val snapshotId = q.activeSnapshotId()
        val main = draft.weapons.firstOrNull { it.slot == "main" }?.name
        val offhand = draft.weapons.firstOrNull { it.slot == "offhand" }?.name
        return q.suggestClass(snapshotId, main, offhand)
    }

    fun knownClassNames(): List<String> {
        val q = query ?: return CommunityWeaponClasses.names()
        return q.knownClassNames(q.activeSnapshotId())
    }

    fun useSuggestedClass() {
        val draft = characterDraft ?: return
        val suggestion = classSuggestion(draft)
        val name = suggestion.name ?: return
        updateCharacterDraft(
            draft.copy(className = name, classSource = suggestion.source.orEmpty()),
            classEdited = false,
        )
    }

    private fun applyClass(next: CharacterSheetJson.Draft, classEdited: Boolean): CharacterSheetJson.Draft {
        val suggestion = classSuggestion(next)
        if (classEdited) {
            val source = if (suggestion.name != null && WeaponClassResolver.sameTitle(suggestion.name, next.className)) {
                suggestion.source.orEmpty()
            } else {
                ClassSource.MANUAL
            }
            return next.copy(classSource = source)
        }
        if (ClassSource.isManual(next.classSource)) return next
        if (!suggestion.pairResolved) return next
        return next.copy(
            className = suggestion.name.orEmpty(),
            classSource = suggestion.source.orEmpty(),
        )
    }

    /**
     * Typeahead against the active warehouse. No snapshot means an empty list, not an
     * error — the user can still type a name by hand.
     */
    fun onGearQuery(fieldId: String, text: String, slot: String?, layer: String? = null) {
        characterSuggestField = fieldId
        val raw = text.trim()
        if (raw.length < 2) {
            gearSuggestSeq++
            gearSuggestJob?.cancel()
            characterSuggestions = emptyList()
            characterSuggestionsReady = false
            return
        }
        val seq = ++gearSuggestSeq
        characterSuggestionsReady = false
        gearSuggestJob?.cancel()
        gearSuggestJob = scope.launch {
            delay(180)
            val hits = try {
                dbLock.withLock {
                    withContext(Dispatchers.IO) {
                        val q = query ?: return@withContext emptyList()
                        val snapshotId = q.activeSnapshotId() ?: return@withContext emptyList()
                        if (layer != null) q.suggestBuildLayer(snapshotId, raw, layer)
                        else q.suggestGear(snapshotId, raw, slot)
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                emptyList()
            }
            if (seq != gearSuggestSeq) return@launch
            characterSuggestions = hits
            characterSuggestionsReady = true
        }
    }

    fun onGearFieldBlur(fieldId: String) {
        scope.launch {
            delay(120)
            if (characterSuggestField == fieldId) clearGearSuggestions()
        }
    }

    fun clearGearSuggestions() {
        gearSuggestSeq++
        gearSuggestJob?.cancel()
        characterSuggestField = null
        characterSuggestions = emptyList()
        characterSuggestionsReady = false
    }

    fun saveCharacterDraft() {
        val draft = characterDraft
            ?: (characterSheet as? Load.Ok)?.value?.let { CharacterSheetJson.fromResolved(it) }
            ?: return
        runImport("Character") { db ->
            val locator = CharacterLocator()
            locator.prepareHome()
            val path = locator.find() ?: locator.charactersDir().resolve("character.json")
            val sheet = (characterSheet as? Load.Ok)?.value?.sheet
            val json = CharacterSheetJson.write(draft, sheet, Instant.now().toString())
            Files.createDirectories(path.parent)
            Files.writeString(path, json)
            locator.remember(path)
            characterDraftDirty = false
            listOf(ManualImportDataSource().importInto(db, ImportRequest(path = path.toString(), content = json)))
        }
    }

    /**
     * Imports run through the same `DataSource` adapters the CLI uses. The UI adds no
     * mapping of its own; it only chooses a file and reports the receipt.
     */
    fun importWarehouse(path: Path) = runImport("Game data", trackProgress = true) { db ->
        val onProgress: (ImportProgress) -> Unit = { progress ->
            scope.launch(Dispatchers.Main.immediate) { importProgress = progress }
        }
        listOf(
            TLHelperDataSource().importInto(
                db,
                ImportRequest(path = path.toString(), activate = true, onProgress = onProgress),
            ),
        )
    }

    fun importWarehouseNow() {
        val path = warehouseSetup?.warehousePath
            ?: patchWatch?.warehouse?.path
            ?: detectedWarehouse
            ?: return
        if (StarterBootstrap.isStarterWarehousePath(path.toString())) return
        importWarehouse(path)
    }

    fun importReadyWarehouse() {
        val path = patchWatch?.warehouse?.path ?: return
        importWarehouse(path)
    }

    /**
     * Installs TL-Helper from the bundled copy or GitHub when no checkout
     * is on disk. Does not start extract.
     */
    fun openTLHelperDownload() {
        ensureTLHelperInstalled(thenRun = false)
    }

    fun runTLHelper(checkout: Path? = null) {
        val locator = TLHelperLocator()
        val found = checkout ?: locator.find()
        if (found == null) {
            ensureTLHelperInstalled(thenRun = true)
            return
        }
        launchTLHelper(locator, found)
    }

    private fun ensureTLHelperInstalled(thenRun: Boolean) {
        scope.launch {
            tlHelperMessage = "Installing TL-Helper…"
            val result = withContext(Dispatchers.IO) { TLHelperInstaller().install() }
            val locator = TLHelperLocator()
            val root = result.getOrNull() ?: run {
                tlHelperMessage = result.exceptionOrNull()?.message ?: TLHelperLauncher.MISSING_CHECKOUT
                runCatching {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(TLHelperLocator.CHECKOUT_URL))
                }
                FilePickers.pickDirectory(
                    "Select the downloaded TL-Helper folder",
                    Path.of("D:", "TL_Helper"),
                )
            }
            if (root == null || !locator.isCheckout(root)) {
                if (tlHelperMessage == "Installing TL-Helper…") {
                    tlHelperMessage = TLHelperLauncher.MISSING_CHECKOUT
                }
                return@launch
            }
            locator.remember(root)
            tlHelperCheckout = root
            if (thenRun) {
                launchTLHelper(locator, root)
            } else {
                tlHelperMessage = "TL-Helper is ready at $root"
            }
        }
    }

    private fun launchTLHelper(locator: TLHelperLocator, root: Path) {
        val buildId = patchWatch?.installedBuild
            ?: (overview as? Load.Ok)?.value?.installedBuild
        val result = TLHelperLauncher(locator = locator).launch(root, buildId)
        tlHelperMessage = result.fold(
            onSuccess = {
                "Opened a Command Prompt titled \"${TLHelperLauncher.WINDOW_TITLE}\". " +
                    "If extract fails, that window stays open with the error. " +
                    "Solisium will import the warehouse when it appears."
            },
            onFailure = { it.message ?: "Could not start TL-Helper." },
        )
        if (result.isSuccess) watchForNewWarehouse()
    }

    fun dismissTLHelperMessage() {
        tlHelperMessage = null
    }

    fun dismissCatalogSyncNote() {
        catalogSyncNote = null
    }

    fun dismissWarehouseSetup() {
        warehouseSetup = null
        markWarehouseSetupComplete()
    }

    fun dismissDropSyncOffer() {
        dropSyncOffer = null
    }

    fun acceptDropSyncOffer() {
        dropSyncOffer = null
        go(Screen.Drops)
        syncDropCache()
    }

    private fun maybeOfferDropSyncAfterImport() {
        scope.launch {
            refreshDropCacheStats()
            val stats = dropCacheStats ?: return@launch
            if (stats.monstersTotal == 0L || dropSyncRunning) return@launch
            val hasExtracted = stats.extractedDropRows > 0L
            val needsCommunitySync = stats.monstersSynced < stats.monstersTotal
            if (!hasExtracted && (stats.dropRows == 0L || needsCommunitySync)) {
                dropSyncOffer = DropSyncOffer(
                    monstersTotal = stats.monstersTotal,
                    dropRows = stats.dropRows,
                )
            }
        }
    }

    fun acceptWarehouseSetup() {
        importWarehouseNow()
    }

    private fun startPatchWatch() {
        scope.launch {
            delay(1_500)
            checkPatch(autoImport = false)
            refreshExtractProgress()
            val marker = Path.of(System.getProperty("user.home"), ".solisium", "tl-extract.json")
            if (Files.isRegularFile(marker)) watchForNewWarehouse()
            maybeOfferWarehouseSetup()
            while (true) {
                delay(patchWatchInterval())
                checkPatch(autoImport = warehouseSetupComplete())
            }
        }
    }

    /**
     * After the user starts TL-Helper extract, poll often enough to import the
     * new warehouse when it lands and drop the stale-data banners.
     */
    private fun watchForNewWarehouse() {
        warehouseWatchJob?.cancel()
        warehouseWatchJob = scope.launch {
            repeat(EXTRACT_WATCH_ATTEMPTS) {
                refreshExtractProgress()
                val build = patchWatch?.installedBuild
                    ?: (overview as? Load.Ok)?.value?.installedBuild
                tlHelperLastRun = withContext(Dispatchers.IO) {
                    runCatching { TLHelperRunLog().latest(build) }.getOrNull()
                }
                val progress = extractProgress
                if (progress?.failed == true) {
                    tlHelperMessage = listOfNotNull(
                        progress.label,
                        tlHelperLastRun?.takeUnless { it.succeeded }?.summary(),
                    ).joinToString(" ")
                }
                if (!importing && it % 10 == 0) {
                    checkPatch(autoImport = true)
                }
                if (patchWatch?.state == PatchWatchState.CURRENT) {
                    tlHelperMessage = null
                    refreshExtractProgress()
                    refreshOverview()
                    return@launch
                }
                delay(EXTRACT_PROGRESS_MS)
            }
        }
    }

    private suspend fun refreshExtractProgress() {
        val build = patchWatch?.installedBuild
            ?: (overview as? Load.Ok)?.value?.installedBuild
            ?: return
        extractProgress = withContext(Dispatchers.IO) {
            runCatching { TLHelperExtractProgress().inspect(build) }.getOrNull()
        }
    }

    private fun patchWatchInterval(): Long =
        System.getenv("SOLISIUM_PATCH_WATCH_MS")?.toLongOrNull()?.takeIf { it >= 5_000L }
            ?: PatchWatch.DEFAULT_INTERVAL_MS

    private suspend fun checkPatch(autoImport: Boolean) {
        if (importing) return
        val q = query ?: return
        val active = try {
            dbLock.withLock { withContext(Dispatchers.IO) { q.snapshotService().active() } }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            return
        }
        val report = try {
            withContext(Dispatchers.IO) {
                PatchWatch(lastPakFingerprint = { readPakCache() }).inspect(active)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            return
        }
        patchWatch = report
        tlHelperLastRun = withContext(Dispatchers.IO) {
            runCatching { TLHelperRunLog().latest(report.installedBuild) }.getOrNull()
        }
        report.pakFingerprint?.let { writePakCache(it) }
        if (autoImport && report.canImport && warehouseSetupComplete()) {
            val path = report.warehouse?.path ?: return
            if (!StarterBootstrap.isStarterWarehousePath(path.toString())) {
                importWarehouse(path)
            }
        }
    }

    private val warehouseSetupMarker: Path
        get() = Path.of(System.getProperty("user.home"), ".solisium", "warehouse-setup.complete")

    private fun warehouseSetupComplete(): Boolean =
        runCatching { Files.exists(warehouseSetupMarker) }.getOrDefault(false)

    private fun markWarehouseSetupComplete() {
        runCatching {
            Files.createDirectories(warehouseSetupMarker.parent)
            Files.writeString(
                warehouseSetupMarker,
                "Warehouse setup was completed or skipped. Delete this file to see the first-run prompt again.\n",
            )
        }
    }

    private suspend fun maybeOfferWarehouseSetup() {
        if (warehouseSetupComplete()) return
        for (attempt in 0 until 25) {
            if (overview is Load.Ok) break
            delay(200)
        }
        val snapshot = (overview as? Load.Ok)?.value?.snapshot
        val demoActive = snapshot?.let { StarterBootstrap.isStarterWarehousePath(it.sourcePath) } == true
        val realWarehouse = patchWatch?.warehouse?.takeUnless {
            StarterBootstrap.isStarterWarehousePath(it.path.toString())
        } ?: detectedWarehouse?.takeUnless {
            StarterBootstrap.isStarterWarehousePath(it.toString())
        }?.let { path ->
            WarehouseRef(path = path, buildId = null, lastModifiedMillis = 0, sizeBytes = 0)
        }
        val canImport = patchWatch?.canImport == true && realWarehouse != null
        val shouldOffer = canImport || (demoActive && realWarehouse != null) || realWarehouse != null
        if (!shouldOffer) {
            markWarehouseSetupComplete()
            return
        }
        warehouseSetup = WarehouseSetupOffer(
            warehousePath = realWarehouse?.path,
            warehouseBuild = realWarehouse?.buildId ?: patchWatch?.warehouse?.buildId,
            demoActive = demoActive,
            canImport = canImport,
            reason = patchWatch?.reason,
        )
    }

    private fun pakCachePath(): Path =
        Path.of(System.getProperty("user.home"), ".solisium", "patch-watch.pak")

    private fun readPakCache(): String? =
        runCatching { Files.readString(pakCachePath()).trim().takeIf { it.isNotEmpty() } }.getOrNull()

    private fun writePakCache(fingerprint: String) {
        runCatching {
            val path = pakCachePath()
            Files.createDirectories(path.parent)
            Files.writeString(path, fingerprint)
        }
    }

    fun importCombatLogs(path: Path?) = runImport("Combat logs") { db ->
        val selection = CombatLogPaths.selectForImport(path?.toString())
        val source = CombatLogDataSource()
        selection.files.mapIndexed { index, file ->
            val receipt = source.importInto(
                db,
                ImportRequest(path = file.toString(), content = Files.readString(file)),
            )
            if (index == 0 && selection.warnings.isNotEmpty()) {
                receipt.copy(warnings = receipt.warnings + selection.warnings)
            } else {
                receipt
            }
        }
    }

    fun importCharacter(path: Path) = importCharacters(listOf(path))

    fun importDetectedCharacters() {
        val locator = CharacterLocator()
        runCatching { locator.prepareHome() }
        val files = locator.findImportable()
        if (files.isEmpty()) return
        importCharacters(files)
    }

    fun importCharacters(paths: List<Path>) {
        if (paths.isEmpty()) return
        runImport("Character") { db ->
            val locator = CharacterLocator()
            val source = ManualImportDataSource()
            paths.map { path ->
                val receipt = source.importInto(
                    db,
                    ImportRequest(path = path.toString(), content = Files.readString(path)),
                )
                locator.remember(path)
                receipt
            }
        }
    }

    private fun runImport(
        label: String,
        trackProgress: Boolean = false,
        block: (SolisiumDatabase) -> List<ImportReceipt>,
    ) {
        if (importing) return
        importing = true
        lastImport = null
        if (trackProgress) {
            importProgress = ImportProgress("Starting import", 0, 0)
        }
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
            importProgress = null
            if (label == "Combat logs") {
                refreshCombatLogDiscovery()
            }
            if (label == "Game data" && outcome.error == null) {
                warehouseSetup = null
                markWarehouseSetupComplete()
                query?.invalidateGradeCache()
                maybeOfferDropSyncAfterImport()
                pendingCatalogConfirm = true
                warehouseWatchJob?.cancel()
                checkPatch(autoImport = false)
            }
            refreshOverview()
            loadSnapshots()
            loadRows()
            loadCombat()
            loadTalkingWall()
            loadCharacters()
            refreshCharacterDetection()
            val importedId = outcome.receipts.mapNotNull { it.characterId }.lastOrNull()
            if (label == "Character" && (importedId != null || selectedCharacterId != null)) {
                selectCharacter(importedId ?: selectedCharacterId!!)
            }
            refreshAdvice()
        }
    }

    private fun refreshCharacterDetection() {
        val locator = CharacterLocator()
        runCatching { locator.prepareHome() }
        detectedCharacterFiles = runCatching { locator.findImportable() }.getOrDefault(emptyList())
    }

    fun pickGoal(next: BuildGoal) {
        if (next == goal) return
        goal = next
        narration = null
        community = metaByGoal[next]?.let { Load.Ok(it) }
        refreshAdvice()
    }

    fun pickClass(option: BuildClassOption?) {
        selectedClassKey = option?.key
        classChosenByUser = true
        classQuery = ""
        refreshAdvice()
    }

    fun onClassQuery(text: String) {
        classQuery = text
    }

    fun selectedClassOption(): BuildClassOption? =
        selectedClassKey?.let { key -> buildClasses.firstOrNull { it.key == key } }

    private fun matchClassKey(match: WeaponClassMatch?): String? {
        if (match == null) return null
        return buildClasses.firstOrNull { option ->
            option.key == WeaponTypeLabel.pairKey(match.weaponA, match.weaponB) ||
                WeaponClassResolver.sameTitle(option.name, match.name)
        }?.key
    }

    fun toggleAxis(axis: StatAxis) {
        axes = if (axis in axes) axes - axis else axes + axis
        refreshAdvice()
    }

    fun addExtraStat(key: String) {
        if (key.isBlank()) return
        extraStatKeys = extraStatKeys + key
        extraStatQuery = ""
        refreshAdvice()
    }

    fun removeExtraStat(key: String) {
        extraStatKeys = extraStatKeys - key
        refreshAdvice()
    }

    fun onExtraStatQuery(text: String) {
        extraStatQuery = text
    }

    fun onDesiredCombatPower(text: String) {
        desiredCombatPowerText = text
        desiredJob?.cancel()
        desiredJob = scope.launch {
            delay(180)
            refreshAdvice()
        }
    }

    fun onDesiredGearScore(text: String) {
        desiredGearScoreText = text
        desiredJob?.cancel()
        desiredJob = scope.launch {
            delay(180)
            refreshAdvice()
        }
    }

    fun refreshAdvice() {
        val currentGoal = goal
        val characterId = selectedCharacterId
        val cachedCommunity = metaByGoal[currentGoal] ?: (community as? Load.Ok)?.value
        val selectedAxes = axes.toList()
        val extras = extraStatKeys
        val classKey = selectedClassKey
        val classLocked = classChosenByUser
        val desiredCp = parseTypedLong(desiredCombatPowerText)
        val desiredGs = parseTypedLong(desiredGearScoreText)
        scope.launch {
            plan = Load.Loading
            advice = Load.Loading
            val loaded = read { q, snapshotId ->
                val labels = StatKeyLabel.map(q.statKeys(snapshotId))
                val options = q.buildClassOptions(snapshotId)
                val classOption = if (classLocked) {
                    classKey?.let { key -> options.firstOrNull { it.key == key } }
                } else {
                    val fromCharacter = characterId?.let { q.resolveCharacter(it, snapshotId)?.weaponClass }
                    q.findBuildClass(snapshotId, match = fromCharacter, key = classKey)
                }
                val planned = DesiredBuildPlanner(q).plan(
                    snapshotId = snapshotId,
                    goal = currentGoal,
                    characterId = characterId,
                    community = cachedCommunity,
                    desiredCombatPower = desiredCp,
                    desiredGearScore = desiredGs,
                    axes = selectedAxes,
                    extraKeys = extras,
                    classOption = classOption,
                )
                Triple(labels, planned, options)
            }
            when (loaded) {
                is Load.Ok -> {
                    availableStatKeys = loaded.value.first.toList()
                    buildClasses = loaded.value.third
                    plan = Load.Ok(loaded.value.second)
                    advice = Load.Ok(loaded.value.second.advice)
                    if (!classLocked) {
                        selectedClassKey = loaded.value.second.selectedClass?.key
                    }
                }
                is Load.Err -> {
                    plan = Load.Err(loaded.message)
                    advice = Load.Err(loaded.message)
                }
                Load.Loading -> Unit
            }
            maybeNarrate()
        }
    }

    /**
     * Talks to Questlog tRPC and TLDB only because the user asked. Results overlay
     * the extracted ranks; they never replace warehouse numbers. Cached per goal so
     * switching Tank does not keep a bow search overlay.
     */
    fun refreshMeta() {
        if (metaRefreshing) return
        metaRefreshing = true
        val currentGoal = goal
        scope.launch {
            if (goal == currentGoal) community = Load.Loading
            val fetched = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = CommunityMetaClient().fetch(currentGoal)
                    bindCommunity(raw)
                }
            }
            fetched.onSuccess { snap -> metaByGoal[currentGoal] = snap }
            if (goal == currentGoal) {
                community = fetched.fold(
                    onSuccess = { Load.Ok(it) },
                    onFailure = { Load.Err(it.message ?: "community fetch failed") },
                )
                metaRefreshing = false
                refreshAdvice()
            } else {
                metaRefreshing = false
            }
        }
    }

    fun onQuestlogSlug(text: String) {
        questlogSlug = text
    }

    /**
     * Pastes a Questlog character-builder slug or URL. Listing is not public (403);
     * a missing slug returns NOT_FOUND, not a crash.
     */
    fun loadQuestlogCharacter() {
        val slug = CommunityMetaClient.slugFromInput(questlogSlug)
        if (slug.isEmpty() || characterFetching) return
        characterFetching = true
        val currentGoal = goal
        val prior = metaByGoal[currentGoal] ?: (community as? Load.Ok)?.value
        scope.launch {
            val fetched = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = CommunityMetaClient().fetchCharacter(slug, prior)
                    bindCommunity(raw)
                }
            }
            fetched.onSuccess { snap ->
                metaByGoal[currentGoal] = snap
                if (goal == currentGoal) community = Load.Ok(snap)
            }.onFailure { err ->
                if (goal == currentGoal) {
                    val existing = (community as? Load.Ok)?.value
                    community = if (existing != null) {
                        Load.Ok(existing.copy(warnings = existing.warnings + (err.message ?: "character fetch failed")))
                    } else {
                        Load.Err(err.message ?: "character fetch failed")
                    }
                }
            }
            characterFetching = false
            if (goal == currentGoal) {
                refreshAdvice()
            }
        }
    }

    private suspend fun bindCommunity(raw: CommunitySnapshot): CommunitySnapshot {
        val q = query ?: return raw
        return dbLock.withLock {
            val snapshotId = q.activeSnapshotId() ?: return@withLock raw
            CommunityOverlay.bind(raw, q, snapshotId)
        }
    }

    private fun maybeNarrate() {
        val current = (advice as? Load.Ok)?.value ?: return
        scope.launch {
            narration = withContext(Dispatchers.IO) {
                runCatching { OllamaNarrator().explain(current) }.getOrNull()
            }
        }
    }

    private fun loadCharacters() {
        scope.launch {
            val loaded = guard {
                val q = requireQuery()
                dbLock.withLock { withContext(Dispatchers.IO) { q.characters() } }
            }
            characters = loaded
            val none = (loaded as? Load.Ok)?.value?.isEmpty() == true
            if (none && !attemptedCharacterAutoImport && detectedCharacter != null && !importing) {
                attemptedCharacterAutoImport = true
                importDetectedCharacters()
                return@launch
            }
            // Most people track one character, so an extra click to see it is pure friction.
            val only = (loaded as? Load.Ok)?.value?.singleOrNull()
            if (only != null && selectedCharacterId == null) selectCharacter(only.id)
            if (screen == Screen.Build) refreshAdvice()
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
            when (val warehouse = read { q, snapshotId ->
                q.itemDetail(snapshotId, row.sourceTable, row.sourceRowId)
            }) {
                is Load.Err -> {
                    detail = warehouse
                    return@launch
                }
                is Load.Loading -> return@launch
                is Load.Ok -> {
                    val base = warehouse.value
                    val questlog = withContext(Dispatchers.IO) {
                        runCatching { CommunityMetaClient().fetchItem(row.sourceRowId) }
                    }
                    detail = Load.Ok(
                        RowDetail(
                            row = row.copy(
                                grade = base.grade ?: row.grade,
                            ),
                            stats = base.warehouseStats,
                            curves = base.curves,
                            curvePoints = base.curvePoints,
                            combatPower = base.combatPower,
                            questlog = questlog.getOrNull(),
                            questlogWarning = questlog.exceptionOrNull()?.message,
                            category = base.category,
                        ),
                    )
                }
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
                discoveredInfluences = snapshot?.let {
                    dbLock.withLock { withContext(Dispatchers.IO) { q.discoveredInfluences(it.id) } }
                }.orEmpty()
                val warning = BuildMismatch.warning(installed, snapshot?.gameBuild)
                if (pendingCatalogConfirm) {
                    pendingCatalogConfirm = false
                    val current = patchWatch?.state == null ||
                        patchWatch?.state == PatchWatchState.CURRENT
                    catalogSyncNote = if (snapshot != null && warning == null && current) {
                        val fresh = discoveredInfluences.count { it.newThisPatch }
                        buildString {
                            append("Imported build ${snapshot.gameBuild}. Matches the installed game.")
                            if (fresh > 0) {
                                append(" $fresh new influence ")
                                append(if (fresh == 1) "family" else "families")
                                append(" in this warehouse.")
                            }
                        }
                    } else {
                        null
                    }
                    if (catalogSyncNote != null) tlHelperMessage = null
                }
                Overview(
                    snapshot = snapshot,
                    counts = counts,
                    installedBuild = installed,
                    buildWarning = warning,
                )
            }
        }
    }

    /** Mean observed DPS across imported combat sessions (for farm time hints). */
    fun observedCombatDps(): Double? {
        val sessions = (combat as? Load.Ok)?.value.orEmpty()
        return sessions.mapNotNull { it.observedDps }.average().takeIf { !it.isNaN() && it > 0 }
    }

    fun onWallSearch(text: String) {
        wallSearch = text
        selectedWallStatement = null
        wallSearchJob?.cancel()
        wallSearchJob = scope.launch {
            delay(200)
            loadTalkingWall()
        }
    }

    fun onWallCategory(category: String?) {
        wallCategory = category
        loadTalkingWall()
    }

    fun selectWallStatement(row: TalkingWallStatement) {
        selectedWallStatement = row
    }

    fun pickEventServer(server: GameServer) {
        eventServer = server
        eventServerQuery = ""
        persistEventServer(server)
        refreshEventPlan()
    }

    fun onEventServerQuery(value: String) {
        eventServerQuery = value
    }

    fun commitEventServerQuery() {
        val typed = eventServerQuery.trim()
        if (typed.isEmpty()) return
        val server = GameServers.find(typed) ?: GameServers.custom(typed, eventServer)
        pickEventServer(server)
    }

    fun pickEventDay(offset: Int) {
        eventDayOffset = offset.coerceIn(0, 6)
        refreshEventPlan()
    }

    fun characterServerHint(): String? {
        val id = selectedCharacterId
        val list = (characters as? Load.Ok)?.value.orEmpty()
        return list.firstOrNull { it.id == id }?.server?.takeIf { it.isNotBlank() }
            ?: list.firstOrNull { !it.server.isNullOrBlank() }?.server
    }

    fun refreshEventCatalog() {
        if (eventRefreshing) return
        eventRefreshing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { CommunityMetaClient().fetchEventCatalog() }
            }
            result.onSuccess { catalog ->
                eventCatalog = catalog
                eventCatalogWarnings = emptyList()
                eventFetchedAt = Instant.now().toString()
            }.onFailure { error ->
                eventCatalogWarnings = listOf("Questlog event catalog failed: ${error.message}")
            }
            eventRefreshing = false
            refreshEventPlan()
        }
    }

    private fun suggestEventServerFromCharacter() {
        if (Files.isRegularFile(eventServerFile())) return
        val hint = characterServerHint() ?: return
        GameServers.find(hint)?.let { eventServer = it }
    }

    private fun refreshEventPlan() {
        scope.launch {
            eventPlan = Load.Loading
            val warehouse = when (
                val loaded = read { q, snapshotId ->
                    q.monsters(snapshotId, null, 400).filter {
                        it.kindHint in setOf("field boss", "world boss", "guild raid")
                    }
                }
            ) {
                is Load.Ok -> loaded.value
                else -> emptyList()
            }
            eventPlan = Load.Ok(
                EventTimelineBuilder().plan(
                    server = eventServer,
                    dayOffset = eventDayOffset,
                    catalog = eventCatalog,
                    warehouse = warehouse,
                    fetchedAt = eventFetchedAt,
                    warnings = eventCatalogWarnings,
                ),
            )
        }
    }

    private fun persistEventServer(server: GameServer) {
        runCatching {
            val file = eventServerFile()
            Files.createDirectories(file.parent)
            Files.writeString(file, server.key)
        }
    }

    private fun eventServerFile(): Path =
        Path.of(System.getProperty("user.home"), ".solisium", "event-server.txt")

    private fun readStoredEventServer(): GameServer {
        val key = runCatching {
            val file = eventServerFile()
            if (Files.isRegularFile(file)) Files.readString(file).trim() else ""
        }.getOrDefault("")
        return GameServers.find(key) ?: GameServers.default
    }

    private fun loadTalkingWall() {
        scope.launch {
            wallStatements = Load.Loading
            val loaded = read { q, snapshotId ->
                q.ensureTalkingWallCommunity(snapshotId, TalkingWallResources.communityJson())
                val snapshots = q.snapshots()
                val previousId = snapshots.drop(1).firstOrNull()?.id
                Triple(
                    q.talkingWallCoverage(snapshotId),
                    q.talkingWallDelta(snapshotId, previousId),
                    q.searchTalkingWall(
                        snapshotId,
                        wallSearch.takeIf { it.isNotBlank() },
                        wallCategory,
                    ),
                )
            }
            when (loaded) {
                is Load.Ok -> {
                    wallCoverage = loaded.value.first
                    wallDelta = loaded.value.second
                    wallStatements = Load.Ok(loaded.value.third)
                    val rows = loaded.value.third
                    if (selectedWallStatement != null && rows.none {
                            it.sourceTable == selectedWallStatement!!.sourceTable &&
                                it.sourceRowId == selectedWallStatement!!.sourceRowId
                        }
                    ) {
                        selectedWallStatement = rows.firstOrNull()
                    } else if (selectedWallStatement == null) {
                        selectedWallStatement = rows.firstOrNull()
                    }
                }
                is Load.Err -> wallStatements = loaded
                Load.Loading -> Unit
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
            val loaded = read { q, snapshotId -> fetch(q, snapshotId, target, term) }
            if (loaded is Load.Ok) {
                browseTotal = loaded.value.size
                val page = loaded.value.take(BROWSE_CAP)
                rows = Load.Ok(page)
                val stillVisible = selected?.let { current ->
                    page.any { it.sourceTable == current.sourceTable && it.sourceRowId == current.sourceRowId }
                } == true
                if (!stillVisible) {
                    selected = null
                    detail = null
                    page.firstOrNull()?.let { select(it) }
                }
            } else {
                browseTotal = 0
                rows = loaded
            }
        }
    }

    private fun fetch(
        q: CatalogQuery,
        snapshotId: String,
        target: CatalogKind,
        term: String?,
    ): List<CatalogRow> {
        val searching = !term.isNullOrBlank()
        fun emit(
            name: String?,
            table: String,
            id: String,
            meta: String?,
            grade: String? = null,
            looksOnly: Boolean = false,
        ): CatalogRow? {
            if (looksOnly && !DisplayName.isItemLooks(table)) return null
            val display = DisplayName.of(name, id)
            if (display == null && !searching) return null
            return CatalogRow(
                name = display ?: id,
                sourceTable = table,
                sourceRowId = id,
                meta = meta,
                grade = grade,
                named = display != null,
            )
        }
        fun gradeFor(id: String, explicit: String? = null): String? =
            explicit?.takeIf { it.isNotBlank() } ?: q.resolveItemGrade(snapshotId, id)
        return when (target) {
            CatalogKind.Items -> q.items(snapshotId, term).mapNotNull { item ->
                if (!GearCatalogFilter.isGearListRow(
                        item.sourceTable,
                        item.sourceRowId,
                        item.name,
                        item.category,
                    )
                ) {
                    return@mapNotNull null
                }
                emit(
                    item.name,
                    item.sourceTable,
                    item.sourceRowId,
                    DisplayName.prettyEnum(item.category),
                    gradeFor(item.sourceRowId, item.grade),
                    looksOnly = true,
                )
            }
            CatalogKind.Weapons -> q.weapons(snapshotId, term).mapNotNull {
                emit(
                    it.name,
                    it.sourceTable,
                    it.sourceRowId,
                    DisplayName.prettyEnum(it.weaponType),
                    gradeFor(it.sourceRowId),
                )
            }
            CatalogKind.Armor -> q.armor(snapshotId, term).mapNotNull {
                emit(
                    it.name,
                    it.sourceTable,
                    it.sourceRowId,
                    DisplayName.prettyEnum(it.slot),
                    gradeFor(it.sourceRowId),
                )
            }
            CatalogKind.Accessories -> q.accessories(snapshotId, term).mapNotNull {
                emit(
                    it.name,
                    it.sourceTable,
                    it.sourceRowId,
                    DisplayName.prettyEnum(it.slot),
                    gradeFor(it.sourceRowId),
                )
            }
            CatalogKind.Traits -> q.traits(snapshotId, term).mapNotNull {
                emit(it.name, it.sourceTable, it.sourceRowId, null)
            }
            CatalogKind.Runes -> q.runes(snapshotId, term).mapNotNull {
                emit(
                    it.name,
                    it.sourceTable,
                    it.sourceRowId,
                    null,
                    gradeFor(it.sourceRowId, it.grade),
                )
            }
            CatalogKind.Skills -> q.skills(snapshotId, term).mapNotNull {
                emit(it.name, it.sourceTable, it.sourceRowId, DisplayName.prettyEnum(it.skillType))
            }
            CatalogKind.Effects -> q.effects(snapshotId, term).mapNotNull {
                emit(it.name, it.sourceTable, it.sourceRowId, null)
            }
            CatalogKind.Recipes -> q.recipes(snapshotId, term).mapNotNull {
                emit(it.name, it.sourceTable, it.sourceRowId, it.recipeKind)
            }
            CatalogKind.Materials -> q.materials(snapshotId, term).mapNotNull {
                emit(it.name, it.sourceTable, it.sourceRowId, null)
            }
            CatalogKind.Stats -> q.stats(snapshotId, term).mapNotNull {
                emit(it.name, it.sourceTable, it.sourceRowId, null)
            }
            CatalogKind.Formulas -> q.formulas(snapshotId, term).map {
                CatalogRow(
                    name = DisplayName.prettyEnum(it.expression) ?: it.sourceRowId,
                    sourceTable = it.sourceTable,
                    sourceRowId = it.sourceRowId,
                    meta = it.confidence,
                    named = DisplayName.prettyEnum(it.expression) != null,
                )
            }
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

    private fun parseTypedLong(raw: String): Long? {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return digits.toLongOrNull()
    }

    private fun openDatabase(): SolisiumDatabase? = try {
        JvmDatabase.openOrCreate(databasePath())
    } catch (t: Throwable) {
        openError = "Could not open ${databasePath()}: ${t.message}"
        null
    }

    companion object {
        private const val BROWSE_CAP = 400
        private const val EXTRACT_PROGRESS_MS = 1_000L
        private const val EXTRACT_WATCH_ATTEMPTS = 1_200

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
