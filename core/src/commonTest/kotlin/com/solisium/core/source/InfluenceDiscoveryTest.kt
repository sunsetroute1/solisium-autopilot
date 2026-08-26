package com.solisium.core.source

import com.solisium.core.domain.GameSkill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfluenceDiscoveryTest {
    @Test
    fun unknownWeaponPrefixBecomesAnInfluence() {
        val found = InfluenceDiscovery.discover(
            listOf(
                skill("WP_FL_Tune", "Flute Tune"),
                skill("WP_FL_Verse", "Verse"),
                skill("WP_SW2_Slam", "Gauntlet Slam"),
                skill("Skill_ImmortalGuardian_01", "Immortal Guardian"),
            ),
        )
        assertEquals(listOf("prefix:WP_FL"), found.map { it.id })
        assertEquals(2, found.single().namedCount)
        assertTrue(found.single().newThisPatch)
    }

    @Test
    fun previousSnapshotClearsNewThisPatch() {
        val previous = listOf(skill("WP_FL_Tune", "Flute Tune"))
        val current = listOf(skill("WP_FL_Tune", "Flute Tune"), skill("WP_FL_Verse", "Verse"))
        val found = InfluenceDiscovery.discover(current, previous)
        assertEquals(1, found.size)
        assertEquals(false, found.single().newThisPatch)
    }

    private fun skill(id: String, name: String) = GameSkill(
        snapshotId = "snap",
        sourceTable = "TLSkill",
        sourceRowId = id,
        name = name,
        skillType = "ESkillCategory::kSkill",
    )
}
