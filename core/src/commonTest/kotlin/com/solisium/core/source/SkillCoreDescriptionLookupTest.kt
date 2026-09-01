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
            "Deals —% Base Damage.",
            SkillCoreDescriptionLookup.description(
                table,
                "Skill Core: Thunder Strike",
                "SkillSet_WP_Item_FieldBoss_T2_Upgrade_ORB_01",
            ),
        )
        assertNull(SkillCoreDescriptionLookup.description(table, "Skill Core: Unknown", "None"))
    }

    @Test
    fun substitutesWarehouseTooltipNumbers() {
        val table = MapLocres(
            "TLSkillPcLooks_Item" to mapOf(
                "WP_Item_Nix_Crack_DA_01_RankDescription_ValueIndex0" to
                    "increases Critical Hit Chance by \$[WP_Item_Nix_Crack_DA_01_Critical_Buff.tooltip1] for \$[WP_Item_Nix_Crack_DA_01_Duration.tooltip1]s. " +
                    "increases Critical Damage by \$[WP_Item_Nix_Crack_DA_01_CriticalDealt_Buff.tooltip1]% for \$[WP_Item_Nix_Crack_DA_01_Duration.tooltip1]s.",
            ),
        )
        val tooltips = TooltipFieldLookup { rowId, field ->
            when {
                rowId.endsWith("Critical_Buff") && field.equals("tooltip1", true) -> 700.0
                rowId.endsWith("Duration") && field.equals("tooltip1", true) -> 3.0
                rowId.endsWith("CriticalDealt_Buff") && field.equals("tooltip1", true) -> 35.0
                else -> null
            }
        }
        assertEquals(
            "increases Critical Hit Chance by 700 for 3s. increases Critical Damage by 35% for 3s.",
            SkillCoreDescriptionLookup.description(
                table,
                "Skill Core: Ambush",
                "SkillSet_WP_Item_Nix_Crack_DA_01",
                tooltips,
            ),
        )
        assertEquals(
            "by —",
            SkillCoreDescriptionLookup.substitute("by \$[missing.tooltip1]", TooltipFieldLookup.Empty),
        )
        val numbers = TooltipFieldLookup { rowId, field ->
            when {
                rowId == "Buff" && field.equals("tooltip1", true) -> 35.0
                rowId == "Rate" && field.equals("tooltip1", true) -> 20.0
                rowId == "Base" && field.equals("tooltip1", true) -> 50.0
                else -> null
            }
        }
        assertEquals("140", SkillCoreDescriptionLookup.substitute("\$[Buff.tooltip1*4]", numbers))
        assertEquals("40", SkillCoreDescriptionLookup.substitute("\$[100*Rate.tooltip1/Base.tooltip1]", numbers))
        assertEquals(
            false,
            SkillCoreDescriptionLookup.substitute(
                "\$[WP_Item_Nix_Crack_DA_01_Critical_Buff.tooltip1] / \$[nope.tooltip1]%",
                numbers,
            ).contains("$["),
        )
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
