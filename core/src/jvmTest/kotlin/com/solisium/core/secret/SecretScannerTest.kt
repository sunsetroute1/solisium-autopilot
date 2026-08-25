package com.solisium.core.secret

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// secret-scan-allow-fixture: the constants below are made-up patterns, not a real key.
class SecretScannerTest {
    private val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val other = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

    /**
     * A scanner with a fixed environment and no default roots, so a result depends only
     * on what the test created rather than on what this machine happens to contain.
     */
    private fun scanner(env: Map<String, String> = emptyMap()) = SecretScanner(
        env = { name -> env[name] },
        useDefaultRoots = false,
    )

    private fun tempDir(): Path = Files.createTempDirectory("solisium-scan")

    @Test
    fun findsAKeyInAnEnvironmentVariable() {
        val report = scanner(mapOf("SOLISIUM_AES_KEY" to "0x$key")).scan()
        val found = report.candidates.single { it.source.contains("SOLISIUM_AES_KEY") }
        assertEquals(key, found.keyHex)
        assertEquals(AesKey.fingerprint(key), found.fingerprint)
    }

    @Test
    fun ignoresAnEnvironmentVariableHoldingSomethingElse() {
        val report = scanner(mapOf("AES_KEY" to "not-a-key")).scan()
        assertTrue(report.candidates.none { it.source.contains("AES_KEY") })
    }

    @Test
    fun findsAFileThatHoldsNothingButAKey() {
        val dir = tempDir()
        Files.writeString(dir.resolve("aes.txt"), "$key\n")
        val report = scanner().scan(listOf(dir))
        assertEquals(listOf(key), report.candidates.map { it.keyHex })
        assertTrue(report.candidates.single().source.contains("aes.txt"))
        assertTrue(report.candidates.single().evidence.contains("only the key"))
    }

    @Test
    fun findsALabelledKeyAndReportsTheFieldAsEvidence() {
        val dir = tempDir()
        Files.writeString(dir.resolve("config.json"), """{"aesKey":"$key"}""")
        val report = scanner().scan(listOf(dir))
        assertEquals(listOf(key), report.candidates.map { it.keyHex })
        assertTrue(report.candidates.single().evidence.contains("aesKey"), "got ${report.candidates}")
    }

    /** The regression that made the first version of this scan worthless. */
    @Test
    fun doesNotReportContentHashesFromAManifest() {
        val dir = tempDir()
        Files.writeString(
            dir.resolve("manifest.json"),
            """{"entries":[{"file":"a.pak","sha256":"$key"},{"file":"b.pak","hash":"$other"}]}""",
        )
        val report = scanner().scan(listOf(dir))
        assertTrue(report.candidates.isEmpty(), "digests are not keys, got ${report.candidates}")
    }

    @Test
    fun readsOnlyPlausibleFileTypes() {
        val dir = tempDir()
        Files.writeString(dir.resolve("notes.md"), key)
        Files.writeString(dir.resolve("config.json"), """{"key":"$key"}""")
        val report = scanner().scan(listOf(dir))
        assertEquals(1, report.distinctKeys)
        assertTrue(report.candidates.single().source.endsWith("config.json"), "got ${report.candidates}")
    }

    @Test
    fun skipsBinaryFilesThatHappenToHaveAScannedExtension() {
        val dir = tempDir()
        Files.write(dir.resolve("blob.key"), byteArrayOf(0x00, 0x01, 0x02) + key.toByteArray())
        val report = scanner().scan(listOf(dir))
        assertTrue(report.candidates.isEmpty(), "a NUL-containing file is not text")
    }

    @Test
    fun skipsFilesOverTheSizeCap() {
        val dir = tempDir()
        Files.writeString(dir.resolve("big.txt"), "aes_key=$key" + "\n#pad".repeat(1024))
        val small = SecretScanner(env = { null }, maxFileBytes = 512, useDefaultRoots = false)
        assertTrue(small.scan(listOf(dir)).candidates.isEmpty())
    }

    @Test
    fun reportsDistinctKeysOnce() {
        val dir = tempDir()
        Files.writeString(dir.resolve("a.txt"), key)
        Files.writeString(dir.resolve("b.txt"), key)
        Files.writeString(dir.resolve("c.txt"), other)
        val report = scanner().scan(listOf(dir))
        assertEquals(2, report.distinctKeys)
    }

    @Test
    fun honoursTheDepthLimit() {
        val dir = tempDir()
        var deep = dir
        repeat(8) { level ->
            deep = deep.resolve("level$level")
            Files.createDirectories(deep)
        }
        Files.writeString(deep.resolve("aes.txt"), key)
        Files.writeString(dir.resolve("readme.md"), "nothing here")
        val shallow = SecretScanner(env = { null }, maxDepth = 2, useDefaultRoots = false)
        assertTrue(shallow.scan(listOf(dir)).candidates.isEmpty(), "a key eight levels down is out of scope")
    }

    @Test
    fun anUnreadableRootIsRecordedRatherThanThrown() {
        val missing = tempDir().resolve("does-not-exist")
        val report = scanner().scan(listOf(missing))
        assertTrue(report.candidates.isEmpty())
        assertFalse(report.searchedRoots.contains(missing), "a missing root is not searched")
    }

    @Test
    fun candidatesNeverPrintTheirKey() {
        val candidate = KeyCandidate("somewhere", key, "field \"aesKey\"")
        assertFalse(candidate.toString().contains(key), "toString leaked the key")
        assertFalse(listOf(candidate).toString().contains(key), "a list of candidates leaked the key")
        val report = ScanReport(listOf(candidate), emptyList(), 1, emptyList())
        assertFalse(report.toString().contains(key), "the report leaked the key")
    }
}
