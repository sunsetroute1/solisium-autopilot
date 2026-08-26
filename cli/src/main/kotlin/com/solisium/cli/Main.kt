package com.solisium.cli

import com.solisium.core.combat.CombatLogParser
import com.solisium.core.db.JvmDatabase
import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.domain.CharacterSheet
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.ResolvedLoadoutLine
import com.solisium.core.domain.UserCharacter
import com.solisium.core.query.BuildMismatch
import com.solisium.core.query.CatalogQuery
import com.solisium.core.secret.AesKey
import com.solisium.core.secret.ScanReport
import com.solisium.core.secret.SecretScanner
import com.solisium.core.secret.SecretStore
import com.solisium.core.source.CharacterLocator
import com.solisium.core.source.CombatLogDataSource
import com.solisium.core.source.CombatLogPaths
import com.solisium.core.source.ImportReceipt
import com.solisium.core.source.ImportRequest
import com.solisium.core.source.InstalledGameDataSource
import com.solisium.core.source.ManualImportDataSource
import com.solisium.core.source.PublicRepositoryDataSource
import com.solisium.core.source.TLHelperDataSource
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    try {
        dispatch(args)
    } catch (t: Throwable) {
        // A bad flag is a user mistake, not a crash, so report it as one line. Set
        // SOLISIUM_DEBUG=1 to get the stack trace back when diagnosing a real fault.
        System.err.println(t.message ?: t::class.simpleName ?: "failed")
        if (System.getenv("SOLISIUM_DEBUG") == "1") t.printStackTrace()
        kotlin.system.exitProcess(1)
    }
}

private fun dispatch(args: Array<String>) {
    val command = args.firstOrNull() ?: "help"
    when (command) {
        "help", "-h", "--help" -> printHelp()
        "import" -> runImport(args.drop(1))
        "query" -> runQuery(args.drop(1))
        "activate" -> runActivate(args.drop(1))
        "alias" -> runAlias(args.drop(1))
        "parse-log" -> runParseLog(args.drop(1))
        "logs" -> runLogs()
        "probe" -> runProbe()
        "detect-install" -> runDetect()
        "patch-check" -> runPatchCheck(args.drop(1))
        "keys" -> runKeys(args.drop(1))
        else -> {
            System.err.println("unknown command: $command")
            printHelp()
            kotlin.system.exitProcess(1)
        }
    }
}

private fun printHelp() {
    println(
        """
        Solisium Autopilot CLI

        solisium help
        solisium probe
        solisium import --source tl-helper [--path <warehouse.sqlite>] [--activate false]
        solisium import --source combat-log [--path <CombatLog.txt|dir>]
        solisium import --source manual [--path <character.json>]
        solisium query snapshots|counts|items|weapons|armor|accessories|traits|runes|synergies|skills|effects|formulas|recipes|materials|stats [--name <text>] [--snapshot <id-or-alias>]
        solisium query item-stats --row <item-row-id> [--snapshot <id-or-alias>]
        solisium query item-curves --row <item-row-id> [--snapshot <id-or-alias>]
        solisium query lookup --table <source_table> --row <source_row_id> [--snapshot <id-or-alias>]
        solisium query advise [--goal ranged|melee|magic|tank|support] [--class <Gladiator>] [--character <id>] [--meta] [--slug <questlog-slug>] [--desired-cp <n>] [--desired-gs <n>] [--axes hit,evasion,endurance] [--stat <key>]
        solisium query characters
        solisium query character --id <character-id> [--snapshot <id-or-alias>]
        solisium query sessions
        solisium query session --id <session-id>
        solisium activate --snapshot <id-or-alias>
        solisium alias --name t4 --snapshot <id>
        solisium parse-log --path <CombatLog.txt>
        solisium logs
        solisium detect-install
        solisium patch-check [--import]
        solisium keys list
        solisium keys scan [--path <folder>]        find a key already on this machine
        solisium keys add --name <name> [--from <folder>] [--value <hex>]
        solisium keys remove --name <name>

        Optional: --db <solisium.sqlite>   (default %USERPROFILE%\.solisium\solisium.sqlite)

        Keys are stored under %LOCALAPPDATA%\Solisium, never in this repository and
        never inside the installed application. Commands print fingerprints, not keys.
        """.trimIndent(),
    )
}

