package com.solisium.core.secret

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Where local secrets live: `%LOCALAPPDATA%\Solisium\secrets.properties`, or
 * `~/.solisium/secrets.properties` as a fallback.
 *
 * Deliberately outside both the source tree and the install directory. Keeping it out
 * of the source tree means no ignore rule has to hold for the key to stay unpublished;
 * keeping it out of the install directory means an installer or an uninstall never
 * carries it, and packaging cannot bundle it by accident.
 */
object SecretPaths {
    const val FILE_NAME = "secrets.properties"

    fun directory(env: (String) -> String? = { System.getenv(it) }): Path {
        val localAppData = env("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        return if (localAppData != null) {
            Path.of(localAppData, "Solisium")
        } else {
            Path.of(System.getProperty("user.home"), ".solisium")
        }
    }

    fun storeFile(env: (String) -> String? = { System.getenv(it) }): Path =
        directory(env).resolve(FILE_NAME)
}

/** A stored secret, described without revealing it. */
data class SecretRef(val name: String, val fingerprint: String) {
    override fun toString(): String = "SecretRef($name, fingerprint=$fingerprint)"
}

/**
 * A tiny local key/value store for secrets.
 *
 * Values are never returned by [list] or included in any exception message, so a stack
 * trace or a log line cannot leak one. [get] is the only way out, and its callers are
 * expected to pass the value straight to whatever needs it.
 */
class SecretStore(private val file: Path = SecretPaths.storeFile()) {

    val path: Path get() = file

    fun exists(): Boolean = Files.isRegularFile(file)

    fun list(): List<SecretRef> = load().entries
        .map { (name, value) -> SecretRef(name, AesKey.fingerprint(value)) }
        .sortedBy { it.name }

    fun get(name: String): String? = load()[name]

    fun contains(name: String): Boolean = load().containsKey(name)

    /**
     * Stores [value] under [name]. Returns the reference so a caller can report what it
     * saved without handling the value again.
     */
    fun put(name: String, value: String): SecretRef {
        require(name.isNotBlank()) { "secret name must not be blank" }
        require(value.isNotBlank()) { "refusing to store a blank secret" }
        val current = load().toMutableMap()
        current[name] = value
        save(current)
        return SecretRef(name, AesKey.fingerprint(value))
    }

    fun remove(name: String): Boolean {
        val current = load().toMutableMap()
        val removed = current.remove(name) != null
        if (removed) save(current)
        return removed
    }

    private fun load(): Map<String, String> {
        if (!Files.isRegularFile(file)) return emptyMap()
        val properties = Properties()
        try {
            Files.newInputStream(file).use { properties.load(it) }
        } catch (e: IOException) {
            // Report the path, never the contents.
            throw IOException("could not read the secret store at $file: ${e.message}")
        }
        return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }

    private fun save(values: Map<String, String>) {
        Files.createDirectories(file.parent)
        val properties = Properties()
        values.forEach { (name, value) -> properties.setProperty(name, value) }
        // Write to a sibling then move, so an interrupted write cannot truncate an
        // existing store and lose a key the user may no longer have a copy of.
        val temp = Files.createTempFile(file.parent, "secrets", ".tmp")
        try {
            restrictToOwner(temp)
            Files.newOutputStream(temp).use {
                properties.store(it, "Solisium local secrets. Not tracked by git. Do not share.")
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            restrictToOwner(file)
        } catch (e: IOException) {
            Files.deleteIfExists(temp)
            throw IOException("could not write the secret store at $file: ${e.message}")
        }
    }

    /**
     * Best effort file locking down. Windows ACLs are not fully expressible through
     * this API, so this narrows access rather than guaranteeing it; the store's real
     * protection is living in a per-user directory.
     */
    private fun restrictToOwner(target: Path) {
        val asFile = target.toFile()
        runCatching {
            asFile.setReadable(false, false)
            asFile.setWritable(false, false)
            asFile.setReadable(true, true)
            asFile.setWritable(true, true)
        }
    }
}
