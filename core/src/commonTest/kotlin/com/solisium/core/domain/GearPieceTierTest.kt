package com.solisium.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GearPieceTierTest {
    @Test
    fun readsGenerationToken() {
        assertEquals(1, GearPieceTier.fromRowId("bow_c_t1_nomal_001"))
        assertEquals(2, GearPieceTier.fromRowId("bow_aa_t2_raid_001"))
        assertEquals(3, GearPieceTier.fromRowId("head_leather_aa_t3_normal_006"))
        assertEquals(4, GearPieceTier.fromRowId("ring_aaa_t4_boss_001"))
        assertNull(GearPieceTier.fromRowId("plain_item_id"))
    }

    @Test
    fun familyKeyStripsGeneration() {
        assertEquals(
            "bow_aa_t*_raid_001",
            GearPieceTier.familyKey("bow_aa_t2_raid_001"),
        )
        assertEquals(
            GearPieceTier.familyKey("bow_aa_t2_raid_001"),
            GearPieceTier.familyKey("bow_aa_t4_raid_001"),
        )
    }
}