private fun runProbe() {
    val helper = TLHelperDataSource().probe()
    val install = InstalledGameDataSource().probe()
    val logs = CombatLogPaths.detect()
    val saves = CombatLogPaths.saveGamesDir()
    println("tl_helper available=${helper.available} ${helper.notes}")
    println("installed_game available=${install.available} ${install.notes}")
    println("combat_logs ${logs?.toString() ?: "folder not found under %LOCALAPPDATA%\\TL\\Saved\\CombatLogs"}")
    println(
        "savegames " +
            if (saves != null) {
                "present at $saves (hashed .sav files; not parsed, not a verified character source)"
            } else {
                "folder not found"
            },
    )
    println("character ${CharacterLocator().describe()}")
    println("public_repo import=false; Questlog/TLDB overlay is `query advise --meta` / `--slug` or the Build screen")
    val dbFile = dbPath(emptyMap())
    if (!Files.isRegularFile(dbFile)) {
        println("local_db not created yet ($dbFile)")
        return
    }
    val active = CatalogQuery(JvmDatabase.openOrCreate(dbFile)).snapshotService().active()
    if (active == null) {
        println("active_snapshot none")
        return
    }
    println("active_snapshot id=${active.id} build=${active.gameBuild} source=${active.source}")
    BuildMismatch.warning(InstalledGameDataSource().detect()?.buildId, active.gameBuild)?.let { warning ->
        println("mismatch $warning")
    }
}

private fun runImport(args: List<String>) {
    val flags = parseFlags(args)
    val source = flags["source"] ?: error("--source is required")
    val path = flags["path"]
    val activate = flags["activate"]?.equals("false", ignoreCase = true) != true
    val db = JvmDatabase.openOrCreate(dbPath(flags))
    when (source) {
        "tl-helper" -> printReceipt(
            TLHelperDataSource().importInto(db, ImportRequest(path = path, activate = activate)),
        )
        "combat-log" -> importCombatLogs(db, path, flags["character"]).forEach(::printReceipt)
        "manual" -> {
            val locator = CharacterLocator()
            val files = if (path != null) {
                listOf(Path.of(path))
            } else {
                locator.prepareHome()
                locator.findImportable()
            }
            if (files.isEmpty()) {
                error("no character json found; pass --path or put a sheet in ${locator.charactersDir()}")
            }
            files.forEach { file ->
                val receipt = ManualImportDataSource().importInto(
                    db,
                    ImportRequest(
                        path = file.toString(),
                        content = Files.readString(file),
                        activate = activate,
                        characterId = flags["character"],
                    ),
                )
                locator.remember(file)
                printReceipt(receipt)
                warnUnresolvedLoadout(db, receipt.characterId)
            }
        }
        "public-repo" -> printReceipt(PublicRepositoryDataSource().importInto(db, ImportRequest(path = path)))
        "installed-game" -> printReceipt(
            InstalledGameDataSource().importInto(db, ImportRequest(activate = activate)),
        )
        else -> error("unknown source: $source")
    }
}

private fun importCombatLogs(db: SolisiumDatabase, pathArg: String?, characterId: String?): List<ImportReceipt> {
    val files = combatLogFiles(pathArg)
    if (files.isEmpty()) {
        error("no CombatLog .txt files to import")
    }
    val source = CombatLogDataSource()
    return files.map { file ->
        source.importInto(
            db,
            ImportRequest(
                path = file.toString(),
                content = Files.readString(file),
                characterId = characterId,
            ),
        )
    }
}

private fun combatLogFiles(pathArg: String?): List<Path> {
    val selection = CombatLogPaths.selectForImport(pathArg)
    selection.warnings.forEach { System.err.println("warning: $it") }
    return selection.files
}

private fun warnUnresolvedLoadout(db: SolisiumDatabase, characterId: String?) {
    val id = characterId ?: return
    val query = CatalogQuery(db)
    val snapshot = query.snapshotService().active() ?: return
    val resolved = query.resolveCharacter(id, snapshot.id) ?: return
    if (resolved.unresolvedCount > 0) {
        println("warning: ${resolved.unresolvedCount} loadout key(s) not in snapshot build=${snapshot.gameBuild}")
    }
}

