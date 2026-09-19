package com.solisium.core.meta

import java.time.Instant
import java.util.Locale

/**
 * Parses public MetaForge TL timer page HTML (SSR data + quiz list).
 * Cadence constants match their client bundle (11806 s cycle, 240 s open window).
 *
 * MetaForge currently copies the EU [anchorIso] onto NA/Asia. NA's fallback is an
 * in-game opening (2026-09-17 7:41 PM Denver), ~4036 s after that copied EU slot.
 */
object MetaForgeThroneLiberty {
    const val TIMER_PAGE_URL = "https://metaforge.app/throne-and-liberty/timer"
    const val CYCLE_MS = 11_806_000L
    const val OPEN_MS = 240_000L
    const val FALLBACK_ANCHOR_ISO = "2026-08-28T18:55:30+03:00"
    const val FALLBACK_ANCHOR_ISO_NA = "2026-09-17T19:41:00-06:00"

    private val ANCHOR_REGEX = Regex("""anchorIso:"([^"]+)"""")
    private val REGION_ANCHOR_REGEX = Regex("""(eu|na|asia):\{anchorIso:"([^"]+)"""")
    private val QUIZ_LINE = Regex("""^-\s+(.+?)\.\s+(TRUE|FALSE)\s*$""")

    fun fallbackAnchorIso(regionId: String?): String = when (regionId?.lowercase()) {
        "na" -> FALLBACK_ANCHOR_ISO_NA
        else -> FALLBACK_ANCHOR_ISO
    }

    fun fallbackAnchorEpochMs(regionId: String?): Long =
        Instant.parse(fallbackAnchorIso(regionId)).toEpochMilli()

    fun parseAnchorEpochMs(html: String, regionId: String? = null): Long? =
        parseAnchorIso(html, regionId)?.let { Instant.parse(it).toEpochMilli() }

    fun parseAnchorIso(html: String, regionId: String? = null): String? {
        val wanted = regionId?.lowercase()
        if (wanted != null) {
            parseAnchorsByRegion(html)[wanted]?.let { return it }
        }
        return ANCHOR_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    fun parseAnchorsByRegion(html: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (match in REGION_ANCHOR_REGEX.findAll(html)) {
            val region = match.groupValues[1].lowercase()
            val iso = match.groupValues[2].trim()
            if (iso.isNotEmpty()) out[region] = iso
        }
        return out
    }

    fun parseTalkingWallStatements(html: String): List<MetaForgeWallStatement> =
        html.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                val match = QUIZ_LINE.matchEntire(line) ?: return@mapNotNull null
                val statement = match.groupValues[1].trim()
                val answerTrue = match.groupValues[2].equals("TRUE", ignoreCase = true)
                MetaForgeWallStatement(statement = statement, answerTrue = answerTrue)
            }
            .toList()

    fun cycleLabel(): String {
        val totalSec = (CYCLE_MS / 1000).toInt()
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return buildString {
            append("${hours}h ${minutes}m")
            if (seconds != 0) append(" ${seconds}s")
        }
    }
}

data class MetaForgeWallStatement(
    val statement: String,
    val answerTrue: Boolean,
)
