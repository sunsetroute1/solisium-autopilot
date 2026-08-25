package com.solisium.core.secret

import java.security.MessageDigest

/**
 * Validation and identification for archive keys.
 *
 * A key is only ever identified by its fingerprint. Nothing in this project prints,
 * logs, or serialises the key material itself, because a key that leaks once is
 * leaked permanently — it cannot be rotated by us.
 */
object AesKey {
    /** AES-256, so 32 bytes / 64 hex characters. */
    const val BYTES = 32
    private const val HEX_LENGTH = BYTES * 2

    /**
     * Returns the canonical lowercase hex form, or null when [raw] is not a 32-byte
     * hex key. Accepts a `0x` prefix and internal whitespace or dashes, because keys
     * get pasted from all sorts of places.
     */
    fun normalize(raw: String?): String? {
        val cleaned = raw
            ?.trim()
            ?.removePrefix("0x")
            ?.removePrefix("0X")
            ?.filterNot { it.isWhitespace() || it == '-' || it == '_' || it == ':' }
            ?: return null
        if (cleaned.length != HEX_LENGTH) return null
        if (!cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return cleaned.lowercase()
    }

    fun isValid(raw: String?): Boolean = normalize(raw) != null

    /**
     * A short, stable identifier for a key: the first eight hex characters of its
     * SHA-256. Enough to tell two keys apart and to confirm a match, and far too
     * little to reconstruct the key.
     */
    fun fingerprint(raw: String): String {
        val normalized = normalize(raw) ?: return "invalid"
        val bytes = normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    /** A display form that reveals nothing: the length and the fingerprint only. */
    fun describe(raw: String): String =
        if (isValid(raw)) "32-byte key, fingerprint ${fingerprint(raw)}" else "not a 32-byte hex key"

    /** A key found in text, together with the field name that introduced it. */
    data class Labelled(val keyHex: String, val label: String?)

    /**
     * A 32-byte key and a SHA-256 hash are both 64 hex characters, so shape alone
     * cannot tell them apart. Matching on shape finds every content hash in every
     * manifest and buries the one value that matters.
     *
     * So a key is only recognised in one of two situations: the file contains nothing
     * but the key, or the value sits under a field name that means "key" and does not
     * mean "hash".
     */
    fun findLabelled(text: String): List<Labelled> {
        val found = LinkedHashMap<String, Labelled>()
        for (match in ASSIGNMENT.findAll(text)) {
            val key = normalize(match.groups["key"]?.value) ?: continue
            val label = match.groups["label"]?.value?.trim() ?: continue
            if (!looksLikeKeyLabel(label)) continue
            found.putIfAbsent(key, Labelled(key, label))
        }
        return found.values.toList()
    }

    /**
     * The key when [text] is a file that holds only a key. This is how a key is usually
     * handed over, and there is no field name to inspect.
     */
    fun wholeTextKey(text: String): String? = normalize(text)

    /**
     * True when [label] names a key rather than a digest. The deny list wins, so
     * `keyHash` and `sha256Key` are both rejected.
     */
    fun looksLikeKeyLabel(label: String): Boolean {
        val normalized = label.lowercase().trim('"', '\'', ' ', ':', '=', ',', '{', '[', '-', '_', '.')
        if (normalized.isEmpty()) return false
        if (DENY.any { normalized.contains(it) }) return false
        return ALLOW.any { normalized.contains(it) }
    }

    // A value preceded by a field name and a separator. The label window only spans
    // identifier characters, so a word further back on the line cannot vouch for the
    // value, and it stays bounded so a long line cannot drag in unrelated text.
    private val ASSIGNMENT = Regex(
        """(?<label>[A-Za-z0-9_.\-]{1,48})["']?\s*[:=]\s*["']?\s*(?<key>(?:0[xX])?[0-9a-fA-F]{$HEX_LENGTH})(?![0-9a-fA-F])""",
    )

    private val ALLOW = listOf("aes", "key", "secret", "encryption", "crypt", "cipher")
    private val DENY = listOf(
        "hash", "sha", "digest", "checksum", "crc", "guid", "uuid",
        "signature", "sign", "etag", "blake", "md5", "public", "pub",
    )
}