private fun printReceipt(receipt: ImportReceipt) {
    println(
        "source=${receipt.source} snapshot=${receipt.snapshotId ?: "-"} session=${receipt.sessionId ?: "-"} imported=${receipt.recordsImported} skipped=${receipt.recordsSkipped}",
    )
    receipt.warnings.forEach { println("warning: $it") }
}

private fun runQuery(args: List<String>) {
    val flags = parseFlags(args)
    val kind = flags["positional"] ?: args.firstOrNull { !it.startsWith("--") } ?: "snapshots"
    val db = JvmDatabase.openOrCreate(dbPath(flags))
    val query = CatalogQuery(db)
    val snapshotRef = flags["snapshot"]
    val snapshotKinds = setOf(
        "counts", "items", "weapons", "armor", "accessories", "traits", "runes", "synergies",
        "skills", "effects", "formulas", "recipes", "materials", "stats", "item-stats",
        "item-curves", "lookup", "advise", "classes",
    )
    val snapshot = when {
        snapshotRef != null -> query.snapshotService().resolve(snapshotRef)
            ?: error("unknown snapshot: $snapshotRef")
        kind in snapshotKinds || kind == "character" -> query.snapshotService().active()
        else -> null
    }
    val name = flags["name"]
    when (kind) {
        "snapshots" -> query.snapshots().forEach { snap ->
            val counts = query.counts(snap.id)
            val aliasText = if (snap.aliases.isEmpty()) "" else " aliases=${snap.aliases.joinToString(",")}"
            println("${snap.id} build=${snap.gameBuild} version=${snap.gameVersion} source=${snap.source} active=${snap.active} ${formatCounts(counts)}$aliasText")
        }
        "counts", "items", "weapons", "armor", "accessories", "traits", "runes", "synergies", "skills", "effects", "formulas", "recipes", "materials", "stats" -> {
            val snapshotId = snapshot?.id ?: error("no active snapshot; import game data first")
            when (kind) {
                "counts" -> println("snapshot=$snapshotId ${formatCounts(query.counts(snapshotId))}")
                "items" -> query.items(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.grade ?: "-"}\t${it.sourceTable}\t${it.sourceRowId}")
                }
                "weapons" -> query.weapons(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.weaponType ?: "-"}\t${it.sourceTable}\t${it.sourceRowId}")
                }
                "armor" -> query.armor(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.slot ?: "-"}\t${it.sourceTable}\t${it.sourceRowId}")
                }
                "accessories" -> query.accessories(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.slot ?: "-"}\t${it.sourceTable}\t${it.sourceRowId}")
                }
                "runes" -> query.runes(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.grade ?: "-"}\t${it.sourceRowId}")
                }
                "synergies" -> query.synergies(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.sourceRowId}")
                }
                "skills" -> query.skills(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.family ?: it.skillType ?: "-"}\t${it.weaponToken ?: "-"}\t${it.sourceRowId}")
                }
                "effects" -> query.effects(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.sourceTable}\t${it.sourceRowId}")
                }
                "recipes" -> query.recipes(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.recipeKind ?: "-"}\t${it.sourceRowId}")
                }
                "stats" -> query.stats(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.sourceRowId}")
                }
                "traits" -> query.traits(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.sourceRowId}")
                }
                "materials" -> query.materials(snapshotId, name).forEach {
                    println("${it.name ?: "-"}\t${it.sourceTable}\t${it.sourceRowId}")
                }
                "formulas" -> query.formulas(snapshotId, name).forEach {
                    println("${it.sourceRowId}\t${it.expression ?: "-"}\tconfidence=${it.confidence}")
                }
            }
        }
        "classes" -> {
            val snapshotId = snapshot?.id
            if (snapshotId != null) {
                query.classes(snapshotId, name).forEach { row ->
                    println("extracted\t${row.name ?: "-"}\t${row.weaponA ?: "-"}\t${row.weaponB ?: "-"}\t${row.sourceTable}\t${row.sourceRowId}")
                }
            }
            com.solisium.core.meta.CommunityWeaponClasses.pairs()
                .filter { name.isNullOrBlank() || it.name.contains(name, ignoreCase = true) }
                .forEach { pair ->
                    println("community\t${pair.name}\t${pair.weaponA}\t${pair.weaponB}")
                }
        }
        "item-stats" -> {
            val snapshotId = snapshot?.id ?: error("no active snapshot; import game data first")
            val row = flags["row"] ?: error("--row is required")
            val stats = query.itemStats(snapshotId, row)
            if (stats.isEmpty()) {
                println("no stat values for row=$row")
            } else {
                println("# raw_value is the unscaled client integer, not a display value")
                stats.forEach {
                    println("${it.scope}\t${it.statKey}\t${it.statName ?: "-"}\t${it.rawValue}\tconfidence=${it.confidence}")
                }
            }
        }
        "item-curves" -> {
            val snapshotId = snapshot?.id ?: error("no active snapshot; import game data first")
            val row = flags["row"] ?: error("--row is required")
            val curves = query.itemCurves(snapshotId, row)
            if (curves.isEmpty()) {
                println("no curves for row=$row")
            } else {
                curves.forEach {
                    println("${it.curveKind}\t${it.curveId}\tmax_level=${it.maxLevel ?: "-"}\t${it.curveSourceTable}")
                }
                println("# cumulative client totals per level; combining with base stats is unverified")
                query.itemCurvePoints(snapshotId, row).forEach {
                    println("${it.curveKind}\tL${it.level}\t${it.statKey}\t${it.statName ?: "-"}\t${it.rawValue}")
                }
            }
        }
        "lookup" -> {
            val snapshotId = snapshot?.id ?: error("no active snapshot; import game data first")
            val table = flags["table"] ?: error("--table is required")
            val row = flags["row"] ?: error("--row is required")
            val hit = query.lookup(snapshotId, table, row)
            if (hit == null) {
                println("unresolved table=$table row=$row")
            } else {
                println("${hit.kind}\t${hit.name ?: "-"}\t${hit.detail ?: "-"}\t${hit.sourceTable}\t${hit.sourceRowId}")
            }
        }
        "advise" -> {
            val snapshotId = snapshot?.id ?: error("no active snapshot; import game data first")
            val goal = com.solisium.core.query.BuildGoal.fromId(flags["goal"])
            var community = if (flags.containsKey("meta") || args.contains("--meta")) {
                val raw = com.solisium.core.meta.CommunityMetaClient().fetch(goal)
                com.solisium.core.meta.CommunityOverlay.bind(raw, query, snapshotId)
            } else {
                null
            }
            flags["slug"]?.let { slug ->
                val raw = com.solisium.core.meta.CommunityMetaClient().fetchCharacter(slug, community)
                community = com.solisium.core.meta.CommunityOverlay.bind(raw, query, snapshotId)
            }
            val classOption = flags["class"]?.let { name ->
                query.findBuildClass(snapshotId, name = name)
                    ?: error("unknown class: $name")
            }
            val plan = com.solisium.core.query.DesiredBuildPlanner(query).plan(
                snapshotId,
                goal,
                flags["character"] ?: flags["id"],
                community,
                desiredCombatPower = flags["desired-cp"]?.filter { it.isDigit() }?.toLongOrNull(),
                desiredGearScore = flags["desired-gs"]?.filter { it.isDigit() }?.toLongOrNull(),
                axes = com.solisium.core.query.StatAxis.fromIds(flags["axes"]),
                extraKeys = flags["stat"]?.let { setOf(it) }.orEmpty(),
                classOption = classOption,
            )
            val advice = plan.advice
            println("goal=${advice.goalId} snapshot=${advice.snapshotId} build=${advice.snapshotBuild ?: "-"}")
            plan.selectedClass?.let {
                println("class=${it.name} weapons=${it.weaponsLabel} source=${it.source}")
            }
            println(advice.scoringNote)
            plan.combatPowerGap?.let { println("typed_cp_gap=$it current=${plan.currentCombatPower} desired=${plan.desiredCombatPower}") }
            plan.gearScoreGap?.let { println("typed_gs_gap=$it current=${plan.currentGearScore} desired=${plan.desiredGearScore}") }
            plan.modeled?.let { modeled ->
                println("modeled_cp=${modeled.current} potential_cp=${modeled.potential} modeled_gs=${modeled.gearScore} potential_gs=${modeled.potentialGearScore}")
                println("modeled_breakdown equipment_base=${modeled.equipmentBase} items=${modeled.itemPower} skills=${modeled.skillPower} mastery=${modeled.masteryPower} unresolved=${modeled.unresolvedCount}")
                modeled.items.forEach { item ->
                    println("modeled_slot ${item.slot}\t${item.name}\tcurrent=${item.current}\tpotential=${item.potential}\t${item.source}")
                }
            }
            plan.modeledCombatPowerGap?.let { println("modeled_cp_gap=$it") }
            plan.modeledGearScoreGap?.let { println("modeled_gs_gap=$it") }
            advice.briefing.forEach { println("brief $it") }
            plan.roadmap.forEach { step ->
                println("roadmap ${step.kind}\t${step.title}")
            }
            plan.limits.forEach { println("limit $it") }
            plan.influences.forEach { layer ->
                println("influence ${layer.layer}\tslotted=${layer.slotted}\tresolved=${layer.resolved}\tcatalog=${layer.catalogNamed}\t${layer.label}")
            }
            advice.slots.forEach { slot ->
                val you = slot.equipped?.let { "you=${it.name}:${it.score}" } ?: "you=-"
                val top = slot.recommended.firstOrNull()?.let { "top=${it.name}:${it.score}" } ?: "top=-"
                println("slot ${slot.slot} $you $top gap=${slot.gap ?: "-"}")
                slot.recommended.take(3).forEach { item ->
                    println("  ${item.score}\t${item.name}\t${item.kind}\tcommunity=${item.communityHits}")
                }
            }
            community?.let { meta ->
                println("meta sources=${meta.sources.joinToString(",")} patch=${meta.patchLabel ?: "-"} items=${meta.items.size} skills=${meta.skills.size} builds=${meta.builds.size}")
                meta.warnings.forEach { println("warning: $it") }
            }
        }
        "characters" -> query.characters().forEach { character ->
            println("${character.id}\t${character.name}\tlevel=${character.level ?: "-"}\tcp=${character.combatPower ?: "-"}\tgs=${character.gearScore ?: "-"}\tclass=${character.className ?: "-"}\tallocated=${character.statPoints.allocated ?: "-"}")
        }
        "character" -> {
            val id = flags["id"] ?: error("--id is required")
            val resolved = query.resolveCharacter(id, snapshot?.id) ?: error("unknown character: $id")
            printResolvedSheet(resolved)
        }
        "sessions" -> query.combatSessions().forEach { session ->
            println(formatSessionLine(session))
        }
        "session" -> {
            val id = flags["id"] ?: error("--id is required")
            val session = query.combatSummary(id) ?: error("unknown session: $id")
            println(formatSessionLine(session))
            session.skillTotals.forEach { total ->
                println(
                    "skill ${total.skillName ?: "-"}\tid=${total.skillId ?: "-"}\tdamage=${total.observedDamageSum}\thits=${total.hits}",
                )
            }
        }
        else -> error("unknown query: $kind")
    }
}

