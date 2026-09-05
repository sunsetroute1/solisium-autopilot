package com.solisium.core.query

import com.solisium.core.domain.CommunityTraitLine
import com.solisium.core.domain.GameItemPower
import com.solisium.core.domain.GearInspectorState
import com.solisium.core.domain.GearTraitSlot
import com.solisium.core.domain.ItemTraitCandidate
import com.solisium.core.domain.ItemTraitProfile
import com.solisium.core.domain.QuestlogItemOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GearInspectorModelTest {
    private fun sampleProfile(): ItemTraitProfile = ItemTraitProfile(
        slotCount = 2,
        candidates = listOf(
            ItemTraitCandidate(
                traitId = "kCostConsumptionModifier",
                label = "Mana Cost Efficiency",
                statKey = "cost_consumption_modifier",
                tierValues = listOf("200", "400"),
            ),
            ItemTraitCandidate(
                traitId = "kCostMax",
                label = "Max Mana",
                statKey = "cost_max",
                tierValues = listOf("50", "100"),
            ),
        ),
        resonanceCandidates = listOf(
            ItemTraitCandidate(
                traitId = "cost_max",
                label = "Max Mana",
                statKey = "cost_max",
                tierValues = listOf("260", "390"),
            ),
        ),
        uniqueCandidates = listOf(
            ItemTraitCandidate(
                traitId = "Unique_ArcaneSurge",
                label = "Arcane Surge",
                statKey = "arcane_surge",
                tierValues = listOf("1"),
            ),
        ),
    )

    @Test
    fun seedStartsEmptySlots() {
        val questlog = QuestlogItemOverlay(
            description = null,
            requiredLevel = 50L,
            sellPrice = null,
            tradeCategory = null,
            properties = emptyList(),
            statLines = emptyList(),
            traitLines = listOf(
                CommunityTraitLine("Mana Cost Efficiency", "200 → 400 → 600 → 800", "cost_consumption_modifier"),
                CommunityTraitLine("Max Mana", "50 → 100 → 150 → 200", "cost_max"),
            ),
            perkSummaries = listOf("Arcane Surge — bonus"),
        )
        val state = GearInspectorModel.seed(sampleProfile(), questlog, curves = emptyList(), combatPower = null)
        assertEquals("50", state.itemLevel)
        assertEquals(2, state.slots.size)
        assertEquals(listOf(GearTraitSlot(), GearTraitSlot()), state.slots)
    }

    @Test
    fun tierDisplayValuesShowAbsoluteScaledTotals() {
        assertEquals(
            listOf("200", "400", "600", "800"),
            GearInspectorModel.tierDisplayValues(listOf("200", "400", "600", "800")),
        )
        assertEquals(
            listOf("40", "80", "120", "160"),
            GearInspectorModel.tierDisplayValues(
                listOf("400", "800", "1200", "1600"),
                "melee_critical_defense",
            ),
        )
        assertEquals(
            listOf("3%", "6%", "9%", "12%"),
            GearInspectorModel.tierDisplayValues(
                listOf("300", "600", "900", "1200"),
                "cost_consumption_modifier",
            ),
        )
        assertEquals(
            listOf("15", "30", "45", "60"),
            GearInspectorModel.tierDisplayValues(
                listOf("15000", "30000", "45000", "60000"),
                "cost_regen",
            ),
        )
        assertEquals(
            listOf("64", "96", "115", "128"),
            GearInspectorModel.tierDisplayValues(
                listOf("640", "960", "1150", "1280"),
                "magic_critical_defense",
            ),
        )
    }

    @Test
    fun potentialUnlocksWhenEverySlotIsMaxTier() {
        val profile = sampleProfile()
        val state = GearInspectorState(
            slots = listOf(
                GearTraitSlot("kCostConsumptionModifier", 2),
                GearTraitSlot("kCostMax", 1),
            ),
        )
        assertEquals(
            false,
            GearInspectorModel.isPotentialUnlocked(state, profile),
        )
        assertEquals(
            true,
            GearInspectorModel.isPotentialUnlocked(
                state.copy(slots = listOf(GearTraitSlot("kCostConsumptionModifier", 2), GearTraitSlot("kCostMax", 2))),
                profile,
            ),
        )
    }

    @Test
    fun mergeSlotsHealsSelectedTraitWithZeroTier() {
        val profile = sampleProfile()
        val healed = GearInspectorModel.mergeSlots(
            listOf(
                GearTraitSlot("kCostConsumptionModifier", 4),
                GearTraitSlot("kCostMax", 0),
            ),
            profile,
        )
        assertEquals(4, healed[0].tier)
        assertEquals(1, healed[1].tier)
        assertEquals("kCostMax", healed[1].traitId)
    }

    @Test
    fun resonanceTierIsIndependentOfTraitSlotTiers() {
        val profile = sampleProfile()
        val state = GearInspectorState(
            slots = listOf(
                GearTraitSlot("kCostConsumptionModifier", 2),
                GearTraitSlot("kCostMax", 2),
            ),
            resonanceTraitId = "cost_max",
            resonanceTier = 1,
        )
        assertEquals(1, GearInspectorModel.activeResonanceTier(state, profile))
        assertEquals("Max Mana 260", GearInspectorModel.traitResonanceLabel(state, profile, profile.candidates.associateBy { it.traitId }))
    }

    @Test
    fun resonanceRequiresAllTraitSlotsAtMaxTier() {
        val profile = sampleProfile()
        val partial = GearInspectorState(
            slots = listOf(
                GearTraitSlot("kCostConsumptionModifier", 2),
                GearTraitSlot("kCostMax", 1),
            ),
            resonanceTraitId = "cost_max",
            resonanceTier = 2,
        )
        val unlocked = GearInspectorState(
            slots = listOf(
                GearTraitSlot("kCostConsumptionModifier", 2),
                GearTraitSlot("kCostMax", 2),
            ),
            resonanceTraitId = "cost_max",
            resonanceTier = 2,
        )
        assertNull(GearInspectorModel.traitResonanceLabel(partial, profile, profile.candidates.associateBy { it.traitId }))
        assertEquals(
            "Max Mana 390",
            GearInspectorModel.traitResonanceLabel(unlocked, profile, profile.candidates.associateBy { it.traitId }),
        )
    }

    @Test
    fun findResonanceCandidateAcceptsWarehouseTraitIds() {
        val candidate = ItemTraitCandidate(
            traitId = "magic_critical_defense",
            label = "Magic Endurance",
            statKey = "magic_critical_defense",
            tierValues = listOf("640", "960"),
        )
        assertEquals(
            candidate,
            GearInspectorModel.findResonanceCandidate(listOf(candidate), "kMagicCriticalDefense"),
        )
    }

    @Test
    fun summarizeShowsPotentialSkillWhenUnlocked() {
        val profile = sampleProfile()
        val state = GearInspectorState(
            itemLevel = "50",
            slots = listOf(
                GearTraitSlot("kCostConsumptionModifier", 2),
                GearTraitSlot("kCostMax", 2),
            ),
            resonanceTraitId = "cost_max",
            resonanceTier = 2,
        )
        val summary = GearInspectorModel.summarize(
            state = state,
            profile = profile,
            questlog = null,
            combatPower = null,
            gearType = "Helmet",
            itemName = "Veiled Concord Mask",
            itemRowId = "head_leather_aa_t3_normal_006",
        )
        assertEquals("Arcane Surge", summary.potentialSkill)
        assertEquals("Max Mana 390", summary.traitResonance)
    }

    @Test
    fun itemPowerAddsPotentialWhenUnlocked() {
        val payload = """{"BaseCombatPower":64,"ItemPotentialCombatPower":30,"ItemEnchantCombatPowerList":[{"CombatPower":0},{"CombatPower":8}]}"""
        val power = GameItemPower(
            snapshotId = "s",
            itemSourceTable = "t",
            itemSourceRowId = "id",
            powerSourceRowId = "weapon_aa_t1",
            evidence = "test",
            confidence = "derived",
            basePower = 64L,
            potentialPower = 30L,
            payload = payload,
        )
        val profile = ItemTraitProfile(
            slotCount = 1,
            candidates = listOf(
                ItemTraitCandidate(
                    traitId = "kAllAccuracy",
                    label = "All accuracy",
                    statKey = "all_accuracy",
                    tierValues = listOf("200", "400"),
                ),
            ),
        )
        val summary = GearInspectorModel.summarize(
            state = GearInspectorState(
                itemLevel = "1",
                slots = listOf(GearTraitSlot("kAllAccuracy", 2)),
            ),
            profile = profile,
            questlog = null,
            combatPower = power,
            gearType = "Bow",
            itemName = "Test Bow",
            itemRowId = "weapon_bow_aa_t3_normal_001",
        )
        assertEquals(72L, summary.itemPowerCurrent)
        assertEquals(102L, summary.itemPowerPotential)
    }
}
