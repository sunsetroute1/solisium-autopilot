package com.solisium.core.secret

import com.solisium.core.source.TLHelperLocator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * A key found somewhere on this machine, described by where it came from.
 *
 * [keyHex] is the only field carrying secret material. It is excluded from [toString]
 * so that logging a candidate, or a collection of them, cannot leak a key.
 */
class KeyCandidate(
    val source: String,
    val keyHex: String,
    /** Why this value was taken to be a key: the field name, or a whole-file match. */
    val evidence: String = "unlabelled",
) {
    val fingerprint: String get() = AesKey.fingerprint(keyHex)

    override fun toString(): String =
        "KeyCandidate(source=$source, evidence=$evidence, fingerprint=$fingerprint)"

    override fun equals(other: Any?): Boolean =
        other is KeyCandidate && other.keyHex == keyHex && other.source == source

    override fun hashCode(): Int = 31 * source.hashCode() + keyHex.hashCode()
}

/** What a scan looked at and what it found, so the UI can explain an empty result. */
data class ScanReport(
    val candidates: List<KeyCandidate>,
    val searchedRoots: List<Path>,
    val filesRead: Int,
    val skipped: List<String>,
) {
    val distinctKeys: Int get() = candidates.map { it.keyHex }.distinct().size
}

/**
 * Looks for an archive key already present on this machine so the user does not have to
 * find and paste one.
 *
 * The scan is deliberately bounded. It walks a small set of plausible roots, not the
 * whole disk: an unbounded search would be slow, would read files that are none of our
 * business, and would turn a convenience into a liability. Anything unreadable is
 * recorded and stepped over rather than failing the scan.
 */
class SecretScanner(
    private val env: (String) -> String? = { System.getenv(it) },
    private val maxDepth: Int = 4,
    private val maxFiles: Int = 4_000,
    private val maxFileBytes: Long = 2L * 1024 * 1024,
    /**
     * When false, only the roots passed to [scan] are searched. Needed wherever the
     * search must be reproducible rather than dependent on what this machine happens
     * to have lying around.
     */
    private val useDefaultRoots: Boolean = true,
) {
    /** Environment variables a key is conventionally passed through. */
    private val envNames = listOf("SOLISIUM_AES_KEY", "TL_AES_KEY", "TL_HELPER_AES_KEY", "AES_KEY")

    /** File names worth opening. Everything else is ignored even inside a searched root. */
    private val interestingNames = listOf(
        "aes.txt", "aes.key", "key.txt", "keys.txt",
        "secrets.properties", "local.properties", ".env",
        "source-manifest.json", "config.local.json",
    )
    private val interestingExtensions = listOf("txt", "key", "json", "ini", "cfg", "conf", "properties", "yaml", "yml")

    fun scan(extraRoots: List<Path> = emptyList()): ScanReport {
        val candidates = LinkedHashMap<String, KeyCandidate>()
        val skipped = mutableListOf<String>()
        var filesRead = 0

            for (name in envNames) {
            val normalized = AesKey.normalize(env(name))
            if (normalized != null) {
                candidates.putIfAbsent(
                    normalized,
                    KeyCandidate("environment variable $name", normalized, "environment variable"),
                )
            }
        }

        val configured = if (useDefaultRoots) extraRoots + defaultRoots() else extraRoots
        val roots = configured.distinct().filter { Files.isDirectory(it) }
        for (root in roots) {
            if (filesRead >= maxFiles) {
                skipped += "stopped after $maxFiles files; narrow the search to a specific folder"
                break
            }
            val walked = walk(root, skipped, maxFiles - filesRead)
            filesRead += walked.size
            for (file in walked) {
                val text = readTextOrNull(file, skipped) ?: continue
                AesKey.wholeTextKey(text)?.let { bare ->
                    candidates.putIfAbsent(bare, KeyCandidate(file.toString(), bare, "file contains only the key"))
                }
                for (labelled in AesKey.findLabelled(text)) {
                    candidates.putIfAbsent(
                        labelled.keyHex,
                        KeyCandidate(file.toString(), labelled.keyHex, "field \"${labelled.label}\""),
                    )
                }
            }
        }

        return ScanReport(candidates.values.toList(), roots, filesRead, skipped.distinct())
    }

    /**
     * Plausible homes for a key: the sibling TL-Helper checkout that produced the
     * warehouse, the warehouse data root, and our own config directory.
     */
    private fun defaultRoots(): List<Path> {
        // `Path` is an `Iterable<Path>` of its own name elements, so `+=` on a list of
        // paths silently means the wrong thing. Always add explicitly.
        val roots = mutableListOf<Path>()
        env("SOLISIUM_AES_KEY_DIR")?.takeIf { it.isNotBlank() }?.let { roots.add(Path.of(it)) }
        val dataRoot = env("TL_DATA_ROOT")?.takeIf { it.isNotBlank() } ?: "D:\\TL_Data"
        roots.add(Path.of(dataRoot))
        roots.add(SecretPaths.directory(env))
        TLHelperLocator(env = env).candidates().forEach { roots.add(it) }
        return roots
    }

    private fun walk(root: Path, skipped: MutableList<String>, budget: Int): List<Path> {
        val found = mutableListOf<Path>()
        try {
            Files.walk(root, maxDepth).use { stream ->
                for (path in stream) {
                    if (found.size >= budget) break
                    if (!isInteresting(path)) continue
                    found.add(path)
                }
            }
        } catch (t: Throwable) {
            // An unreadable root is normal (permissions, a disconnected drive). Record
            // it so an empty result is explainable, and carry on.
            skipped += "could not fully read $root: ${t.message ?: t::class.simpleName}"
        }
        return found
    }

    private fun isInteresting(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false
        val name = path.fileName?.toString()?.lowercase() ?: return false
        if (name in interestingNames) return true
        val extension = name.substringAfterLast('.', "")
        return extension in interestingExtensions
    }

    private fun readTextOrNull(file: Path, skipped: MutableList<String>): String? {
        return try {
            if (Files.size(file) > maxFileBytes) return null
            val bytes = Files.readAllBytes(file)
            // A NUL byte means this is not text; scanning it for hex would be noise.
            if (bytes.any { it == 0.toByte() }) return null
            String(bytes, StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            skipped += "could not read $file: ${t.message ?: t::class.simpleName}"
            null
        }
    }
}