private fun formatCounts(counts: CatalogCounts): String =
    "items=${counts.items} weapons=${counts.weapons} armor=${counts.armor} accessories=${counts.accessories} " +
        "traits=${counts.traits} runes=${counts.runes} synergies=${counts.synergies} skills=${counts.skills} " +
        "effects=${counts.effects} formulas=${counts.formulas} recipes=${counts.recipes} " +
        "materials=${counts.materials} stats=${counts.stats} " +
        "item_stats=${counts.itemStats} items_with_stats=${counts.itemsWithStats} " +
        "curve_points=${counts.curvePoints} item_curves=${counts.itemCurveLinks} classes=${counts.classes} " +
        "combat_power=${counts.combatPowerRows} item_power=${counts.itemPowerLinks}"

private fun formatSessionLine(session: CombatSessionSummary): String {
    val dps = session.observedDps?.let { String.format("%.1f", it) } ?: "-"
    return "${session.sessionId} events=${session.eventCount} damage=${session.observedDamageSum} observed_dps=$dps version=${session.logVersion ?: "-"}"
}

private fun printResolvedSheet(resolved: ResolvedCharacterSheet) {
    val character = resolved.sheet.character
    println(
        "id=${character.id} name=${character.name} level=${character.level ?: "-"} cp=${character.combatPower ?: "-"} gs=${character.gearScore ?: "-"} ${formatStatPoints(character)} server=${character.server ?: "-"}",
    )
    val match = resolved.weaponClass
    println(
        "class=${match?.name ?: character.className ?: "-"} source=${match?.source ?: character.classSource ?: "-"} weapons=${match?.weaponsLabel ?: "-"}",
    )
    if (resolved.snapshotId == null) {
        println("catalog=none (import a warehouse snapshot to resolve names)")
        printSheet(resolved.sheet, includeIdentity = false)
        return
    }
    println("catalog snapshot=${resolved.snapshotId} build=${resolved.snapshotBuild ?: "-"}")
    resolved.lines.forEach { println(formatLine(it)) }
    resolved.sheet.currency.forEach {
        println("currency ${it.currency}=${it.amount} (manual; no local file source)")
    }
    resolved.sheet.cookingLevel?.let { println("cooking_level=$it (manual; no local file source)") }
    resolved.sheet.goals.forEach {
        println("goal type=${it.goalType} label=${it.label} active=${it.active}")
    }
    resolved.sheet.builds.forEach {
        println("build id=${it.id} name=${it.name} snapshot=${it.snapshotId ?: "-"}")
    }
    if (resolved.unresolvedCount > 0) {
        println("unresolved=${resolved.unresolvedCount} (missing from this snapshot; names are not guessed)")
    }
}

