package com.solisium.core.secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// secret-scan-allow-fixture: the constants below are made-up patterns, not a real key.
class AesKeyTest {
    private val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun acceptsTheFormsAKeyGetsPastedIn() {
        assertEquals(key, AesKey.normalize(key))
        assertEquals(key, AesKey.normalize("0x$key"))
        assertEquals(key, AesKey.normalize("0X$key"))
        assertEquals(key, AesKey.normalize("  $key  "))
        assertEquals(key, AesKey.normalize(key.uppercase()))
        assertEquals(key, AesKey.normalize(key.chunked(8).joinToString("-")))
        assertEquals(key, AesKey.normalize(key.chunked(4).joinToString(" ")))
    }

    @Test
    fun rejectsAnythingThatIsNotA32ByteHexKey() {
        assertNull(AesKey.normalize(null))
        assertNull(AesKey.normalize(""))
        assertNull(AesKey.normalize(key.dropLast(1)), "63 characters is not a 32-byte key")
        assertNull(AesKey.normalize(key + "a"), "65 characters is not a 32-byte key")
        assertNull(AesKey.normalize(key.dropLast(1) + "g"), "g is not hex")
        assertFalse(AesKey.isValid("hello"))
    }

    @Test
    fun fingerprintIsStableDistinguishingAndShort() {
        val other = "f".repeat(64)
        assertEquals(AesKey.fingerprint(key), AesKey.fingerprint(key.uppercase()))
        assertEquals(AesKey.fingerprint(key), AesKey.fingerprint("0x$key"))
        assertTrue(AesKey.fingerprint(key) != AesKey.fingerprint(other))
        assertEquals(8, AesKey.fingerprint(key).length)
    }

    @Test
    fun nothingThatDescribesAKeyContainsTheKey() {
        val described = AesKey.describe(key)
        assertFalse(described.contains(key), "describe() leaked the key: $described")
        assertFalse(AesKey.fingerprint(key).let { key.contains(it) && it.length > 8 })
        assertTrue(described.contains("fingerprint"))
        assertEquals("not a 32-byte hex key", AesKey.describe("nope"))
    }

    @Test
    fun findsALabelledKeyAndCollapsesRepeats() {
        val text = """
            some_setting = true
            aes_key = $key
            repeated_aes_key = $key
        """.trimIndent()
        assertEquals(listOf(AesKey.Labelled(key, "aes_key")), AesKey.findLabelled(text))
    }

    @Test
    fun acceptsAPrefixedValueAndJsonQuoting() {
        assertEquals(key, AesKey.findLabelled("key=0x$key").single().keyHex)
        assertEquals(key, AesKey.findLabelled(""""encryptionKey": "$key"""").single().keyHex)
    }

    /**
     * The whole point of labelling. A content manifest is full of 64-hex SHA-256 values,
     * and reporting those as keys makes the scan useless.
     */
    @Test
    fun ignoresDigestsThatHappenToBe64HexCharacters() {
        val manifest = """
            {"file":"a.pak","sha256":"$key","hash":"$other","contentHash":"$key"}
        """.trimIndent()
        assertEquals(emptyList(), AesKey.findLabelled(manifest))
    }

    @Test
    fun rejectsLabelsThatCombineKeyAndDigestWords() {
        assertFalse(AesKey.looksLikeKeyLabel("keyHash"))
        assertFalse(AesKey.looksLikeKeyLabel("key_sha256"))
        assertFalse(AesKey.looksLikeKeyLabel("publicKey"))
        assertFalse(AesKey.looksLikeKeyLabel("signature"))
        assertFalse(AesKey.looksLikeKeyLabel("id"))
        assertTrue(AesKey.looksLikeKeyLabel("aes_key"))
        assertTrue(AesKey.looksLikeKeyLabel("AesKey"))
        assertTrue(AesKey.looksLikeKeyLabel("\"encryptionKey\""))
        assertTrue(AesKey.looksLikeKeyLabel("secret"))
    }

    @Test
    fun aFileHoldingNothingButAKeyIsRecognised() {
        assertEquals(key, AesKey.wholeTextKey("  $key\n"))
        assertEquals(key, AesKey.wholeTextKey("0x$key"))
        assertNull(AesKey.wholeTextKey("aes_key = $key"), "that has structure, so the label rules apply")
    }

    @Test
    fun anUnlabelledValueIsNotAssumedToBeAKey() {
        assertEquals(emptyList(), AesKey.findLabelled("the value $key appears here"))
    }

    private val other = "f".repeat(64)
}
