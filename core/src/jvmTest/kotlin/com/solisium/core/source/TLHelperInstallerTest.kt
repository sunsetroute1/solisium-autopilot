package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TLHelperInstallerTest {
    @Test
    fun copiesBundledCheckoutAndSkipsSecrets() {
        val bundled = checkout("bundled")
        Files.writeString(bundled.resolve("config.local.json"), """{"aesKey":"no"}""")
        Files.writeString(bundled.resolve("aes.txt"), "no")
        Files.createDirectories(bundled.resolve("out"))
        Files.writeString(bundled.resolve("out").resolve("noise.txt"), "skip")
        Files.writeString(bundled.resolve("keep.txt"), "yes")
        val home = Files.createTempDirectory("solisium-tlh-inst-home")
        val local = Files.createTempDirectory("solisium-tlh-inst-local")
        val locator = locator(home, local)
        val dest = locator.defaultInstallRoot()
        val installed = TLHelperInstaller(
            locator = locator,
            installRoot = dest,
            bundled = bundled,
            vendorRoots = emptyList(),
            download = { _, _ -> error("should not download") },
        ).install().getOrThrow()
        assertEquals(dest, installed)
        assertTrue(locator.isCheckout(installed))
        assertEquals(dest.toString(), Files.readString(home.resolve("tl-helper-root.txt")))
        assertTrue(Files.isRegularFile(installed.resolve("keep.txt")))
        assertFalse(Files.exists(installed.resolve("config.local.json")))
        assertFalse(Files.exists(installed.resolve("aes.txt")))
        assertFalse(Files.exists(installed.resolve("out")))
    }

    @Test
    fun keepsAnExistingCheckoutWithoutDownloading() {
        val existing = checkout("already")
        val home = Files.createTempDirectory("solisium-tlh-inst-home")
        val local = Files.createTempDirectory("solisium-tlh-inst-local")
        val locator = locator(home, local, env = { if (it == "SOLISIUM_TL_HELPER") existing.toString() else null })
        val installed = TLHelperInstaller(
            locator = locator,
            installRoot = locator.defaultInstallRoot(),
            bundled = null,
            vendorRoots = emptyList(),
            download = { _, _ -> error("should not download") },
        ).install().getOrThrow()
        assertEquals(existing, installed)
        assertEquals(existing.toAbsolutePath().normalize().toString(), Files.readString(home.resolve("tl-helper-root.txt")))
    }

    @Test
    fun installsFromADownloadedGithubZip() {
        val zipSource = checkout("zip-inner")
        Files.writeString(zipSource.resolve("from-zip.txt"), "ok")
        val zip = Files.createTempFile("solisium-tlh-zip", ".zip")
        zipCheckout(zip, "tl-helper-master", zipSource)
        val home = Files.createTempDirectory("solisium-tlh-inst-home")
        val local = Files.createTempDirectory("solisium-tlh-inst-local")
        val locator = locator(home, local)
        val dest = locator.defaultInstallRoot()
        val installed = TLHelperInstaller(
            locator = locator,
            installRoot = dest,
            bundled = null,
            vendorRoots = emptyList(),
            download = { _, destZip -> Files.copy(zip, destZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING) },
        ).install().getOrThrow()
        assertEquals(dest, installed)
        assertTrue(Files.isRegularFile(installed.resolve("from-zip.txt")))
        assertTrue(locator.isCheckout(installed))
    }

    @Test
    fun skipsSecretRelativePaths() {
        assertTrue(TLHelperInstaller.shouldSkip(Path.of("src", "TlCollector", "config.local.json")))
        assertTrue(TLHelperInstaller.shouldSkip(Path.of("out", "decoded.json")))
        assertFalse(TLHelperInstaller.shouldSkip(Path.of("scripts", "update-tl-helper.mjs")))
    }

    private fun locator(
        home: Path,
        local: Path,
        env: (String) -> String? = { null },
    ): TLHelperLocator = TLHelperLocator(
        env = env,
        isFile = { Files.isRegularFile(it) },
        solisiumHome = home,
        userHome = Files.createTempDirectory("solisium-tlh-inst-unused"),
        localAppData = local,
        bundledCheckouts = emptyList(),
        workingDir = Files.createTempDirectory("solisium-tlh-inst-cwd"),
        wellKnownRoots = emptyList(),
    )

    private fun checkout(label: String): Path {
        val root = Files.createTempDirectory("solisium-tlh-$label")
        Files.createDirectories(root.resolve("scripts"))
        Files.writeString(root.resolve("scripts").resolve(TLHelperLocator.UPDATE_SCRIPT), "// fixture")
        return root
    }

    private fun zipCheckout(zip: Path, rootName: String, source: Path) {
        ZipOutputStream(Files.newOutputStream(zip)).use { zout ->
            Files.walk(source).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    val rel = source.relativize(file).toString().replace('\\', '/')
                    zout.putNextEntry(ZipEntry("$rootName/$rel"))
                    Files.copy(file, zout)
                    zout.closeEntry()
                }
            }
        }
    }
}
