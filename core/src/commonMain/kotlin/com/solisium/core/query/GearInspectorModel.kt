package com.solisium.core.query

import com.solisium.core.domain.GameItemCurve
import com.solisium.core.domain.GameItemPower
import com.solisium.core.domain.GearInspectorState
import com.solisium.core.domain.GearRollSummary
import com.solisium.core.domain.GearTraitSlot
import com.solisium.core.domain.GearTraitView
import com.solisium.core.domain.ItemTraitCandidate
import com.solisium.core.domain.ItemTraitProfile
import com.solisium.core.domain.QuestlogItemOverlay
import com.solisium.core.json.JsonParseException
import com.solisium.core.json.JsonParser
import com.solisium.core.source.CombatPowerLookup
import com.solisium.core.source.ItemTraitSlots
import com.solisium.core.source.TraitDisplayFormat
import com.solisium.core.source.TraitStatKeys

object GearInspectorModel {
    fun rowKey(sourceTable: String, sourceRowId: String): String = "$sourceTable|$sourceRowId"

    fun seed(
        profile: ItemTraitProfile?,
        questlog: QuestlogItemOverlay?,
        curves: List<GameItemCurve>,
        @Suppress("UNUSED_PARAMETER") combatPower: GameItemPower?,
    ): GearInspectorState {
        val slotCount = profile?.slotCount ?: 0
        return GearInspectorState(
            itemLevel = defaultItemLevel(questlog, curves),
            slots = List(slotCount) { GearTraitSlot() },
        )
    }

    fun mergeSlots(saved: List<GearTraitSlot>, profile: ItemTraitProfile?): List<GearTraitSlot> {
        if (profile == null) return saved
        val validIds = profile.candidates.map { it.traitId }.toSet()
        return List(profile.slotCount) { index ->
            val existing = saved.getOrNull(index)
            if (existing != null && (existing.traitId.isBlank() || existing.traitId in validIds)) {
                val tier = when {
                    // A selected trait with tier 0 is invalid (usually from an old
                    // "re-click clears tier" UI bug) and silently drops resonance.
                    existing.traitId.isNotBlank() && existing.tier <= 0 -> 1
                    else -> existing.tier.coerceIn(0, 4)
                }
                existing.copy(tier = tier)
            } else {
                GearTraitSlot()
            }
        }
    }

    fun defaultItemLevel(questlog: QuestlogItemOverlay?, curves: List<GameItemCurve>): String {
        questlog?.requiredLevel?.takeIf { it > 0L }?.let { return it.toString() }
        val itemMax = curves.filter { it.curveKind == "item_level" }.mapNotNull { it.maxLevel }.maxOrNull()
        if (itemMax != null && itemMax > 0L) return itemMax.toString()
        val enchantMax = curves.filter { it.curveKind == "enchant" }.mapNotNull { it.maxLevel }.maxOrNull()
        if (enchantMax != null && enchantMax > 0L) return enchantMax.toString()
        return ""
    }

    fun gearTypeLabel(meta: String?, category: String?, catalogKind: String): String {
        val token = com.solisium.core.domain.DisplayName.prettyEnum(meta)
            ?: com.solisium.core.domain.DisplayName.prettyEnum(category)
            ?: catalogKind.removeSuffix("s")
        return when (token.lowercase()) {
            "head" -> "Helmet"
            "hands" -> "Gloves"
            "legs" -> "Legs"
            "feet" -> "Boots"
            "ear", "earring" -> "Earring"
            "bow" -> "Bow"
            "sword2h" -> "Two-Handed Sword"
            else -> token.replaceFirstChar { ch -> ch.uppercase() }
        }
    }

    fun summarize(
        state: GearInspectorState,
        profile: ItemTraitProfile?,
        questlog: QuestlogItemOverlay?,
        combatPower: GameItemPower?,
        gearType: String,
        itemName: String,
        itemRowId: String,
    ): GearRollSummary {
        val candidateMap = profile?.candidates?.associateBy { it.traitId }.orEmpty()
        val traits = state.slots.mapIndexedNotNull { index, slot ->
            if (slot.traitId.isBlank()) return@mapIndexedNotNull null
            val candidate = candidateMap[slot.traitId] ?: return@mapIndexedNotNull null
            val tier = slot.tier.coerceAtLeast(1)
            GearTraitView(
                slotIndex = index,
                label = candidate.rollLabel(tier),
                tierValues = candidate.tierValues,
                selectedTier = slot.tier,
                traitId = slot.traitId,
                statKey = candidate.statKey,
            )
        }
        val resonanceTier = activeResonanceTier(state, profile)
        val maxTier = maxTierFor(profile, candidateMap)
        val potentialUnlocked = isPotentialUnlocked(state, profile, candidateMap, maxTier)
        val (current, potential) = itemPowerTotals(
            power = combatPower,
            state = state,
            profile = profile,
            itemRowId = itemRowId,
            potentialUnlocked = potentialUnlocked,
            maxTier = maxTier,
        )
        return GearRollSummary(
            gearType = gearType,
            itemName = itemName,
            itemLevel = state.itemLevel,
            traits = traits,
            traitResonance = traitResonanceLabel(state, profile, candidateMap, resonanceTier),
            potentialSkill = potentialSkillLabel(state, profile, candidateMap, potentialUnlocked),
            potentialUnlocked = potentialUnlocked,
            itemPowerCurrent = current,
            itemPowerPotential = potential,
        )
    }

