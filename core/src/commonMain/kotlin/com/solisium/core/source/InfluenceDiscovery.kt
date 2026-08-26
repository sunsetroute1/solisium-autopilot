package com.solisium.core.source

import com.solisium.core.domain.DiscoveredInfluence
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.GameSkill

/**
 * Turns newly observed skill-id prefixes into build-influence rows. Does not
 * invent combat-power math or map NPC Guardian skills to the player slot.
 */
object InfluenceDiscovery {
    fun discover(
        current: List<GameSkill>,
        previous: List<GameSkill> = emptyList(),
    ): List<DiscoveredInfluence> {
        val previousPrefixes = previous.mapNotNull { SkillFamilyLookup.prefixGroup(it.sourceRowId) }.toSet()
        return current
            .groupBy { SkillFamilyLookup.prefixGroup(it.sourceRowId) }
            .mapNotNull { (prefix, rows) ->
                if (prefix == null) return@mapNotNull null
                if (SkillFamilyLookup.isCataloguedPrefix(prefix)) return@mapNotNull null
                if (SkillFamilyLookup.isBlockedPrefix(prefix)) return@mapNotNull null
                val named = rows.count { DisplayName.of(it.name, it.sourceRowId) != null }
                if (named == 0) return@mapNotNull null
                val kind = when {
                    prefix.startsWith("WP_", ignoreCase = true) -> "weapon-skill prefix"
                    prefix.startsWith("WM_", ignoreCase = true) -> "mastery-node prefix"
                    else -> "skill prefix"
                }
                DiscoveredInfluence(
                    id = "prefix:$prefix",
                    label = "New $kind $prefix",
                    prefix = prefix,
                    namedCount = named,
                    totalCount = rows.size,
                    newThisPatch = prefix !in previousPrefixes,
                    note = "Observed on this warehouse. Not a hardcoded skills-screen family and not a CP formula.",
                )
            }
            .sortedWith(compareByDescending<DiscoveredInfluence> { it.newThisPatch }.thenBy { it.prefix })
    }
}
