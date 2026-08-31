package com.solisium.core.meta

import com.solisium.core.domain.CommunityEventEntry
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue

/** Parses Questlog `database.getEvents` pages. Community names only. */
object EventCalendarParser {
    data class Page(
        val entries: List<CommunityEventEntry>,
        val pageCount: Int,
        val currentPage: Int,
    )

    fun page(json: String): Page {
        val root = JsonParser.parse(json)
        val data = root.child("result")?.child("data") ?: root.child("data") ?: return Page(emptyList(), 0, 0)
        val items = data.arr("pageData")
        val entries = items.mapNotNull { item ->
            val obj = item as? JsonValue.Obj ?: return@mapNotNull null
            val name = obj.str("name")?.let(TextNorm::stripMarkup)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            if (obj.bool("isDisabled") == true) return@mapNotNull null
            CommunityEventEntry(
                id = obj.str("id") ?: obj.str("compoundId") ?: name,
                name = name,
                category = obj.str("mainCategory") ?: obj.str("dbType"),
                icon = obj.str("icon"),
                createdAt = obj.str("createdAt"),
            )
        }
        return Page(
            entries = entries,
            pageCount = data.long("pageCount")?.toInt() ?: 0,
            currentPage = data.long("currentPage")?.toInt() ?: 0,
        )
    }
}
