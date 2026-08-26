package com.solisium.core.query

import com.solisium.core.domain.BuildClassOption
import com.solisium.core.domain.BuildLayer
import com.solisium.core.domain.DesiredBuildPlan
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.LayerCoverage
import com.solisium.core.domain.ModeledPowerBreakdown
import com.solisium.core.domain.RankedGear
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.RoadmapStep
import com.solisium.core.domain.SkillCoverage
import com.solisium.core.domain.CommunitySnapshot
import com.solisium.core.domain.SlotAdvice
import com.solisium.core.source.EquipCategory

/**
 * Wraps extracted gear ranks with typed window CP/GS plus a Questlog-shaped
 * modeled estimate. Item weights come from the warehouse; skill/mastery/base
 * terms are community. This is not the live character-window aggregator.
 */
class DesiredBuildPlanner(private val query: CatalogQuery) {
    fun plan(
        snapshotId: String,
        goal: BuildGoal,
        characterId: String? = null,
        community: CommunitySnapshot? = null,
        desiredCombatPower: Long? = null,
        desiredGearScore: Long? = null,
        axes: List<StatAxis> = emptyList(),
        extraKeys: Set<String> = emptySet(),
        classOption: BuildClassOption? = null,
    ): DesiredBuildPlan {
        val advice = BuildAdvisor(query).advise(
            snapshotId = snapshotId,
            goal = goal,
            characterId = characterId,
            community = community,
            extraKeys = extraKeys,
            axes = axes,
            classOption = classOption,
        )
        val sheet = characterId?.let { query.resolveCharacter(it, snapshotId) }
        val currentCp = sheet?.sheet?.character?.combatPower
        val currentGs = sheet?.sheet?.character?.gearScore
        val cpGap = typedGap(currentCp, desiredCombatPower)
        val gsGap = typedGap(currentGs, desiredGearScore)
        val modeled = if (sheet != null) ModeledCombatPower(query).estimate(snapshotId, sheet) else null
        val modeledCpGap = typedGap(modeled?.potential, desiredCombatPower)
        val modeledGsGap = typedGap(modeled?.potentialGearScore, desiredGearScore)
        val coverage = skillCoverage(snapshotId, sheet, goal, classOption)
        val influences = layerCoverage(snapshotId, sheet)
        val roadmap = roadmap(
            slots = advice.slots,
            coverage = coverage,
            influences = influences,
            cpGap = cpGap,
            gsGap = gsGap,
            modeled = modeled,
            modeledCpGap = modeledCpGap,
            modeledGsGap = modeledGsGap,
            characterPresent = sheet != null,
            classOption = classOption,
        )
        return DesiredBuildPlan(
            advice = advice,
            currentCombatPower = currentCp,
            desiredCombatPower = desiredCombatPower,
            combatPowerGap = cpGap,
            currentGearScore = currentGs,
            desiredGearScore = desiredGearScore,
            gearScoreGap = gsGap,
            modeled = modeled,
            modeledCombatPowerGap = modeledCpGap,
            modeledGearScoreGap = modeledGsGap,
            axes = axes.map { it.label },
            extraKeys = extraKeys.sorted(),
            roadmap = roadmap,
            skillCoverage = coverage,
            influences = influences,
            selectedClass = classOption,
            characterClass = sheet?.weaponClass,
            limits = LIMITS,
        )
    }

