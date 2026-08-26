package com.solisium.core.domain

import com.solisium.core.json.JsonParser
import com.solisium.core.meta.CommunityWeaponClasses
import com.solisium.core.query.WeaponClassResolver
import com.solisium.core.source.WeaponClassMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeaponClassTest {
    @Test
    fun communityFallbackNamesGreatswordAndSpearGladiator() {
        val match = WeaponClassResolver.resolve(emptyList(), "kSword2h", "EItemCategory::kSpear")
        assertEquals("Gladiator", match.name)
        assertEquals(ClassSource.COMMUNITY, match.source)
        assertEquals("Greatsword · Spear", match.weaponsLabel)
    }

    @Test
    fun buildClassOptionExposesPairTokensForSkillCoverage() {
        val option = BuildClassOption("Gladiator", "kSpear", "kSword2h", ClassSource.COMMUNITY)
        assertEquals(setOf("kSpear", "kSword2h"), option.tokens)
        assertEquals(setOf("spear", "sword2h"), option.skillCategories())
        assertEquals("Spear · Greatsword", option.weaponsLabel)
    }

    @Test
    fun extractedRowsBeatCommunityLabels() {
        val extracted = listOf(
            GameClass("snap", "TLPcClass", "ravager", "Warehouse Ravager", "kDagger", "kSword2h"),
        )
        val match = WeaponClassResolver.resolve(extracted, "kSword2h", "kDagger")
        assertEquals("Warehouse Ravager", match.name)
        assertEquals(ClassSource.EXTRACTED, match.source)
    }

    @Test
    fun unknownGauntletPairIsNotInvented() {
        val match = WeaponClassResolver.resolve(emptyList(), "kGauntlet", "kSpear")
        assertNull(match.name)
        assertNull(match.source)
        assertEquals("Gauntlet · Spear", match.weaponsLabel)
        assertTrue(match.pairResolved)
    }

    @Test
    fun manualOverrideKeepsTypedNameWhenWeaponsChange() {
        val suggested = WeaponClassResolver.resolve(emptyList(), "kSword2h", "kSpear")
        val stored = WeaponClassResolver.applyStored("My Override", ClassSource.MANUAL, suggested)
        assertEquals("My Override", stored.name)
        assertEquals(ClassSource.MANUAL, stored.source)
        assertEquals("Gladiator", suggested.name)
    }

    @Test
    fun mapperReadsTlPcClassWeaponFields() {
        val json = JsonParser.parse(
            """{"weapon_a":"EItemCategory::kSword2h","weapon_b":"kSpear","name":"Gladiator"}""",
        )
        val parsed = WeaponClassMapper.parse("TLPcClass", "gladiator", null, json)!!
        assertEquals("kSpear", parsed.weaponA)
        assertEquals("kSword2h", parsed.weaponB)
        assertEquals("Gladiator", parsed.name)
    }

    @Test
    fun mapperIgnoresMasteryLooksAndIncompletePairs() {
        val looks = JsonParser.parse("""{"weapon_a":"kBow","weapon_b":"kStaff"}""")
        assertNull(WeaponClassMapper.parse("TLTableWeaponSpecializationLooks", "Bow_Hero", "Hero", looks))
        val incomplete = JsonParser.parse("""{"weapon_a":"kGauntlet"}""")
        assertNull(WeaponClassMapper.parse("TLPcClass", "fist", "Brawler", incomplete))
    }

    @Test
    fun communityTextParserCapturesNewTitles() {
        CommunityWeaponClasses.clearOverlay()
        try {
            val rows = CommunityWeaponClasses.parseFromText(
                "Gladiator – Spear + Greatsword. Gauntlet and Spear – Ironfist.",
            )
            assertTrue(rows.any { it.name == "Gladiator" })
            val gauntlet = rows.single { it.name == "Ironfist" }
            assertEquals("kGauntlet", gauntlet.weaponA)
            assertEquals("kSpear", gauntlet.weaponB)
            CommunityWeaponClasses.merge(rows)
            assertEquals("Ironfist", CommunityWeaponClasses.lookup("kSpear", "kGauntlet")?.name)
        } finally {
            CommunityWeaponClasses.clearOverlay()
        }
    }
}
