package com.solisium.core.query

import com.solisium.core.domain.BuildLayer
import com.solisium.core.domain.DesiredBuildPlan
import com.solisium.core.domain.LiveProgressionSnapshot
import com.solisium.core.domain.ProgressionCadence
import com.solisium.core.domain.ProgressionCharacterSnapshot
import com.solisium.core.domain.ProgressionDifficulty
import com.solisium.core.domain.ProgressionEase
import com.solisium.core.domain.ProgressionPlan
import com.solisium.core.domain.ProgressionRecommendation
import com.solisium.core.domain.ProgressionTaskTemplate
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.RoadmapStep

/**
 * Ranks what to do next from manual completion flags, character sheet, and optional
 * build plan. Does not read live contract/codex state from the game client.
 */
class ProgressionAnalyzer {
    fun analyze(
        sheet: ResolvedCharacterSheet?,
        buildPlan: DesiredBuildPlan?,
        buildGoal: BuildGoal,
        completedTaskIds: Set<String>,
        live: LiveProgressionSnapshot? = null,
    ): ProgressionPlan {
        val character = characterSnapshot(sheet, buildPlan, buildGoal)
        val level = character.level ?: 1L
        val cp = character.combatPower
        val mergedCompleted = ProgressionSync.mergedCompleted(completedTaskIds, live)
        val tasks = mutableListOf<ProgressionRecommendation>()
        ProgressionCatalog.templates
            .filter { level >= it.minLevel }
            .forEach { template ->
                tasks += recommendCatalog(template, mergedCompleted, level, cp, sheet, buildPlan)
            }
        tasks += derivedFromBuild(buildPlan, mergedCompleted, cp)
        tasks += derivedFromLayers(buildPlan, mergedCompleted)
        tasks += ProgressionSync.liveRecommendations(live)
        val byId = linkedMapOf<String, ProgressionRecommendation>()
        tasks.forEach { rec ->
            val prev = byId[rec.id]
            byId[rec.id] = when {
                prev == null -> rec
                rec.completed && !prev.completed -> rec
                !rec.completed && prev.completed -> prev
                rec.priorityScore > prev.priorityScore -> rec
                else -> prev
            }
        }
        val merged = byId.values.toList()
        val open = merged.filter { !it.completed }
        val ranked = open.sortedByDescending { it.priorityScore } +
            merged.filter { it.completed }.sortedByDescending { it.priorityScore }
        val notes = LIMITS.toMutableList()
        live?.warnings?.let { notes.addAll(it) }
        if (live != null && live.sources.isNotEmpty()) {
            notes += "Live sync: ${live.sources.joinToString(", ")}."
        }
        return ProgressionPlan(
            character = character,
            recommendations = ranked,
            completedCount = merged.count { it.completed },
            openCount = open.size,
            notes = notes,
            live = live,
        )
    }

    private fun characterSnapshot(
        sheet: ResolvedCharacterSheet?,
        buildPlan: DesiredBuildPlan?,
        buildGoal: BuildGoal,
    ): ProgressionCharacterSnapshot {
        val c = sheet?.sheet?.character
        return ProgressionCharacterSnapshot(
            id = c?.id,
            name = c?.name,
            level = c?.level,
            combatPower = c?.combatPower?.takeIf { it > 0 } ?: buildPlan?.currentCombatPower,
            gearScore = c?.gearScore?.takeIf { it > 0 } ?: buildPlan?.currentGearScore,
            buildGoalLabel = buildGoal.label,
            classLabel = buildPlan?.selectedClass?.name ?: buildPlan?.characterClass?.name ?: c?.className,
        )
    }

