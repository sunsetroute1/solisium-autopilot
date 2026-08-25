package com.solisium.core.meta

object TextNorm {
    private val tags = Regex("<[^>]+>")
    private val junk = Regex("[^a-z0-9]+")

    fun stripMarkup(raw: String): String =
        raw.replace(tags, "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    fun fold(raw: String): String = junk.replace(stripMarkup(raw).lowercase(), " ").trim()

    fun likelySame(a: String?, b: String?): Boolean {
        val left = a?.let(::fold).orEmpty()
        val right = b?.let(::fold).orEmpty()
        if (left.length < 4 || right.length < 4) return false
        if (left == right) return true
        val shorter = if (left.length <= right.length) left else right
        val longer = if (left.length <= right.length) right else left
        return shorter.length >= 8 && longer.contains(shorter)
    }
}