private fun formatStatPoints(character: UserCharacter): String {
    return "str=${character.strength ?: "-"} dex=${character.dexterity ?: "-"} " +
        "wis=${character.wisdom ?: "-"} per=${character.perception ?: "-"} " +
        "for=${character.fortitude ?: "-"} allocated=${character.statPoints.allocated ?: "-"}"
}

private fun formatLine(line: ResolvedLoadoutLine): String {
    val hit = line.hit
    val name = when {
        hit != null -> hit.name ?: "-"
        line.empty -> "EMPTY"
        line.unresolved -> line.name ?: "UNRESOLVED"
        else -> line.name ?: "-"
    }
    val parts = mutableListOf(line.kind)
    line.label?.let { parts.add(it) }
    parts.add("name=$name")
    hit?.detail?.let { parts.add("detail=$it") }
    line.extra?.let { parts.add(it) }
    if (line.stats.isNotEmpty()) {
        parts.add(line.stats.joinToString(",") { "${it.statKey}=${it.rawValue}" })
    }
    parts.add("table=${line.sourceTable ?: "-"}")
    parts.add("row=${line.sourceRowId ?: "-"}")
    return parts.joinToString(" ")
}

private fun printSheet(sheet: CharacterSheet, includeIdentity: Boolean = true) {
    if (includeIdentity) {
        val character = sheet.character
        println(
            "id=${character.id} name=${character.name} level=${character.level ?: "-"} cp=${character.combatPower ?: "-"} gs=${character.gearScore ?: "-"} ${formatStatPoints(character)} server=${character.server ?: "-"}",
        )
    }
    sheet.equipment.forEach {
        println("equipment slot=${it.slot} name=${it.name ?: "-"} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} ilvl=${it.itemLevel ?: "-"}")
    }
    sheet.weapons.forEach {
        println("weapon slot=${it.slot} name=${it.name ?: "-"} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} ilvl=${it.itemLevel ?: "-"}")
    }
    sheet.traits.forEach {
        println("trait table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} rank=${it.rank ?: "-"}")
    }
    sheet.runes.forEach {
        println("rune slot=${it.slot ?: "-"} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} level=${it.runeLevel ?: "-"}")
    }
    sheet.skills.forEach {
        println("skill name=${it.name ?: "-"} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} loadout=${it.loadout ?: "-"} level=${it.skillLevel ?: "-"} family=${it.family ?: "-"}")
    }
    sheet.weaponMastery.forEach {
        println("weapon_mastery weapon=${it.weapon} level=${it.level ?: "-"}")
    }
    sheet.buildLayers.forEach {
        println("layer ${it.layer} slot=${it.slot ?: "-"} name=${it.name ?: "-"} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} level=${it.level ?: "-"}")
    }
    sheet.inventory.forEach {
        println("inventory name=${it.name ?: "-"} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} qty=${it.quantity}")
    }
    sheet.materials.forEach {
        println("material table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} qty=${it.quantity}")
    }
    sheet.currency.forEach {
        println("currency ${it.currency}=${it.amount} (manual; no local file source)")
    }
    sheet.cookingLevel?.let { println("cooking_level=$it (manual; no local file source)") }
    sheet.goals.forEach {
        println("goal type=${it.goalType} label=${it.label} active=${it.active}")
    }
    sheet.builds.forEach {
        println("build id=${it.id} name=${it.name} snapshot=${it.snapshotId ?: "-"}")
    }
}

