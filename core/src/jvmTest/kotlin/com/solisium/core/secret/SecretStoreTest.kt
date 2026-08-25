package com.solisium.core.secret

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// secret-scan-allow-fixture: the constants below are made-up patterns, not a real key.
class SecretStoreTest {
    private val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val other = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

    private fun store() = SecretStore(Files.createTempDirectory("solisium-secrets").resolve("secrets.properties"))

    @Test
    fun readingAnAbsentStoreIsEmptyRatherThanAnError() {
        val store = store()
        assertFalse(store.exists())
        assertEquals(emptyList(), store.list())
        assertNull(store.get("archive"))
        assertFalse(store.contains("archive"))
    }

    @Test
    fun roundTripsASecretAndCreatesItsDirectory() {
        val store = store()
        val ref = store.put("archive", key)
        assertEquals("archive", ref.name)
        assertEquals(AesKey.fingerprint(key), ref.fingerprint)
        assertTrue(store.exists())
        assertEquals(key, store.get("archive"))
        assertEquals(listOf(SecretRef("archive", AesKey.fingerprint(key))), store.list())
    }

    @Test
    fun listingNeverExposesTheValue() {
        val store = store()
        store.put("archive", key)
        val rendered = store.list().toString()
        assertFalse(rendered.contains(key), "list() leaked the key: $rendered")
    }

    @Test
    fun overwritingReplacesTheValueAndKeepsOtherSecrets() {
        val store = store()
        store.put("archive", key)
        store.put("second", other)
        store.put("archive", other)
        assertEquals(other, store.get("archive"))
        assertEquals(other, store.get("second"))
        assertEquals(listOf("archive", "second"), store.list().map { it.name })
    }

    @Test
    fun removingIsIdempotent() {
        val store = store()
        store.put("archive", key)
        assertTrue(store.remove("archive"))
        assertFalse(store.remove("archive"))
        assertNull(store.get("archive"))
    }

    @Test
    fun refusesBlankNamesAndValues() {
        val store = store()
        assertFailsWith<IllegalArgumentException> { store.put("", key) }
        assertFailsWith<IllegalArgumentException> { store.put("archive", "  ") }
    }

    @Test
    fun survivesAKeyContainingPropertiesSyntax() {
        // Properties escaping is easy to get wrong; a value with = and : must round trip.
        val store = store()
        store.put("odd", "a=b:c\\d")
        assertEquals("a=b:c\\d", store.get("odd"))
    }

    @Test
    fun storeLivesOutsideTheProjectAndUnderTheUsersOwnDirectory() {
        val path = SecretPaths.storeFile { name -> if (name == "LOCALAPPDATA") "C:\\Users\\someone\\AppData\\Local" else null }
        assertEquals("secrets.properties", path.fileName.toString())
        assertTrue(path.toString().contains("AppData"), "got $path")
        assertTrue(path.toString().contains("Solisium"), "got $path")
    }

    @Test
    fun fallsBackToTheHomeDirectoryWithoutLocalAppData() {
        val path = SecretPaths.storeFile { null }
        assertTrue(path.toString().contains(".solisium"), "got $path")
    }
}