    fun slotCount(itemRowId: String, profile: ItemTraitProfile?): Int =
        profile?.slotCount ?: ItemTraitSlots.countFor(itemRowId)

    /**
     * Absolute display values for the T1–T4 selector, scaled like the in-game tooltip
     * (e.g. Melee Endurance `40` / `80` / `120` / `160`, Mana Cost Efficiency `3%` / `6%` / `9%` / `12%`).
     */
    fun tierDisplayValues(cumulative: List<String>, statKey: String = ""): List<String> {
        if (cumulative.isEmpty()) return emptyList()
        val rawNumeric = cumulative.map { raw ->
            raw.filter { ch -> ch.isDigit() || ch == '-' }.toLongOrNull()
        }
        if (rawNumeric.all { it != null }) {
            val percent = TraitDisplayFormat.isPercentStat(statKey)
            return rawNumeric.map { raw ->
                val body = formatTierAmount(TraitDisplayFormat.scale(statKey, raw!!))
                if (percent) "$body%" else body
            }
        }
        return cumulative
    }

    /** @deprecated use [tierDisplayValues] — kept for callers expecting the old name. */
    fun tierUpgradeCosts(cumulative: List<String>, statKey: String = ""): List<String> =
        tierDisplayValues(cumulative, statKey)

    private fun formatTierAmount(value: Double): String {
        val truncated = kotlin.math.truncate((value + 1e-10 * kotlin.math.sign(value)) * 100.0) / 100.0
        return if (truncated % 1.0 == 0.0) {
            truncated.toLong().toString()
        } else {
            truncated.toString().trimEnd('0').trimEnd('.')
        }
    }

    private fun maxTierFor(
        profile: ItemTraitProfile?,
        candidateMap: Map<String, ItemTraitCandidate>,
    ): Int {
        val fromValues = profile?.candidates?.mapNotNull { it.tierValues.size.takeIf { n -> n > 0 } }?.maxOrNull() ?: 4
        return fromValues.coerceIn(1, 4)
    }

    fun isPotentialUnlocked(
        state: GearInspectorState,
        profile: ItemTraitProfile?,
        candidateMap: Map<String, ItemTraitCandidate> = profile?.candidates?.associateBy { it.traitId }.orEmpty(),
        maxTier: Int = maxTierFor(profile, candidateMap),
    ): Boolean {
        if (profile == null) return false
        val active = state.slots.filter { it.traitId.isNotBlank() && it.tier > 0 }
        if (active.size < profile.slotCount) return false
        return active.all { it.tier >= maxTier }
    }

    fun isResonanceUnlocked(
        state: GearInspectorState,
        profile: ItemTraitProfile?,
        candidateMap: Map<String, ItemTraitCandidate> = profile?.candidates?.associateBy { it.traitId }.orEmpty(),
        maxTier: Int = maxTierFor(profile, candidateMap),
    ): Boolean {
        if (profile == null) return false
        val active = state.slots.filter { it.traitId.isNotBlank() && it.tier > 0 }
        if (active.size < profile.slotCount) return false
        return active.all { it.tier >= maxTier }
    }

    fun activeResonanceTier(
        state: GearInspectorState,
        profile: ItemTraitProfile?,
        candidateMap: Map<String, ItemTraitCandidate> = profile?.candidates?.associateBy { it.traitId }.orEmpty(),
        maxTier: Int = maxTierFor(profile, candidateMap),
    ): Int {
        if (!isResonanceUnlocked(state, profile, candidateMap, maxTier)) return 0
        return state.resonanceTier.coerceIn(1, maxTier)
    }

    fun resonanceSelectionKey(candidate: ItemTraitCandidate): String =
        candidate.statKey.ifBlank { candidate.traitId }

    fun normalizeResonanceKey(id: String): String =
        if (id.startsWith("k")) TraitStatKeys.toQuestlogKey(id, emptyList()) else id

    fun findResonanceCandidate(
        candidates: List<ItemTraitCandidate>,
        storedId: String,
    ): ItemTraitCandidate? {
        if (storedId.isBlank()) return null
        val normalized = normalizeResonanceKey(storedId)
        return candidates.firstOrNull { candidate ->
            candidate.statKey == storedId ||
                candidate.traitId == storedId ||
                candidate.statKey == normalized ||
                normalizeResonanceKey(candidate.traitId) == normalized
        }
    }