private fun runActivate(args: List<String>) {
    val flags = parseFlags(args)
    val ref = flags["snapshot"] ?: flags["positional"] ?: error("--snapshot is required")
    val db = JvmDatabase.openOrCreate(dbPath(flags))
    val snapshot = CatalogQuery(db).snapshotService().activate(ref)
    println("active=${snapshot.id} build=${snapshot.gameBuild}")
}

private fun runAlias(args: List<String>) {
    val flags = parseFlags(args)
    val name = flags["name"] ?: error("--name is required")
    val snapshotId = flags["snapshot"] ?: error("--snapshot is required")
    val db = JvmDatabase.openOrCreate(dbPath(flags))
    val snapshot = CatalogQuery(db).snapshotService().setAlias(name, snapshotId)
    println("alias=$name snapshot=${snapshot.id}")
}

private fun runParseLog(args: List<String>) {
    val flags = parseFlags(args)
    val file = Path.of(flags["path"] ?: error("--path is required"))
    val parsed = CombatLogParser.parse(Files.readString(file))
    println("version=${parsed.version ?: "missing"} events=${parsed.events.size} errors=${parsed.errors.size}")
    parsed.errors.forEach { println("error: $it") }
    val damage = parsed.events.mapNotNull { it.damage }.sum()
    println("observed_damage_sum=$damage (log statistic, not modeled DPS)")
}

