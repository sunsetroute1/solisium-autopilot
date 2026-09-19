package com.solisium.core.query

import com.solisium.core.domain.GateOfMemoryRegion
import com.solisium.core.meta.HttpFetcher
import com.solisium.core.meta.JvmHttpFetcher
import com.solisium.core.meta.MetaForgeThroneLiberty
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Caches MetaForge per-region [anchorIso] values. When MetaForge publishes the
 * same ISO for NA and EU, NA stays on the in-game fallback instead of the
 * copied EU slot.
 */
class GateOfMemoryAnchorStore(
    private val fetcher: HttpFetcher = JvmHttpFetcher(),
    private val cacheFile: Path = defaultCacheFile(),
    private val clock: () -> Instant = { Instant.now() },
) {
    private val memory = HashMap<String, Long>()

    fun anchorEpochMs(region: GateOfMemoryRegion = GateOfMemoryRegion.NA): Long {
        val id = region.id
        memory[id]?.let { return it }
        readCache()[id]?.let {
            memory[id] = it
            return it
        }
        val fallback = MetaForgeThroneLiberty.fallbackAnchorEpochMs(id)
        memory[id] = fallback
        return fallback
    }

    fun refreshFromNetwork(): RefreshResult {
        return runCatching {
            val html = fetcher.get(MetaForgeThroneLiberty.TIMER_PAGE_URL)
            val parsed = MetaForgeThroneLiberty.parseAnchorsByRegion(html)
            if (parsed.isEmpty()) {
                val single = MetaForgeThroneLiberty.parseAnchorEpochMs(html)
                    ?: return RefreshResult.Failed("MetaForge timer page had no anchorIso")
                writeCache(mapOf("eu" to single))
                memory["eu"] = single
                return RefreshResult.Updated(single, html)
            }
            val euIso = parsed["eu"]
            val stored = linkedMapOf<String, Long>()
            for ((region, iso) in parsed) {
                if (region == "na" && euIso != null && iso == euIso) continue
                stored[region] = Instant.parse(iso).toEpochMilli()
            }
            val previous = readCache()
            for ((region, ms) in previous) {
                stored.putIfAbsent(region, ms)
            }
            writeCache(stored)
            stored.forEach { (region, ms) -> memory[region] = ms }
            val na = stored["na"] ?: MetaForgeThroneLiberty.fallbackAnchorEpochMs("na")
            memory.putIfAbsent("na", na)
            RefreshResult.Updated(na, html)
        }.getOrElse { RefreshResult.Failed(it.message ?: it.toString()) }
    }

    fun refreshIfStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): RefreshResult {
        val cachedAt = cachedAtEpochMs()
        val ageMs = if (cachedAt == null) Long.MAX_VALUE else clock().toEpochMilli() - cachedAt
        return if (ageMs >= maxAgeMs) refreshFromNetwork() else RefreshResult.Skipped(anchorEpochMs())
    }

    private fun readCache(): Map<String, Long> {
        if (!Files.isRegularFile(cacheFile)) return emptyMap()
        val out = linkedMapOf<String, Long>()
        for (line in Files.readAllLines(cacheFile)) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("anchorMs=") ->
                    trimmed.removePrefix("anchorMs=").toLongOrNull()?.let { out.putIfAbsent("eu", it) }
                trimmed.startsWith("na=") ->
                    trimmed.removePrefix("na=").toLongOrNull()?.let { out["na"] = it }
                trimmed.startsWith("eu=") ->
                    trimmed.removePrefix("eu=").toLongOrNull()?.let { out["eu"] = it }
                trimmed.startsWith("asia=") ->
                    trimmed.removePrefix("asia=").toLongOrNull()?.let { out["asia"] = it }
            }
        }
        return out
    }

    private fun cachedAtEpochMs(): Long? {
        if (!Files.isRegularFile(cacheFile)) return null
        return Files.readAllLines(cacheFile)
            .firstOrNull { it.startsWith("fetchedAt=") }
            ?.removePrefix("fetchedAt=")
            ?.trim()
            ?.toLongOrNull()
    }

    private fun writeCache(anchors: Map<String, Long>) {
        Files.createDirectories(cacheFile.parent)
        val lines = mutableListOf(
            "fetchedAt=${clock().toEpochMilli()}",
            "source=${MetaForgeThroneLiberty.TIMER_PAGE_URL}",
        )
        for (region in listOf("na", "eu", "asia")) {
            anchors[region]?.let { lines.add("$region=$it") }
        }
        Files.writeString(cacheFile, lines.joinToString("\n"))
    }

    sealed interface RefreshResult {
        data class Updated(val anchorEpochMs: Long, val html: String) : RefreshResult
        data class Skipped(val anchorEpochMs: Long) : RefreshResult
        data class Failed(val message: String) : RefreshResult
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 6 * 3_600_000L

        fun defaultCacheFile(): Path =
            Path.of(System.getProperty("user.home"), ".solisium", "gate-of-memory-anchor.txt")
    }
}
