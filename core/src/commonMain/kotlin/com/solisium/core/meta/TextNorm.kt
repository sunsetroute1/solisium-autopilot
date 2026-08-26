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

    /**
     * Same as [likelySame], plus a single-character slip on the folded string
     * (`Calenthia` vs `Calanthia`). Used for typeahead, not for inventing names.
     */
    fun nearMatch(a: String?, b: String?): Boolean {
        if (likelySame(a, b)) return true
        val left = a?.let(::fold).orEmpty()
        val right = b?.let(::fold).orEmpty()
        if (left.length < 6 || right.length < 6) return false
        if (editDistanceAtMost(left, right, 1)) return true
        return editDistanceAtMost(left.replace(" ", ""), right.replace(" ", ""), 1)
    }

    internal fun editDistanceAtMost(left: String, right: String, max: Int): Boolean {
        if (kotlin.math.abs(left.length - right.length) > max) return false
        if (left == right) return true
        val rows = left.length + 1
        val cols = right.length + 1
        var prev = IntArray(cols) { it }
        var curr = IntArray(cols)
        for (i in 1 until rows) {
            curr[0] = i
            var rowMin = curr[0]
            val a = left[i - 1]
            for (j in 1 until cols) {
                val cost = if (a == right[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return false
            val swap = prev
            prev = curr
            curr = swap
        }
        return prev[right.length] <= max
    }
}