private fun runLogs() {
    val folder = CombatLogPaths.detect()
    if (folder == null) {
        println("folder not found under %LOCALAPPDATA%\\TL\\Saved\\CombatLogs")
        val saves = CombatLogPaths.saveGamesDir()
        if (saves != null) {
            println("SaveGames present at $saves; hashed .sav files are not parsed")
        }
        return
    }
    val files = CombatLogPaths.listLogFiles(folder)
    println("folder=$folder files=${files.size}")
    files.forEach { println(it) }
}

private fun runDetect() {
    val capability = InstalledGameDataSource().probe()
    println("available=${capability.available}")
    println(capability.notes)
}

private fun runPatchCheck(args: List<String>) {
    val flags = parseFlags(args)
    val dbFile = dbPath(flags)
    val active = if (Files.isRegularFile(dbFile)) {
        CatalogQuery(JvmDatabase.openOrCreate(dbFile)).snapshotService().active()
    } else {
        null
    }
    val report = com.solisium.core.source.PatchWatch().inspect(active)
    println("state=${report.state.name.lowercase()}")
    println(report.reason)
    println("installed=${report.installedBuild ?: "-"} catalog=${report.activeBuild ?: "-"}")
    report.warehouse?.let {
        println("warehouse ${it.path} build=${it.buildId ?: "-"} hash=${it.sha256 ?: "-"}")
    }
    if (active != null) {
        val db = JvmDatabase.openOrCreate(dbFile)
        CatalogQuery(db).discoveredInfluences(active.id).forEach { inf ->
            println("influence ${inf.id}\tnamed=${inf.namedCount}\tnew=${inf.newThisPatch}\t${inf.label}")
        }
    }
    if (flags.containsKey("import") || args.contains("--import")) {
        if (!report.canImport) {
            println("import skipped; warehouse is not ready")
            return
        }
        val path = report.warehouse?.path ?: error("no warehouse path")
        val db = JvmDatabase.openOrCreate(dbFile)
        val receipt = TLHelperDataSource().importInto(db, ImportRequest(path = path.toString(), activate = true))
        println("imported source=${receipt.source} records=${receipt.recordsImported} skipped=${receipt.recordsSkipped} snapshot=${receipt.snapshotId ?: "-"}")
        receipt.warnings.forEach { println("warning: $it") }
    }
}

