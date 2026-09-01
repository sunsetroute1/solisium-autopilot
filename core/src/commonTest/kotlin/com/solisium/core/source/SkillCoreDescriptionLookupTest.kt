package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkillCoreDescriptionLookupTest {
    @Test
    fun stripsSkillCorePrefixAndPerkEquipId() {
        assertEquals(
            "Enraged Primal Brothers' Thunder Strike",
            SkillCoreDescriptionLookup.skillNameFromCore(
                "Skill Core: Enraged Primal Brothers' Thunder Strike",
            ),
        )
        assertEquals("orb_aa_S1_000", SkillCoreDescriptionLookup.equipRowId("Perk_orb_aa_S1_000"))
        assertNull(SkillCoreDescriptionLookup.equipRowId("sword_aa_t1"))
    }

    @Test
    fun complexIdYieldsSkillCandidates() {
        val ids = SkillCoreDescriptionLookup.skillIdCandidates("SkillSet_WP_Item_Boss_T2_Upgrade_ORB_01")
        assertEquals(
            listOf(
                "WP_Item_Boss_T2_Upgrade_ORB_01",
                "Boss_T2_Upgrade_ORB_01",
                "WP_Item_Boss_T2_ORB_01",
                "Boss_T2_ORB_01",
            ),
            ids,
        )
        assertEquals(emptyList(), SkillCoreDescriptionLookup.skillIdCandidates("None"))
    }

    @Test
    fun prefersSkillDescAndSkipsGenericItemBlurb() {
        val table = MapLocres(
            "TLStringSkillDesc" to mapOf(
                "TEXT_SKILL_DESC_WP_Item_FieldBoss_T2_Upgrade_ORB_01" to
                    "Deals ^<c=@SkillTooltipChange>\$[hit.tooltip1]%</> Base Damage.",
            ),
            "TLItemLooks_Equip" to mapOf(
                "Perk_Item_Description" to "An item that contains a skill's ability.",
            ),
        )
        assertEquals(
            "Deals \$[hit.tooltip1]% Base Damage.",
            SkillCoreDescriptionLookup.description(
                table,
                "Skill Core: Thunder Strike",
                "SkillSet_WP_Item_FieldBoss_T2_Upgrade_ORB_01",
            ),
        )
        assertNull(SkillCoreDescriptionLookup.description(table, "Skill Core: Unknown", "None"))
    }

    @Test
    fun fallsBackToSkillNameIndex() {
        val table = object : LocresLookup {
            override fun get(namespace: String, key: String): String? = null
            override fun skillDescriptionByName(skillName: String): String? =
                if (skillName == "Purifying Cry") "Removes CC from nearby allies." else null
        }
        assertEquals(
            "Removes CC from nearby allies.",
            SkillCoreDescriptionLookup.description(table, "Skill Core: Purifying Cry", "None"),
        )
    }

    private class MapLocres(
        private vararg val namespaces: Pair<String, Map<String, String>>,
    ) : LocresLookup {
        override fun get(namespace: String, key: String): String? =
            namespaces.toMap()[namespace]?.get(key)
    }
}
