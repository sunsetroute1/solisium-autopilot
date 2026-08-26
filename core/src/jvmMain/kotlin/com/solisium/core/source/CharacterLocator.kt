package com.solisium.core.source

import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds user-supplied character JSON. The game does not write a loadout file we can
 * parse, so this looks in Solisium's own folder, then next to a TL-Helper warehouse.
 * Hashed `.sav` files under Saved\SaveGames are never candidates.
 */
class CharacterLocator(
    private val env: (String) -> String? = { System.getenv(it) },
    private val solisiumHome: Path = Path.of(System.getProperty("user.home"), ".solisium"),
    private val cwd: Path = Path.of("").toAbsolutePath().normalize(),
    private val exampleText: () -> String? = {
        CharacterLocator::class.java.getResourceAsStream("/character.example.json")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
    },
) {
    fun charactersDir(): Path = solisiumHome.resolve("characters")

    fun lastPathFile(): Path = solisiumHome.resolve("last-character.txt")

    /**
     * Creates the default character folder on first run and drops a starter document
     * there if the folder is empty, so Import has a file and Choose file opens on it.
     */
    fun prepareHome(): Path {
        val dir = charactersDir()
        Files.createDirectories(dir)
        if (readLastPath()?.let { isCharacterJson(it) } == true) return dir
        val existing = listJson(dir).filter { isCharacterJson(it) }
        if (existing.isEmpty()) {
            exampleText()?.let { text ->
                Files.writeString(dir.resolve("character.json"), text)
            }
        }
        return dir
    }

    fun findImportable(): List<Path> {
        env("SOLISIUM_CHARACTER")?.takeIf { it.isNotBlank() }?.let { explicit ->
            val fromEnv = resolve(Path.of(explicit))
            if (fromEnv.isNotEmpty()) return fromEnv
        }
        readLastPath()?.let { last ->
            val remembered = resolve(last)
            if (remembered.isNotEmpty()) return remembered
        }
        val homeFiles = resolve(charactersDir())
        if (homeFiles.isNotEmpty()) return homeFiles
        val dataRoot = env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() } ?: "D:\\TL_Data"
        val fromData = resolve(Path.of(dataRoot, "characters"))
        if (fromData.isNotEmpty()) return fromData
        WarehouseLocator(env).find()?.parent?.resolve("characters")?.let { besideWarehouse ->
            val fromWarehouse = resolve(besideWarehouse)
            if (fromWarehouse.isNotEmpty()) return fromWarehouse
        }
        return resolve(cwd.resolve("examples").resolve("character.json"))
    }

    fun find(): Path? = findImportable().firstOrNull()

    fun pickerDirectory(): Path {
        find()?.parent?.let { return it }
        val home = charactersDir()
        if (Files.isDirectory(home)) return home
        val examples = cwd.resolve("examples")
        if (Files.isDirectory(examples)) return examples
        WarehouseLocator(env).find()?.parent?.let { return it }
        return solisiumHome
    }

    fun remember(path: Path) {
        runCatching {
            Files.createDirectories(solisiumHome)
            Files.writeString(lastPathFile(), path.toAbsolutePath().normalize().toString())
        }
    }

    fun describe(): String {
        val files = findImportable()
        return if (files.isNotEmpty()) {
            "character json found at ${files.joinToString()}"
        } else {
            "no character json at SOLISIUM_CHARACTER, ${charactersDir()}, or %TL_DATA_ROOT%\\characters"
        }
    }

    private fun resolve(path: Path): List<Path> {
        return when {
            Files.isDirectory(path) -> listJson(path).map { it.toAbsolutePath().normalize() }.filter { isCharacterJson(it) }
            isCharacterJson(path) -> listOf(path.toAbsolutePath().normalize())
            else -> emptyList()
        }
    }

    private fun readLastPath(): Path? {
        val file = lastPathFile()
        if (!Files.isRegularFile(file)) return null
        val raw = Files.readString(file).trim()
        if (raw.isEmpty()) return null
        return Path.of(raw)
    }

    private fun listJson(dir: Path): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.toList()
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json", ignoreCase = true) }
                .filter { !it.fileName.toString().endsWith(".example.json", ignoreCase = true) }
                .sortedByDescending { Files.getLastModifiedTime(it).toMillis() }
        }
    }

    companion object {
        fun isCharacterJson(path: Path): Boolean {
            if (!Files.isRegularFile(path)) return false
            if (!path.fileName.toString().endsWith(".json", ignoreCase = true)) return false
            val size = Files.size(path)
            if (size <= 0L || size > 2_000_000L) return false
            return runCatching {
                val root = JsonParser.parse(Files.readString(path))
                root.str("schema") == "solisium.manual-character" &&
                    ((root.child("character") as? JsonValue.Obj)?.str("id")?.isNotBlank() == true)
            }.getOrDefault(false)
        }
    }
}