    private fun roadmap(
        slots: List<SlotAdvice>,
        coverage: SkillCoverage,
        influences: List<LayerCoverage>,
        cpGap: Long?,
        gsGap: Long?,
        modeled: ModeledPowerBreakdown?,
        modeledCpGap: Long?,
        modeledGsGap: Long?,
        characterPresent: Boolean,
        classOption: BuildClassOption?,
    ): List<RoadmapStep> {
        val steps = mutableListOf<RoadmapStep>()
        if (classOption != null) {
            steps += RoadmapStep(
                kind = "class",
                title = "${classOption.name}: ${classOption.weaponsLabel}",
                detail = "Weapon ranks and catalog skill coverage use this pair. " +
                    "Source is ${classOption.source}; extracted game_class wins over the community table.",
            )
        }
        if (modeled != null) {
            steps += RoadmapStep(
                kind = "modeled-cp",
                title = "Modeled CP ${modeled.current} · potential ${modeled.potential}",
                detail = modeled.note,
            )
            steps += RoadmapStep(
                kind = "modeled-gs",
                title = "Modeled gear score ${modeled.gearScore} · potential ${modeled.potentialGearScore}",
                detail = "Questlog equipment subtotal (250 starting value + warehouse item weights). " +
                    "Not the typed character-window watermark; the warehouse has no GS table.",
            )
        }
        if (modeledCpGap != null) {
            steps += RoadmapStep(
                kind = "modeled-cp-gap",
                title = "Modeled potential-CP gap ${modeledCpGap}",
                detail = "Desired typed target minus modeled potential CP. Community skill/mastery " +
                    "and warehouse item potentials; not a promise the window will move by this amount.",
            )
        }
        if (modeledGsGap != null) {
            steps += RoadmapStep(
                kind = "modeled-gs-gap",
                title = "Modeled potential gear-score gap ${modeledGsGap}",
                detail = "Desired watermark minus modeled potential equipment subtotal.",
            )
        }
        if (cpGap != null) {
            steps += RoadmapStep(
                kind = "typed-cp",
                title = "Typed combat-power gap ${cpGap}",
                detail = "Subtracted from the character window value you typed. " +
                    "This is not a modeled delta and is not item-base-CP.",
            )
        }
        if (gsGap != null) {
            steps += RoadmapStep(
                kind = "typed-gs",
                title = "Typed gear-score gap ${gsGap}",
                detail = "The warehouse has no gear-score watermark table. Gap is typed subtraction only.",
            )
        }
        slots.filter { it.recommended.isNotEmpty() }
            .map { slot -> slot to itemPowerGap(slot.equipped, slot.recommended.first()) }
            .filter { (slot, powerGap) ->
                (slot.gap != null && slot.gap!! > 0L) || (powerGap != null && powerGap > 0L)
            }
            .sortedByDescending { (_, powerGap) -> powerGap ?: 0L }
            .forEach { (slot, powerGap) ->
                val top = slot.recommended.first()
                val equipped = slot.equipped
                steps += RoadmapStep(
                    kind = "slot",
                    title = "${slot.slot}: ${equipped?.name ?: "empty"} → ${top.name}",
                    detail = listOfNotNull(
                        slot.gap?.let { "Extracted main_base gap $it. Not DPS." },
                        powerGap?.let {
                            "Modeled item CP ${signed(it)} (warehouse weights + potential, derived row map)."
                        },
                    ).joinToString(" "),
                    statGap = slot.gap,
                    itemPowerGap = powerGap,
                )
            }
        if (coverage.missingNames.isNotEmpty()) {
            val preview = coverage.missingNames.take(8).joinToString(", ")
            steps += RoadmapStep(
                kind = "skills",
                title = "${coverage.missingNames.size} catalog skill(s) for this pathway are not in your JSON loadout",
                detail = "$preview. ${coverage.note}",
            )
        } else if (coverage.slotted == 0) {
            steps += RoadmapStep(
                kind = "skills",
                title = "No warehouse-keyed skills in the character JSON",
                detail = coverage.note,
            )
        }
        val emptyLayers = influences.filter { it.slotted == 0 }
        if (characterPresent && emptyLayers.isNotEmpty()) {
            steps += RoadmapStep(
                kind = "layers",
                title = "${emptyLayers.size} skills-screen layers have no typed rows",
                detail = emptyLayers.joinToString("; ") { it.label } +
                    ". Presence only; none of these fold into the window CP aggregator.",
            )
        }
        val fresh = influences.filter { it.newThisPatch }
        if (fresh.isNotEmpty()) {
            steps += RoadmapStep(
                kind = "new-influences",
                title = "${fresh.size} new skill-screen family(ies) on this warehouse",
                detail = fresh.joinToString("; ") { it.label } +
                    ". Typed presence only; Solisium does not invent a CP delta for them.",
            )
        }
        val mappedPower = slots.mapNotNull { slot ->
            val top = slot.recommended.firstOrNull() ?: return@mapNotNull null
            itemPowerGap(slot.equipped, top)
        }.filter { it > 0L }.sum()
        if (cpGap != null && mappedPower > 0L) {
            steps += RoadmapStep(
                kind = "item-cp-cap",
                title = "Mapped item CP swaps add at most $mappedPower",
                detail = when {
                    modeledCpGap != null && mappedPower < modeledCpGap ->
                        "That is less than the modeled potential-CP gap. Skills, mastery, and unmapped items still sit outside warehouse item weights."
                    mappedPower < cpGap ->
                        "That is less than the typed CP gap. The remainder has no extracted aggregator."
                    else ->
                        "That is a modeled weight sum, not a promise the window CP will move by this amount."
                },
                itemPowerGap = mappedPower,
            )
        }
        return steps
    }

    private fun skillCoverage(
        snapshotId: String,
        sheet: ResolvedCharacterSheet?,
        goal: BuildGoal,
        classOption: BuildClassOption?,
    ): SkillCoverage {
        val slottedIds = sheet?.sheet?.skills?.mapNotNull { it.sourceRowId }?.toSet().orEmpty()
        val weaponsById = query.weapons(snapshotId).associateBy { it.sourceRowId }
        val sheetTokens = sheet?.lines.orEmpty()
            .filter { it.kind == "weapon" }
            .mapNotNull { line ->
                skillToken(line.hit?.detail) ?: skillToken(weaponsById[line.sourceRowId]?.weaponType)
            }
            .toSet()
        val wanted = (classOption?.skillCategories() ?: goal.skillCategories) + sheetTokens
        val relevant = query.skills(snapshotId).filter { skill ->
            val token = skillToken(skill.skillType)
            token != null && token in wanted
        }
        val missing = relevant.mapNotNull { skill ->
            if (skill.sourceRowId in slottedIds) return@mapNotNull null
            DisplayName.of(skill.name, skill.sourceRowId)
        }
        return SkillCoverage(
            catalogRelevant = relevant.size,
            slotted = slottedIds.size,
            missingNames = missing.take(24),
            note = SKILL_NOTE,
        )
    }

