package com.solisium.core.meta

import com.solisium.core.domain.CommunityHit
import com.solisium.core.domain.CommunitySnapshot
import com.solisium.core.domain.QuestlogItemOverlay
import com.solisium.core.domain.WeaponClassPair
import com.solisium.core.query.BuildGoal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * User-initiated community fetch. Never runs on import, never writes into game_* tables.
 * Questlog tRPC and TLDB HTML are labeled community, not extracted client data.
 */
class CommunityMetaClient(
    private val http: HttpFetcher = JvmHttpFetcher(),
    private val clock: () -> String = { Instant.now().toString() },
) {
    fun fetch(goal: BuildGoal): CommunitySnapshot {
        val sources = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val items = mutableListOf<CommunityHit>()
        val skills = mutableListOf<CommunityHit>()
        var patch: String? = null

        for (term in goal.questlogQueries) {
            runCatching {
                val body = http.get(questlogSearchUrl(term))
                val hits = QuestlogParser.searchHits(body)
                    .filter { it.detail?.startsWith("item") == true }
                items += hits
                if ("questlog" !in sources) sources += "questlog"
            }.onFailure { warnings += "Questlog search '$term' failed: ${it.message}" }
        }

        runCatching {
            val body = http.get(questlogSkillSetsUrl())
            skills += QuestlogParser.skillHits(body, goal)
            if ("questlog" !in sources) sources += "questlog"
            notes += "Questlog skill sets filtered to ${goal.skillCategories.joinToString("/")}."
        }.onFailure { warnings += "Questlog skill sets failed: ${it.message}" }

        runCatching {
            val html = http.get("https://tldb.info/")
            patch = TldbParser.patchLabel(html)
            if (patch != null) {
                sources += "tldb"
                notes += "$patch (community site, not your warehouse build)."
            } else {
                warnings += "TLDB homepage had no patch label."
            }
        }.onFailure { warnings += "TLDB homepage failed: ${it.message}" }

        val uniqueItems = items.distinctBy { TextNorm.fold(it.name) + (it.detail ?: "") }
        val uniqueSkills = skills.distinctBy { TextNorm.fold(it.name) }
        if (uniqueItems.isEmpty() && uniqueSkills.isEmpty() && warnings.isNotEmpty()) {
            notes += "Community fetch did not return usable rows."
        }
        notes += "These names are from third-party sites. They are not extracted client stats."
        return CommunitySnapshot(
            fetchedAt = clock(),
            sources = sources.distinct(),
            patchLabel = patch,
            items = uniqueItems.take(40),
            skills = uniqueSkills.take(40),
            notes = notes,
            warnings = warnings,
        )
    }

    fun fetchCharacter(slug: String, base: CommunitySnapshot? = null): CommunitySnapshot {
        val trimmed = slugFromInput(slug)
        require(trimmed.isNotEmpty()) { "Questlog character slug is empty" }
        val body = http.get(questlogCharacterUrl(trimmed))
        val (hits, missing) = QuestlogParser.characterHits(body, trimmed)
        val prior = base ?: CommunitySnapshot(
            fetchedAt = clock(),
            sources = emptyList(),
            patchLabel = null,
            items = emptyList(),
            skills = emptyList(),
            notes = emptyList(),
            warnings = emptyList(),
        )
        val notes = prior.notes.toMutableList()
        val warnings = prior.warnings.toMutableList()
        val sources = prior.sources.toMutableList()
        if ("questlog" !in sources) sources += "questlog"
        if (missing != null) {
            warnings += missing
        } else {
            notes += "Questlog character \"$trimmed\" is a community loadout, not extracted client data."
        }
        val titles = hits.filter { it.detail == "character · $trimmed" }
        val gear = hits.filter { it.detail?.startsWith("gear") == true }
        return prior.copy(
            fetchedAt = clock(),
            sources = sources.distinct(),
            items = (prior.items + gear).distinctBy { TextNorm.fold(it.name) }.take(50),
            builds = (prior.builds + titles).distinctBy { TextNorm.fold(it.name) + (it.detail ?: "") },
            notes = notes,
            warnings = warnings,
        )
    }

    /** On-demand Questlog item lookup for the gear catalog detail pane. */
    fun fetchItem(rowId: String): QuestlogItemOverlay? {
        val trimmed = rowId.trim()
        if (trimmed.isEmpty()) return null
        val body = http.get(questlogItemUrl(trimmed))
        return QuestlogParser.itemDetail(body)
    }

    fun fetchClasses(): List<WeaponClassPair> {
        val found = mutableListOf<WeaponClassPair>()
        val urls = listOf(
            "https://questlog.gg/throne-and-liberty/en/db/search?q=Gladiator",
            "https://arzyelbuilds.com/throne-and-liberty-weapons/",
        )
        for (url in urls) {
            runCatching {
                found += QuestlogParser.classPairs(http.get(url))
            }
        }
        CommunityWeaponClasses.merge(found)
        return CommunityWeaponClasses.pairs()
    }

    companion object {
        fun slugFromInput(raw: String): String {
            val trimmed = raw.trim()
            val markers = listOf("/character-builder/", "/characters/", "/character/")
            for (marker in markers) {
                val idx = trimmed.indexOf(marker, ignoreCase = true)
                if (idx >= 0) {
                    return trimmed.substring(idx + marker.length)
                        .substringBefore('/')
                        .substringBefore('?')
                        .substringBefore('#')
                }
            }
            return trimmed.trim('/')
        }

        fun questlogSearchUrl(term: String): String {
            val input = """{"searchTerm":${jsonString(term)},"language":"en","extendSearch":false}"""
            return "https://questlog.gg/throne-and-liberty/api/trpc/database.searchEntities?input=${enc(input)}"
        }

        fun questlogSkillSetsUrl(): String {
            val input = """{"language":"en"}"""
            return "https://questlog.gg/throne-and-liberty/api/trpc/skillBuilder.getSkillSets?input=${enc(input)}"
        }

        fun questlogCharacterUrl(slug: String): String {
            val input = """{"slug":${jsonString(slug)}}"""
            return "https://questlog.gg/throne-and-liberty/api/trpc/characterBuilder.getCharacter?input=${enc(input)}"
        }

        fun questlogItemUrl(rowId: String): String {
            val input = """{"id":${jsonString(rowId)},"language":"en"}"""
            return "https://questlog.gg/throne-and-liberty/api/trpc/database.getItem?input=${enc(input)}"
        }

        private fun enc(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8)

        private fun jsonString(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
