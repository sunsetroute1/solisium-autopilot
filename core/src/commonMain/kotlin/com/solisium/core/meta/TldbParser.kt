package com.solisium.core.meta

object TldbParser {
    private val patchBanner = Regex("""updated for\s*<a[^>]*>Patch\s+([0-9.]+)</a>""", RegexOption.IGNORE_CASE)
    private val versionLabel = Regex("""Version:</span>\s*<span[^>]*>\s*([0-9.]+)\s*</span>""", RegexOption.IGNORE_CASE)
    private val plainPatch = Regex("""Patch\s+([0-9]+\.[0-9]+(?:\.[0-9]+)?)""")

    fun patchLabel(html: String): String? {
        patchBanner.find(html)?.groupValues?.getOrNull(1)?.let { return "TLDB patch $it" }
        versionLabel.find(html)?.groupValues?.getOrNull(1)?.let { return "TLDB version $it" }
        plainPatch.find(html)?.groupValues?.getOrNull(1)?.let { return "TLDB patch $it" }
        return null
    }
}