    private fun layerCoverage(
        snapshotId: String,
        sheet: ResolvedCharacterSheet?,
    ): List<LayerCoverage> {
        val catalogByFamily = query.skills(snapshotId).groupingBy { it.family.orEmpty() }.eachCount()
        val lines = sheet?.lines.orEmpty()
        val masteryLevels = LayerCoverage(
            layer = "weapon_mastery_level",
            label = "Weapon mastery levels",
            slotted = sheet?.sheet?.weaponMastery?.count { it.weapon.isNotBlank() } ?: 0,
            catalogNamed = 0,
            resolved = 0,
            note = "Typed numbers from the skills screen (167 / 151). Not WM_ catalog nodes and not a CP formula.",
        )
        val layers = BuildLayer.entries.map { layer ->
            val slotted = when (layer) {
                BuildLayer.WeaponSkill -> sheet?.sheet?.skills.orEmpty().size
                else -> sheet?.sheet?.buildLayers.orEmpty().count { it.layer.equals(layer.id, ignoreCase = true) }
            }
            val resolved = when (layer) {
                BuildLayer.WeaponSkill -> lines.count { it.kind == "skill" && it.hit != null }
                else -> lines.count { it.kind == layer.id && it.hit != null }
            }
            LayerCoverage(
                layer = layer.id,
                label = layer.label,
                slotted = slotted,
                catalogNamed = layer.catalogFamily?.let { catalogByFamily[it.id] } ?: 0,
                resolved = resolved,
                note = layer.blurb,
            )
        }
        return listOf(masteryLevels) + layers + query.discoveredInfluences(snapshotId).map { inf ->
            LayerCoverage(
                layer = inf.id,
                label = inf.label,
                slotted = sheet?.sheet?.buildLayers.orEmpty().count {
                    it.layer.equals(inf.id, ignoreCase = true) || it.layer.equals(inf.prefix, ignoreCase = true)
                },
                catalogNamed = inf.namedCount,
                resolved = lines.count { it.kind == inf.id && it.hit != null },
                note = inf.note,
                newThisPatch = inf.newThisPatch,
            )
        }
    }

    companion object {
        val LIMITS = listOf(
            "Live combat power is the typed character-window value. Solisium does not compute the live window aggregator.",
            "Modeled CP copies Questlog's equipment + skills + mastery layout. Mapped items use warehouse TLItemCombatPower; A/AA families stay unresolved.",
            "ItemPotentialCombatPower is added only on the potential total. It is an item-component weight, not proof the window will move by that amount.",
            "Skill ×2, mastery ×3 with 130/260/390/520 bonuses, and the 250 equipment starting value are community Questlog constants. They are not in TLItemCombatPower.",
            "Modeled gear score is that equipment subtotal. The warehouse has no gear-score watermark table; typed GS stays the window value.",
            "Skill-screen families on game_skill are derived from row-id prefixes (WP_, WM_, Gem_, WP_Item_, WP_Polymorph).",
            "Weapon mastery levels are typed. WM_ rows are mastery-tree nodes, not those numbers.",
            "TLWeaponSpecializationStat, TLItemMaterialStat, and TLSkillOptionalDataForPc are not in the current warehouse collect.",
            "NPC Skill_ImmortalGuardian* rows are not the player Guardian slot.",
            "Traits, runes, resonance, unique traits, perks, skill cores, specializations, Guardian, Transcendence, and Material Effect are not folded into modeled CP unless they have a warehouse item-power row.",
            "Ranks are extracted main_base raw sums. Not DPS.",
            "Class types are weapon-pair titles. Extracted TLPcClass rows win; otherwise the community table. Pairs with no published title stay unnamed.",
        )

        const val SKILL_NOTE =
            "Presence only for catalog matching. Typed skill levels feed the community skill-power term (×2). " +
                "That is not the live window aggregator."

        private fun typedGap(current: Long?, desired: Long?): Long? {
            if (current == null || desired == null) return null
            return (desired - current).coerceAtLeast(0L)
        }

        private fun itemPowerGap(equipped: RankedGear?, top: RankedGear): Long? {
            val next = top.potentialPower ?: top.itemPower ?: return null
            val yours = equipped?.itemPower ?: 0L
            return next - yours
        }

        private fun signed(value: Long): String = if (value >= 0) "+$value" else "$value"

        private fun skillToken(raw: String?): String? {
            val token = EquipCategory.token(raw) ?: return null
            return token.removePrefix("k").lowercase()
        }
    }
}
