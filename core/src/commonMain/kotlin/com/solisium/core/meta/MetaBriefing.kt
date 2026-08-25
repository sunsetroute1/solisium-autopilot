package com.solisium.core.meta

import com.solisium.core.domain.BuildAdvice
import com.solisium.core.query.BuildGoal

/**
 * Deterministic briefing. The only numbers here already exist on [BuildAdvice].
 * An optional LLM may rephrase this; it must not add stats.
 */
object MetaBriefing {
    fun lines(advice: BuildAdvice, goal: BuildGoal): List<String> {
        val lines = mutableListOf<String>()
        lines += "Goal: ${goal.label}. ${goal.blurb}"
        lines += advice.scoringNote
        advice.snapshotBuild?.let { lines += "Warehouse build $it is the numeric source." }
        val weaponSlots = advice.slots.filter { it.slot in WEAPON_SLOTS }
        val topSlot = weaponSlots.maxByOrNull { it.recommended.firstOrNull()?.score ?: Long.MIN_VALUE }
        val topWeapon = topSlot?.recommended?.firstOrNull()
        if (topWeapon != null) {
            lines += "Highest extracted ${goal.label} weapon in this snapshot: ${topWeapon.name} (score ${topWeapon.score})."
        } else {
            lines += "No named weapon in this snapshot scored for ${goal.label}."
        }
        val equippedSlot = weaponSlots.firstOrNull { it.equipped != null }
        val equipped = equippedSlot?.equipped
        if (equipped != null && topWeapon != null) {
            val gap = equippedSlot.gap ?: 0
            lines += if (gap <= 0) {
                "Your ${equipped.name} matches or beats that extracted rank."
            } else {
                "Your ${equipped.name} scores ${equipped.score} against ${topWeapon.score} on the top pick (${gap} raw points behind)."
            }
        } else if (advice.characterName == null) {
            lines += "Import a character JSON to compare your loadout against these ranks."
        }
        val community = advice.community
        if (community != null) {
            community.patchLabel?.let { lines += "Community sites currently mention $it." }
            val overlap = advice.slots.flatMap { it.recommended }.count { it.communityHits > 0 }
            lines += if (overlap > 0) {
                "$overlap ranked pieces also appear in the Questlog search for this goal."
            } else {
                "Questlog returned ${community.items.size} names; none matched the top extracted ranks. That is a naming gap, not a DPS ranking."
            }
            if (community.builds.isNotEmpty()) {
                lines += "Questlog character overlay: ${community.builds.take(3).joinToString { it.name }}."
            }
            community.warnings.forEach { lines += it }
        } else {
            lines += "Community meta has not been fetched this session. Use Search current meta when you want Questlog and TLDB."
        }
        if (advice.skillShares.isNotEmpty()) {
            val top = advice.skillShares.first()
            lines += "Your combat logs spend ${pct(top.share)} of observed damage on ${top.name}."
            top.questlogName?.let { lines += "Questlog lists a skill set named $it." }
        }
        return lines
    }

    private fun pct(share: Double): String = "${(share * 100).toInt()}%"

    private val WEAPON_SLOTS = setOf(
        "bow", "crossbow", "sword", "sword2h", "dagger", "spear", "gauntlet",
        "staff", "wand", "orb", "weapon",
    )
}