    private fun recommendCatalog(
        template: ProgressionTaskTemplate,
        completed: Set<String>,
        level: Long,
        cp: Long?,
        sheet: ResolvedCharacterSheet?,
        buildPlan: DesiredBuildPlan?,
    ): ProgressionRecommendation {
        val reasons = mutableListOf<String>()
        var ease = template.baseEase
        var value = template.progressionValue
        when (template.cadence) {
            ProgressionCadence.Daily -> {
                reasons += "Resets daily — high value per minute when unchecked."
                value += 8
            }
            ProgressionCadence.Weekly -> {
                reasons += "Weekly cap — finish before reset if partially done."
                value += 5
            }
            ProgressionCadence.Monthly -> value += 2
            ProgressionCadence.Always -> reasons += "Always available; rank drops once marked done."
            ProgressionCadence.OneTime -> Unit
        }
        when (template.id) {
            "always_skill_cores" -> {
                val emptyCores = buildPlan?.influences?.count { layer ->
                    layer.layer == BuildLayer.SkillCore.id && layer.slotted == 0
                } ?: 0
                if (emptyCores > 0) {
                    reasons += "$emptyCores empty skill-core slot(s) on Character."
                    ease = ProgressionEase.Easy
                    value += emptyCores * 6
                } else if (sheet != null) {
                    ease = ProgressionEase.Moderate
                    reasons += "No empty core rows detected — still verify in game."
                }
            }
            "always_mastery" -> {
                val typed = sheet?.sheet?.weaponMastery?.count { !it.weapon.isNullOrBlank() } ?: 0
                if (typed == 0) {
                    reasons += "No mastery levels typed on Character."
                    value += 10
                }
            }
            "always_gear" -> {
                val gaps = buildPlan?.advice?.slots?.count { (it.gap ?: 0L) > 0L } ?: 0
                if (gaps > 0) {
                    reasons += "$gaps gear slot(s) behind Build top pick."
                    ease = if (gaps >= 4) ProgressionEase.Hard else ProgressionEase.Moderate
                    value += gaps * 4
                } else if (buildPlan != null) {
                    reasons += "Build shows no extracted gear gaps — maintenance only."
                    ease = ProgressionEase.Easy
                    value -= 15
                }
            }
            "nix_talking_wall" -> {
                if (level < 55) {
                    value -= 40
                    reasons += "Nix content — deprioritized below level 55."
                }
            }
        }
        if (cp != null && cp < 5_000 && template.difficulty == ProgressionDifficulty.Heavy) {
            ease = ProgressionEase.Hard
            reasons += "Low typed CP — heavy content may be inefficient."
        }
        val priority = (value + cadenceBoost(template.cadence) + easeBoost(ease))
            .coerceAtLeast(1)
        return ProgressionRecommendation(
            id = template.id,
            title = template.title,
            detail = template.detail,
            cadence = template.cadence,
            category = template.category,
            ease = ease,
            difficulty = template.difficulty,
            progressionValue = value.coerceIn(1, 100),
            priorityScore = priority,
            completed = template.id in completed,
            source = "catalog",
            reasons = reasons,
        )
    }

    private fun derivedFromBuild(
        buildPlan: DesiredBuildPlan?,
        completed: Set<String>,
        cp: Long?,
    ): List<ProgressionRecommendation> {
        if (buildPlan == null) return emptyList()
        return buildPlan.roadmap
            .filter { it.kind in DERIVED_ROADMAP_KINDS }
            .mapNotNull { step -> roadmapTask(step, completed, cp) }
    }

