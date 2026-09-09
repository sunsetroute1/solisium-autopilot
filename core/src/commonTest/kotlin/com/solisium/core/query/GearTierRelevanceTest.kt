package com.solisium.core.query

import com.solisium.core.domain.CommunityHit
import com.solisium.core.domain.CommunitySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GearTierRelevanceTest {
    @Test
    fun silentWhenNoEvidence() {
        val verdict = GearTierRelevance.evaluate(
            GearTierRelevance.Facts(
                sourceRowId = "bow_aa_t2_raid_001",
                name = "Raid Bow",
                pieceTier = 2,
                basePower = 40,
                higherGenMaxPower = 80,
                higherGenExists = true,
            ),
        )
        assertFalse(verdict.relevant)
        assertEquals("T2", verdict.badge)
        assertTrue(verdict.note!!.contains("Later generations"))
    }

    @Test
    fun statsCompeteAgainstLaterSibling() {
        val verdict = GearTierRelevance.evaluate(
            GearTierRelevance.Facts(
                sourceRowId = "bow_aa_t2_raid_001",
                name = "Raid Bow",
                pieceTier = 2,
                basePower = 70,
                higherGenMaxPower = 80,
                higherGenExists = true,
            ),
        )
        assertTrue(verdict.relevant)
        assertEquals("T2 · stats compete", verdict.badge)
        assertTrue(verdict.note!!.contains("warehouse combat power 70", ignoreCase = true))
        assertTrue(verdict.note!!.contains("80"))
    }

    @Test
    fun usedInQuestlogLoadout() {
        val community = CommunitySnapshot(
            fetchedAt = "2026-09-08T00:00:00Z",
            sources = listOf("questlog"),
            patchLabel = "Nix 4.0",
            items = listOf(
                CommunityHit("questlog", "Raid Bow", "gear · slug · weapon", null, null, "bow_aa_t2_raid_001"),
            ),
            skills = emptyList(),
            builds = emptyList(),
            notes = emptyList(),
            warnings = emptyList(),
        )
        val hits = GearTierRelevance.communityHits("Raid Bow", "bow_aa_t2_raid_001", community)
        assertTrue(hits > 0)
        val verdict = GearTierRelevance.evaluate(
            GearTierRelevance.Facts(
                sourceRowId = "bow_aa_t2_raid_001",
                name = "Raid Bow",
                pieceTier = 2,
                communityHits = hits,
                communityPatch = community.patchLabel,
            ),
        )
        assertTrue(verdict.relevant)
        assertEquals("T2 · used in builds", verdict.badge)
        assertTrue(verdict.note!!.contains("Nix 4.0"))
    }

    @Test
    fun exclusiveTraitOnLowerGen() {
        val verdict = GearTierRelevance.evaluate(
            GearTierRelevance.Facts(
                sourceRowId = "bow_aa_t2_raid_001",
                name = "Raid Bow",
                pieceTier = 2,
                higherGenExists = true,
                exclusiveTraitUnlock = true,
            ),
        )
        assertTrue(verdict.relevant)
        assertTrue(verdict.note!!.contains("selected trait"))
    }

    @Test
    fun noTierNoBadge() {
        val verdict = GearTierRelevance.evaluate(
            GearTierRelevance.Facts(sourceRowId = "plain", name = "X", pieceTier = null),
        )
        assertEquals("", verdict.badge)
        assertNull(verdict.note)
    }

    @Test
    fun higherGenMaxFromFamily() {
        val (exists, max) = GearTierRelevance.higherGenMaxPower(
            sourceRowId = "bow_aa_t2_raid_001",
            powerById = mapOf(
                "bow_aa_t2_raid_001" to 60L,
                "bow_aa_t3_raid_001" to 75L,
                "bow_aa_t4_raid_001" to 90L,
            ),
            familyIds = setOf("bow_aa_t2_raid_001", "bow_aa_t3_raid_001", "bow_aa_t4_raid_001"),
        )
        assertTrue(exists)
        assertEquals(90L, max)
    }
}
