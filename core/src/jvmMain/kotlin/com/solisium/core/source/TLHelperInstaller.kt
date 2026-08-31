package com.solisium.core.source

import com.solisium.core.bootstrap.InstallResources
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.util.zip.ZipInputStream

/**
 * Puts a TL-Helper checkout on this machine so extract can run.
 *
 * Prefers a checkout that is already present, then the copy bundled in the
 * Solisium image, then `vendor/tl-helper` in a source tree, then a download
 * from [TLHelperLocator.CHECKOUT_URL]. Keys and local config are never copied.
 */
class TLHelperInstaller(
    private val locator: TLHelperLocator = TLHelperLocator(),
    private val installRoot: Path = locator.defaultInstallRoot(),
    private val bundled: Path? = InstallResources.tlHelper(),
    private val vendorRoots: List<Path> = locator.vendorCandidates(),
    private val download: (URI, Path) -> Unit = Companion::downloadZip,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    fun install(): Result<Path> = runCatching {
        locator.find()?.takeIf { locator.isCheckout(it) }?.let { found ->
            locator.remember(found)
            return@runCatching found
        }
        if (locator.isCheckout(installRoot)) {
            locator.remember(installRoot)
            return@runCatching installRoot
        }
        val source = bundled?.takeIf { locator.isCheckout(it) }
            ?: vendorRoots.firstOrNull { locator.isCheckout(it) }
            ?: downloadCheckout()
        val dest = prepareDest()
        copyCheckout(source, dest)
        if (!locator.isCheckout(dest)) {
            error("Installed files at $dest but scripts\\${TLHelperLocator.UPDATE_SCRIPT} is missing.")
        }
        locator.remember(dest)
        dest
    }

    private fun prepareDest(): Path {
        if (!Files.exists(installRoot)) return installRoot
        if (locator.isCheckout(installRoot)) return installRoot
        val fallback = locator.solisiumHome().resolve("tl-helper")
        if (!Files.exists(fallback) || locator.isCheckout(fallback)) return fallback
        error(
            "Refusing to overwrite $installRoot or $fallback; neither is a TL-Helper checkout.",
        )
    }

    private fun downloadCheckout(): Path {
        val scratch = Files.createTempDirectory("solisium-tlh-${clockMillis()}")
        val zip = scratch.resolve("tl-helper.zip")
        download(URI(ARCHIVE_URL), zip)
        if (!Files.isRegularFile(zip) || Files.size(zip) < 1) {
            error("Download from ${TLHelperLocator.CHECKOUT_URL} did not produce a zip.")
        }
        val unpacked = scratch.resolve("unpacked")
        unzip(zip, unpacked)
        return findCheckout(unpacked)
            ?: error("Downloaded zip from ${TLHelperLocator.CHECKOUT_URL} has no scripts\\${TLHelperLocator.UPDATE_SCRIPT}.")
    }

    private fun findCheckout(root: Path): Path? {
        if (locator.isCheckout(root)) return root
        if (!Files.isDirectory(root)) return null
        return Files.list(root).use { stream ->
            stream.filter { Files.isDirectory(it) && locator.isCheckout(it) }.findFirst().orElse(null)
        }
    }

    companion object {
        const val ARCHIVE_URL = "https://github.com/sunsetroute1/tl-helper/archive/refs/heads/master.zip"

        val skipDirectoryNames = setOf(
            ".git",
            "node_modules",
            "bin",
            "obj",
            "out",
            "tools",
            ".claude",
            ".wrangler",
        )

        val skipFileNames = setOf(
            ".env",
            ".env.local",
            "config.local.json",
            "aes.txt",
            "aes.key",
            "secrets.properties",
            "source-manifest.json",
        )

        fun shouldSkip(relative: Path): Boolean {
            for (i in 0 until relative.nameCount) {
                val name = relative.getName(i).toString()
                if (name in skipDirectoryNames || name in skipFileNames) return true
            }
            return false
        }

        fun copyCheckout(from: Path, to: Path) {
            Files.createDirectories(to)
            Files.walkFileTree(
                from,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val rel = from.relativize(dir)
                        if (rel.nameCount > 0 && shouldSkip(rel)) return FileVisitResult.SKIP_SUBTREE
                        Files.createDirectories(to.resolve(rel.toString()))
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val rel = from.relativize(file)
                        if (shouldSkip(rel)) return FileVisitResult.CONTINUE
                        val dest = to.resolve(rel.toString())
                        Files.createDirectories(dest.parent)
                        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }

        fun unzip(zip: Path, dest: Path) {
            Files.createDirectories(dest)
            ZipInputStream(Files.newInputStream(zip)).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val target = dest.resolve(entry.name).normalize()
                    if (!target.startsWith(dest)) {
                        error("refusing zip entry outside destination")
                    }
                    if (entry.isDirectory) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(zin, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }

        fun downloadZip(url: URI, dest: Path) {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()
            val request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMinutes(3))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(dest))
            val code = response.statusCode()
            if (code !in 200..299) {
                error("Download from $url failed with HTTP $code.")
            }
        }
    }
}