    private fun roadmapTask(
        step: RoadmapStep,
        completed: Set<String>,
        cp: Long?,
    ): ProgressionRecommendation? {
        val id = "build:${step.kind}:${step.title.hashCode()}"
        if (id in completed) {
            return ProgressionRecommendation(
                id = id,
                title = step.title,
                detail = step.detail,
                cadence = ProgressionCadence.Always,
                category = "Build",
                ease = ProgressionEase.Moderate,
                difficulty = ProgressionDifficulty.Standard,
                progressionValue = 50,
                priorityScore = 1,
                completed = true,
                source = "build",
                reasons = listOf("Marked complete."),
            )
        }
        val (ease, difficulty) = when (step.kind) {
            "slot" -> {
                val hard = (step.itemPowerGap ?: 0L) > 30L || (step.statGap ?: 0L) > 500L
                if (hard) {
                    ProgressionEase.Hard to ProgressionDifficulty.Heavy
                } else {
                    ProgressionEase.Moderate to ProgressionDifficulty.Standard
                }
            }
            "skills" -> ProgressionEase.Moderate to ProgressionDifficulty.Standard
            "layers" -> ProgressionEase.Easy to ProgressionDifficulty.Light
            "modeled-cp-gap", "typed-cp" -> {
                if (cp != null && (step.statGap ?: step.itemPowerGap ?: 0L) > 1_000L) {
                    ProgressionEase.Hard to ProgressionDifficulty.Heavy
                } else {
                    ProgressionEase.Moderate to ProgressionDifficulty.Standard
                }
            }
            else -> ProgressionEase.Moderate to ProgressionDifficulty.Standard
        }
        val value = when (step.kind) {
            "slot" -> 95
            "skills" -> 88
            "layers" -> 84
            "modeled-cp-gap", "typed-cp" -> 76
            else -> 60
        }
        return ProgressionRecommendation(
            id = id,
            title = step.title,
            detail = step.detail,
            cadence = ProgressionCadence.Always,
            category = "Build",
            ease = ease,
            difficulty = difficulty,
            progressionValue = value,
            priorityScore = value + easeBoost(ease),
            completed = false,
            source = "build",
            reasons = listOf("From Build roadmap — warehouse/main_base scoring only."),
        )
    }

    private fun derivedFromLayers(
        buildPlan: DesiredBuildPlan?,
        completed: Set<String>,
    ): List<ProgressionRecommendation> {
        if (buildPlan == null) return emptyList()
        return buildPlan.influences
            .filter { it.slotted == 0 && it.layer !in SKIP_LAYER_IDS }
            .map { layer ->
                val id = "layer:${layer.layer}"
                ProgressionRecommendation(
                    id = id,
                    title = "Fill ${layer.label}",
                    detail = layer.note,
                    cadence = ProgressionCadence.Always,
                    category = "Build layers",
                    ease = ProgressionEase.Easy,
                    difficulty = ProgressionDifficulty.Light,
                    progressionValue = 83,
                    priorityScore = 83 + if (layer.newThisPatch) 12 else 0,
                    completed = id in completed,
                    source = "character",
                    reasons = buildList {
                        add("Empty on Character sheet.")
                        if (layer.newThisPatch) add("New family on this warehouse patch.")
                        if (layer.catalogNamed > 0) add("${layer.catalogNamed} named row(s) in catalog.")
                    },
                )
            }
    }

    private fun cadenceBoost(cadence: ProgressionCadence): Int = when (cadence) {
        ProgressionCadence.Daily -> 12
        ProgressionCadence.Weekly -> 8
        ProgressionCadence.Monthly -> 4
        ProgressionCadence.Always -> 0
        ProgressionCadence.OneTime -> 6
    }

    private fun easeBoost(ease: ProgressionEase): Int = when (ease) {
        ProgressionEase.Easy -> 15
        ProgressionEase.Moderate -> 8
        ProgressionEase.Hard -> 0
    }

    companion object {
        private val DERIVED_ROADMAP_KINDS = setOf(
            "slot", "skills", "layers", "modeled-cp-gap", "typed-cp", "new-influences",
        )
        private val SKIP_LAYER_IDS = setOf(
            BuildLayer.WeaponSkill.id,
            "weapon_mastery_level",
        )
        val LIMITS = listOf(
            "Manual checkboxes, clipboard paste, and NCStorageLocalData.ini hints merge here.",
            "Local config is client-side cache — not verified server completion state.",
            "Rankings mix reset cadence, Build gaps, and empty Character layers. Not live DPS or drop rates.",
            "Mark tasks done as you finish them; dailies/weeklies stay open until toggled or pasted as complete.",
        )
    }
}
