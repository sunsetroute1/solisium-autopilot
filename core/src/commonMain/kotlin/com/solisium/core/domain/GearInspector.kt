package com.solisium.core.domain

/** One trait that can roll on a specific item row. */
data class ItemTraitCandidate(
    val traitId: String,
    /** Base stat name, e.g. `Max Mana`. */
    val label: String,
    /** Questlog / warehouse stat key, e.g. `cost_max`. */
    val statKey: String = "",
    /** Tier upgrade values (T1–T4) as shown in game / Questlog. */
    val tierValues: List<String>,
    val baseSeed: Int = 3,
) {
    fun rollLabel(tier: Int = 1): String =
        com.solisium.core.source.TraitDisplayFormat.rollLabel(label, statKey, tierValues, tier)
}

/** Warehouse + Questlog trait layout for one equippable item. */
data class ItemTraitProfile(
    val slotCount: Int,
    val candidates: List<ItemTraitCandidate>,
    val uniqueCandidates: List<ItemTraitCandidate> = emptyList(),
    /** Trait pool eligible for trait resonance on this item. */
    val resonanceCandidates: List<ItemTraitCandidate> = emptyList(),
    val source: String = SOURCE_WAREHOUSE,
) {
    companion object {
        const val SOURCE_WAREHOUSE = "warehouse"
        const val SOURCE_QUESTLOG = "questlog"
    }
}

/** User-selected trait on one gear slot. */
data class GearTraitSlot(
    val traitId: String = "",
    val tier: Int = 0,
)

/** In-game roll entry for one catalog gear row. */
data class GearInspectorState(
    val itemLevel: String = "",
    val slots: List<GearTraitSlot> = emptyList(),
    /** Questlog stat key for the chosen resonance effect, e.g. `magic_critical_defense`. */
    val resonanceTraitId: String = "",
    /** Resonance tier (T1–T4), independent of individual trait slot tiers. */
    val resonanceTier: Int = 0,
    /** Selected unique / potential trait id when the item supports one. */
    val potentialTraitId: String = "",
)

data class GearTraitView(
    val slotIndex: Int,
    val label: String,
    val tierValues: List<String>,
    val selectedTier: Int,
    val traitId: String,
    val statKey: String = "",
)

data class GearRollSummary(
    val gearType: String,
    val itemName: String,
    val itemLevel: String,
    val traits: List<GearTraitView>,
    val traitResonance: String?,
    val potentialSkill: String,
    val potentialUnlocked: Boolean,
    val itemPowerCurrent: Long?,
    val itemPowerPotential: Long?,
)
