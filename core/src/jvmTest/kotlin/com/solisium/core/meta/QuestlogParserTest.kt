package com.solisium.core.meta

import com.solisium.core.query.BuildGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("bow_c_t1_nomal_001", hits.first().entityId)
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
    fun itemDetailParsesMainStatsAndTraits() {
        val json = """
            {"result":{"data":{
              "id":"bow_aa_t2_raid_001",
              "description":"A purple string is drawn from the depths.",
              "requiredLevel":1,
              "sellPrice":0,
              "tradeCategory":"bow",
              "isExchangeable":false,
              "isSellable":false,
              "isStorable":true,
              "itemStats":{
                "main":{"75":{"mainhand":{"min":88,"max":352,"statId":"attack_power_main_hand"},"extra":{"attack_speed_main_hand":776}}},
                "extra":{"75":{"dex":17}},
                "traits":{"all_accuracy":[200,400,600,800]}
              },
              "itemAvailablePerks":[{"name":"Skill Core: Deadly Grave","passive":{"text":"Creates an area."}}],
              "itemIsContainedInItems":[{"name":"Calanthia Weapon Selection Chest","id":"chest_1","dbType":"item","probability":1,"quantity":1,"dropType":"selectable","mainCategory":"other"}],
              "itemDroppedFromNpcs":[{"id":"FD_L03_M_Golem_Talus_001","name":"Talus","dbType":"npc","mainCategory":"boss","level":46,"quantity":1,"dropType":"random","probability":0.0769232,"dropCondition":"normalDrop"}]
            }}}
        """.trimIndent()
        val detail = QuestlogParser.itemDetail(json)!!
        assertEquals("A purple string is drawn from the depths.", detail.description)
        assertTrue(detail.statLines.any { it.label == "Attack power main hand" && it.value == "88 – 352" })
        assertTrue(detail.traitLines.any { it.label == "All accuracy" && it.tiers.contains("200") })
        assertEquals(listOf("Skill Core: Deadly Grave — Creates an area."), detail.perkSummaries)
        assertEquals(listOf("Calanthia Weapon Selection Chest"), detail.dropSources)
        assertEquals(1, detail.droppedFromNpcs.size)
        assertEquals("Talus", detail.droppedFromNpcs.first().name)
        assertEquals("7.69%", detail.droppedFromNpcs.first().probabilityLabel)
    }

    @Test
    fun npcDetailParsesLootTable() {
        val json = """
            {"result":{"data":{
              "id":"FD_L03_M_Golem_Talus_001",
              "name":"Talus",
              "subtitle":"Eternal Guardian",
              "level":46,
              "mainCategory":"boss",
              "mapId":10070052,
              "npcDropsItems":[{"id":"staff_aa_t5_boss_001","name":"Talus's Crystalline Staff","dbType":"item","mainCategory":"weapons","quantity":1,"dropType":"random","probability":0.0769232}]
            }}}
        """.trimIndent()
        val npc = QuestlogParser.npcDetail(json)!!
        assertEquals("Talus", npc.name)
        assertEquals(1, npc.drops.size)
        assertEquals("Talus's Crystalline Staff", npc.drops.first().name)
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

    @Test
    fun nearMatchAcceptsAOneLetterSlipOnCalanthia() {
        assertTrue(TextNorm.nearMatch("Calenthia", "Calanthia"))
        assertTrue(TextNorm.nearMatch("Calenthia's Visage", "Calanthia's Visage"))
        assertFalse(TextNorm.nearMatch("Calenthia", "Longbow"))
    }
}