/**
 * Key management. Every path through here prints fingerprints rather than keys, so a
 * terminal scrollback, a screen share, or a pasted log cannot leak one.
 */
private fun runKeys(args: List<String>) {
    val action = args.firstOrNull() ?: "list"
    val flags = parseFlags(args.drop(1))
    val store = SecretStore()
    when (action) {
        "list" -> {
            val stored = store.list()
            println("store ${store.path}")
            if (stored.isEmpty()) {
                println("no keys stored")
            } else {
                stored.forEach { println("${it.name}\tfingerprint=${it.fingerprint}") }
            }
        }
        "scan" -> {
            val report = scanForKeys(flags)
            report.searchedRoots.forEach { println("searched $it") }
            println("files read ${report.filesRead}")
            report.skipped.take(5).forEach { println("skipped $it") }
            if (report.candidates.isEmpty()) {
                println("no 32-byte key found; pass --path <folder> to search somewhere specific")
                return
            }
            report.candidates.forEach {
                println("found fingerprint=${it.fingerprint} via ${it.evidence} at ${it.source}")
            }
            println("add one with: solisium keys add --name archive --from <folder containing it>")
        }
        "add" -> {
            val name = flags["name"] ?: error("keys add needs --name")
            // An explicit --value stays out of the scan path entirely.
            val explicit = flags["value"]
            val key = if (explicit != null) {
                AesKey.normalize(explicit) ?: error("--value is not a 32-byte hex key")
            } else {
                val report = scanForKeys(flags)
                val distinct = report.candidates.map { it.keyHex }.distinct()
                when {
                    distinct.isEmpty() -> error("no key found to add; try 'solisium keys scan'")
                    distinct.size > 1 -> error(
                        "found ${distinct.size} different keys; narrow it with --from <folder> " +
                            "or pass --value",
                    )
                    else -> distinct.single()
                }
            }
            val ref = store.put(name, key)
            println("stored ${ref.name} fingerprint=${ref.fingerprint} in ${store.path}")
            println("this file is outside the repository and outside any installed copy")
        }
        "remove" -> {
            val name = flags["name"] ?: error("keys remove needs --name")
            println(if (store.remove(name)) "removed $name" else "no key named $name")
        }
        else -> error("unknown keys action: $action (expected list, scan, add, or remove)")
    }
}

private fun scanForKeys(flags: Map<String, String>): ScanReport {
    val explicitRoot = flags["path"] ?: flags["from"]
    val roots = explicitRoot?.let { listOf(Path.of(it)) } ?: emptyList()
    // An explicit folder means exactly that folder, so a scan can be made reproducible.
    return SecretScanner(useDefaultRoots = explicitRoot == null).scan(roots)
}

private fun dbPath(flags: Map<String, String>): Path {
    val override = flags["db"] ?: System.getenv("SOLISIUM_DB")
    return if (override.isNullOrBlank()) {
        Path.of(System.getProperty("user.home"), ".solisium", "solisium.sqlite")
    } else {
        Path.of(override)
    }
}

private fun parseFlags(args: List<String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--") && i + 1 < args.size && !args[i + 1].startsWith("--")) {
            out[arg.removePrefix("--")] = args[i + 1]
            i += 2
        } else if (!arg.startsWith("--") && !out.containsKey("positional")) {
            out["positional"] = arg
            i++
        } else {
            i++
        }
    }
    return out
}
