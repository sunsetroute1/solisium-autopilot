package com.solisium.cli

import com.solisium.core.combat.CombatLogParser
import com.solisium.core.db.JvmDatabase
import com.solisium.core.db.SolisiumDatabase
import com.solisium.core.domain.CatalogCounts
import com.solisium.core.domain.CharacterSheet
import com.solisium.core.domain.CombatSessionSummary
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.ResolvedLoadoutLine
import com.solisium.core.query.BuildMismatch
import com.solisium.core.query.CatalogQuery
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
        solisium import --source manual --path <character.json>
        solisium query snapshots|counts|items|weapons|armor|accessories|traits|runes|synergies|skills|effects|formulas|recipes|materials|stats [--name <text>] [--snapshot <id-or-alias>]
        solisium query item-stats --row <item-row-id> [--snapshot <id-or-alias>]
        solisium query item-curves --row <item-row-id> [--snapshot <id-or-alias>]
        solisium query lookup --table <source_table> --row <source_row_id> [--snapshot <id-or-alias>]
        solisium query characters
        solisium query character --id <character-id> [--snapshot <id-or-alias>]
        solisium query sessions
        solisium query session --id <session-id>
        solisium activate --snapshot <id-or-alias>
        solisium alias --name t4 --snapshot <id>
        solisium parse-log --path <CombatLog.txt>
        solisium logs
        solisium detect-install

        Optional: --db <solisium.sqlite>   (default %USERPROFILE%\.solisium\solisium.sqlite)
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
    println("public_repo available=false live scrape is off")
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
            val file = Path.of(path ?: error("--path is required"))
            val receipt = ManualImportDataSource().importInto(
                db,
                ImportRequest(
                    path = file.toString(),
                    content = Files.readString(file),
                    activate = activate,
                    characterId = flags["character"],
                ),
            )
            printReceipt(receipt)
            warnUnresolvedLoadout(db, receipt.characterId)
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
    if (pathArg == null) {
        val folder = CombatLogPaths.detect()
            ?: error("combat log folder not found under %LOCALAPPDATA%\\TL\\Saved\\CombatLogs")
        val files = CombatLogPaths.listLogFiles(folder)
        if (files.isEmpty()) error("no .txt files in $folder")
        val newest = files.first()
        if (files.size > 1) {
            System.err.println("warning: importing newest ${newest.fileName}; ${files.size - 1} older log(s) skipped")
        }
        return listOf(newest)
    }
    val path = Path.of(pathArg)
    return when {
        Files.isDirectory(path) -> {
            val files = CombatLogPaths.listLogFiles(path)
            if (files.isEmpty()) error("no .txt files in $path")
            files
        }
        Files.isRegularFile(path) -> listOf(path)
        else -> error("path not found: $path")
    }
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
        "item-curves", "lookup",
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
                    println("${it.name ?: "-"}\t${it.skillType ?: "-"}\t${it.sourceRowId}")
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
        "characters" -> query.characters().forEach { character ->
            println("${character.id}\t${character.name}\tlevel=${character.level ?: "-"}\tcp=${character.combatPower ?: "-"}")
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
        "curve_points=${counts.curvePoints} item_curves=${counts.itemCurveLinks}"

private fun formatSessionLine(session: CombatSessionSummary): String {
    val dps = session.observedDps?.let { String.format("%.1f", it) } ?: "-"
    return "${session.sessionId} events=${session.eventCount} damage=${session.observedDamageSum} observed_dps=$dps version=${session.logVersion ?: "-"}"
}

private fun printResolvedSheet(resolved: ResolvedCharacterSheet) {
    val character = resolved.sheet.character
    println(
        "id=${character.id} name=${character.name} level=${character.level ?: "-"} cp=${character.combatPower ?: "-"} server=${character.server ?: "-"}",
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

private fun formatLine(line: ResolvedLoadoutLine): String {
    val hit = line.hit
    val name = when {
        hit != null -> hit.name ?: "-"
        line.unresolved -> "UNRESOLVED"
        else -> "-"
    }
    val parts = mutableListOf(line.kind)
    line.label?.let { parts.add(it) }
    parts.add("name=$name")
    hit?.detail?.let { parts.add("detail=$it") }
    line.extra?.let { parts.add(it) }
    parts.add("table=${line.sourceTable ?: "-"}")
    parts.add("row=${line.sourceRowId ?: "-"}")
    return parts.joinToString(" ")
}

private fun printSheet(sheet: CharacterSheet, includeIdentity: Boolean = true) {
    if (includeIdentity) {
        val character = sheet.character
        println(
            "id=${character.id} name=${character.name} level=${character.level ?: "-"} cp=${character.combatPower ?: "-"} server=${character.server ?: "-"}",
        )
    }
    sheet.equipment.forEach {
        println("equipment slot=${it.slot} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} ilvl=${it.itemLevel ?: "-"}")
    }
    sheet.weapons.forEach {
        println("weapon slot=${it.slot} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} ilvl=${it.itemLevel ?: "-"}")
    }
    sheet.traits.forEach {
        println("trait table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} rank=${it.rank ?: "-"}")
    }
    sheet.runes.forEach {
        println("rune slot=${it.slot ?: "-"} table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} level=${it.runeLevel ?: "-"}")
    }
    sheet.skills.forEach {
        println("skill table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} loadout=${it.loadout ?: "-"}")
    }
    sheet.inventory.forEach {
        println("inventory table=${it.sourceTable ?: "-"} row=${it.sourceRowId ?: "-"} qty=${it.quantity}")
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
