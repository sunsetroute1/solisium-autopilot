package com.solisium.core.meta

import com.solisium.core.query.BuildGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestlogParserTest {
    @Test
    fun stripsMarkupAndKeepsQuestlogItemRows() {
        val json = """
            {"result":{"data":[
              {"id":"bow_c_t1_nomal_001","name":"Sparring <span class='font-black'>Longbow</span>","grade":"11","dbType":"item","mainCategory":"weapons","subCategory":"bow"},
              {"id":"bow_c_t1_nomal_001","name":"Sparring Longbow","grade":"11","dbType":"recipe","mainCategory":"weapon","subCategory":"bow"}
            ]}}
        """.trimIndent()
        val hits = QuestlogParser.searchHits(json)
        assertEquals("Sparring Longbow", hits.first().name)
        assertEquals("item · weapons · 11", hits.first().detail)
        assertEquals(2, hits.size)
    }

    @Test
    fun skillSetsFilterToTheGoalWeapon() {
        val json = """
            {"result":{"data":[
              {"id":"a","name":"Piercing Strike","mainCategory":"crossbow","skillType":"passive"},
              {"id":"b","name":"Fireball","mainCategory":"staff","skillType":"active"}
            ]}}
        """.trimIndent()
        val hits = QuestlogParser.skillHits(json, BuildGoal.RangedDps)
        assertEquals(listOf("Piercing Strike"), hits.map { it.name })
    }

    @Test
    fun characterSlugNotFoundIsExplicit() {
        val json = """{"result":{"data":{"status":"NOT_FOUND"}}}"""
        val (hits, warning) = QuestlogParser.characterHits(json, "missing-hero")
        assertTrue(hits.isEmpty())
        assertTrue(warning!!.contains("missing-hero"))
    }

    @Test
    fun characterPayloadCollectsEquippedNames() {
        val json = """
            {"result":{"data":{
              "name":"Bow/Staff PvE",
              "slug":"bow-staff-pve",
              "equipment":[
                {"slot":"head","item":{"name":"Frost Lord's Black Scale Helm"}},
                {"slot":"weapon","item":{"name":"Tevent's Arc of Wailing Death"}}
              ]
            }}}
        """.trimIndent()
        val (hits, warning) = QuestlogParser.characterHits(json, "bow-staff-pve")
        assertEquals(null, warning)
        assertTrue(hits.any { it.name == "Bow/Staff PvE" })
        assertTrue(hits.any { it.name.contains("Frost Lord") })
        assertTrue(hits.any { it.name.contains("Tevent") })
    }
}

class TldbParserTest {
    @Test
    fun readsPatchBannerFromHomepageHtml() {
        val html = """TLDB has been updated for <a href="/db/items/page/1?added_in_patch=1">Patch 3.18.0</a>"""
        assertEquals("TLDB patch 3.18.0", TldbParser.patchLabel(html))
    }
}

class CommunityMetaClientTest {
    @Test
    fun fetchUsesInjectedHttpAndDoesNotInventRows() {
        val bodies = mapOf(
            CommunityMetaClient.questlogSearchUrl("longbow") to
                """{"result":{"data":[{"id":"x","name":"Sparring Longbow","grade":"11","dbType":"item","mainCategory":"weapons"}]}}""",
            CommunityMetaClient.questlogSearchUrl("crossbow") to """{"result":{"data":[]}}""",
            CommunityMetaClient.questlogSearchUrl("bow") to """{"result":{"data":[]}}""",
            CommunityMetaClient.questlogSkillSetsUrl() to
                """{"result":{"data":[{"id":"s","name":"Zephyr","mainCategory":"bow","skillType":"active"}]}}""",
            "https://tldb.info/" to "updated for <a>Patch 3.18.0</a>",
        )
        val client = CommunityMetaClient(
            http = HttpFetcher { url -> bodies[url] ?: error("unexpected $url") },
            clock = { "2026-08-25T00:00:00Z" },
        )
        val snapshot = client.fetch(BuildGoal.RangedDps)
        assertEquals("2026-08-25T00:00:00Z", snapshot.fetchedAt)
        assertEquals(listOf("questlog", "tldb"), snapshot.sources)
        assertEquals("TLDB patch 3.18.0", snapshot.patchLabel)
        assertEquals("Sparring Longbow", snapshot.items.single().name)
        assertEquals("Zephyr", snapshot.skills.single().name)
        assertTrue(snapshot.notes.any { it.contains("not extracted") })
    }

    @Test
    fun sourceFailureIsAWarningNotACrash() {
        val client = CommunityMetaClient(
            http = HttpFetcher { error("offline") },
            clock = { "t" },
        )
        val snapshot = client.fetch(BuildGoal.Tank)
        assertTrue(snapshot.items.isEmpty())
        assertTrue(snapshot.warnings.size >= 3)
        assertTrue(snapshot.warnings.all { it.contains("offline") })
    }

    @Test
    fun missingCharacterSlugIsAWarning() {
        val client = CommunityMetaClient(
            http = HttpFetcher { """{"result":{"data":{"status":"NOT_FOUND"}}}""" },
            clock = { "t" },
        )
        val snapshot = client.fetchCharacter("nope")
        assertTrue(snapshot.items.isEmpty())
        assertTrue(snapshot.warnings.any { it.contains("nope") })
    }

    @Test
    fun characterGearLandsOnItemsAndTitleOnBuilds() {
        val json = """
            {"result":{"data":{
              "name":"Bow/Staff PvE",
              "equipment":[{"slot":"weapon","item":{"name":"Tevent's Arc of Wailing Death"}}]
            }}}
        """.trimIndent()
        val client = CommunityMetaClient(
            http = HttpFetcher { json },
            clock = { "t" },
        )
        val snapshot = client.fetchCharacter("https://questlog.gg/throne-and-liberty/en/character-builder/bow-staff-pve")
        assertEquals("Bow/Staff PvE", snapshot.builds.single().name)
        assertTrue(snapshot.items.any { it.name.contains("Tevent") })
        assertTrue(snapshot.notes.any { it.contains("bow-staff-pve") })
    }

    @Test
    fun slugFromInputAcceptsBuilderUrls() {
        assertEquals(
            "TheGrayMadnessAndRageOfSpirit",
            CommunityMetaClient.slugFromInput(
                "https://questlog.gg/throne-and-liberty/en/character-builder/TheGrayMadnessAndRageOfSpirit?x=1",
            ),
        )
        assertEquals("plain-slug", CommunityMetaClient.slugFromInput("plain-slug"))
    }
}

class TextNormTest {
    @Test
    fun likelySameIgnoresMarkupAndCase() {
        assertTrue(TextNorm.likelySame("Sparring Longbow", "sparring  longbow"))
        assertTrue(TextNorm.likelySame("Longbow of Undead Skewering", "of Undead Skewering"))
    }
}