    fun matchesResonanceCandidate(candidate: ItemTraitCandidate, storedId: String): Boolean =
        findResonanceCandidate(listOf(candidate), storedId) != null

    fun normalizeResonanceState(state: GearInspectorState, profile: ItemTraitProfile?): GearInspectorState {
        val profile = profile ?: return state
        val candidates = profile.resonanceCandidates
        if (candidates.isEmpty()) return state
        val candidateMap = profile.candidates.associateBy { it.traitId }
        val maxTier = maxTierFor(profile, candidateMap)
        val picked = findResonanceCandidate(candidates, state.resonanceTraitId)
        val nextId = resonanceSelectionKey(picked ?: candidates.first())
        val nextTier = when {
            !isResonanceUnlocked(state, profile, candidateMap, maxTier) -> state.resonanceTier.coerceIn(0, maxTier)
            state.resonanceTier <= 0 -> maxTier
            else -> state.resonanceTier.coerceIn(1, maxTier)
        }
        return state.copy(resonanceTraitId = nextId, resonanceTier = nextTier)
    }

    fun traitResonanceLabel(
        state: GearInspectorState,
        profile: ItemTraitProfile?,
        candidateMap: Map<String, ItemTraitCandidate>,
        tier: Int? = activeResonanceTier(state, profile, candidateMap),
    ): String? {
        if (tier == null || tier <= 0) return null
        val candidates = profile?.resonanceCandidates.orEmpty()
        if (candidates.isEmpty()) return null
        val candidate = findResonanceCandidate(candidates, state.resonanceTraitId) ?: return null
        return candidate.rollLabel(tier)
    }

    /** @deprecated use [isResonanceUnlocked] */
    fun traitResonanceLabel(
        state: GearInspectorState,
        candidateMap: Map<String, ItemTraitCandidate>,
    ): String? = traitResonanceLabel(state, null, candidateMap, activeResonanceTier(state, null, candidateMap))

    fun candidateChipLabel(candidate: ItemTraitCandidate, previewTier: Int = 1): String =
        candidate.rollLabel(if (previewTier > 0) previewTier else 1)

    fun potentialSkillLabel(
        state: GearInspectorState,
        profile: ItemTraitProfile?,
        candidateMap: Map<String, ItemTraitCandidate>,
        unlocked: Boolean,
    ): String {
        if (!unlocked) return "No"
        val uniqueMap = profile?.uniqueCandidates?.associateBy { it.traitId }.orEmpty()
        if (uniqueMap.isEmpty()) return "No"
        val picked = state.potentialTraitId.takeIf { it.isNotBlank() && it in uniqueMap }
            ?: uniqueMap.keys.firstOrNull()
        return picked?.let { uniqueMap[it]?.label } ?: "No"
    }

    private fun itemPowerTotals(
        power: GameItemPower?,
        state: GearInspectorState,
        profile: ItemTraitProfile?,
        itemRowId: String,
        potentialUnlocked: Boolean,
        maxTier: Int,
    ): Pair<Long?, Long?> {
        if (power == null) return null to null
        val itemLevel = state.itemLevel.filter { it.isDigit() }.toLongOrNull()
        val json = parsePayload(power.payload)
        if (json == null) {
            val base = power.basePower
            val withPotential = if (potentialUnlocked) base + (power.potentialPower ?: 0L) else base
            return base to withPotential
        }
        val enchantIndex = CombatPowerLookup.enchantIndex(json, itemLevel)
        val traitIndex = (state.slots.filter { it.tier > 0 }.maxOfOrNull { it.tier } ?: 1) - 1
        val resonanceIndex = if (isResonanceUnlocked(state, profile)) {
            (activeResonanceTier(state, profile) - 1)
        } else {
            0
        }.coerceAtLeast(0)
        val uniqueIndex = if (potentialUnlocked && profile?.uniqueCandidates?.isNotEmpty() == true) 0 else 0
        val current = CombatPowerLookup.components(
            json = json,
            enchantIndex = enchantIndex,
            traitIndex = traitIndex.coerceAtLeast(0),
            uniqueIndex = uniqueIndex,
            resonanceIndex = resonanceIndex,
            includePotential = false,
        ).total
        val withPotential = CombatPowerLookup.components(
            json = json,
            enchantIndex = enchantIndex,
            traitIndex = traitIndex.coerceAtLeast(0),
            uniqueIndex = uniqueIndex,
            resonanceIndex = resonanceIndex,
            includePotential = potentialUnlocked,
        ).total
        return current to withPotential
    }

    private fun parsePayload(payload: String?): com.solisium.core.json.JsonValue? {
        if (payload.isNullOrBlank()) return null
        return try {
            JsonParser.parse(payload)
        } catch (_: JsonParseException) {
            null
        }
    }
}
