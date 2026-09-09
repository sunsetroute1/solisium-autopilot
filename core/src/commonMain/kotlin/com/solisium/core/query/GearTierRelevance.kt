package com.solisium.core.query

import com.solisium.core.domain.CommunitySnapshot
import com.solisium.core.domain.GearPieceTier
import com.solisium.core.meta.TextNorm

/**
 * Piece-generation relevance from warehouse numbers and observed loadouts —
 * not a calendar caption on T2/T3.
 *
 * A lower generation is marked relevant only when:
 * - extracted combat power still competes with a later-generation sibling, or
 * - the selected trait does not roll on those later siblings, or
 * - a current Questlog / local / Build loadout actually uses the piece.
 */
object GearTierRelevance {
    /** This piece's extracted power vs the strongest later-generation sibling. */
    const val COMPETE_RATIO = 0.85

    data class Facts(
        val sourceRowId: String,
        val name: String?,
        val pieceTier: Int?,
        val basePower: Long? = null,
        val higherGenMaxPower: Long? = null,
        val higherGenExists: Boolean = false,
        val communityHits: Int = 0,
        val communityPatch: String? = null,
        val equippedLocally: Boolean = false,
        val recommendedInBuild: Boolean = false,
        val exclusiveTraitUnlock: Boolean = false,
    )

    data class Verdict(
        val pieceTier: Int?,
        val relevant: Boolean,
        val badge: String,
        val note: String?,
    )

    fun evaluate(facts: Facts): Verdict {
        val tier = facts.pieceTier
        val reasons = mutableListOf<String>()
        val used = facts.communityHits > 0 || facts.equippedLocally || facts.recommendedInBuild
        val compete = statsCompete(facts)
        if (compete && facts.basePower != null) {
            val later = facts.higherGenMaxPower
            reasons += if (later != null && later > 0L) {
                val pct = ((facts.basePower.toDouble() / later.toDouble()) * 100.0).toInt()
                "Warehouse combat power ${facts.basePower} is $pct% of the strongest later-generation sibling ($later)."
            } else {
                "Warehouse combat power ${facts.basePower} has no later-generation sibling with a mapped power row."
            }
        }
        if (facts.exclusiveTraitUnlock) {
            reasons += "The selected trait does not roll on later-generation siblings of this family."
        }
        if (facts.communityHits > 0) {
            val patch = facts.communityPatch?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            reasons += "Listed on a current Questlog loadout or item overlay$patch."
        }
        if (facts.equippedLocally) {
            reasons += "Equipped on an imported character sheet."
        }
        if (facts.recommendedInBuild) {
            reasons += "Recommended in the current Build ranking (raw warehouse stats)."
        }
        val relevant = used || compete || facts.exclusiveTraitUnlock
        val badge = when {
            tier == null -> ""
            relevant && used -> "${GearPieceTier.label(tier)} · used in builds"
            relevant && (compete || facts.exclusiveTraitUnlock) ->
                "${GearPieceTier.label(tier)} · stats compete"
            else -> GearPieceTier.label(tier)
        }
        val note = when {
            reasons.isEmpty() && tier != null && facts.higherGenExists ->
                "Later generations of this family exist. No competing warehouse power and no current loadout use."
            reasons.isEmpty() -> null
            else -> reasons.joinToString(" ")
        }
        return Verdict(pieceTier = tier, relevant = relevant, badge = badge, note = note)
    }

    fun communityHits(name: String?, sourceRowId: String, community: CommunitySnapshot?): Int {
        if (community == null) return 0
        val hits = community.items + community.builds
        return hits.count { hit ->
            hit.entityId?.equals(sourceRowId, ignoreCase = true) == true ||
                (!name.isNullOrBlank() && TextNorm.likelySame(hit.name, name))
        }
    }

    fun higherGenMaxPower(
        sourceRowId: String,
        powerById: Map<String, Long>,
        familyIds: Set<String>,
    ): Pair<Boolean, Long?> {
        val tier = GearPieceTier.fromRowId(sourceRowId) ?: return false to null
        val later = familyIds.filter { id ->
            val other = GearPieceTier.fromRowId(id) ?: return@filter false
            other > tier
        }
        if (later.isEmpty()) return false to null
        val maxPower = later.mapNotNull { powerById[it] }.maxOrNull()
        return true to maxPower
    }

    fun exclusiveTraitUnlock(
        sourceRowId: String,
        familyIds: Set<String>,
        traitItemIds: Set<String>?,
    ): Boolean {
        if (traitItemIds == null || sourceRowId !in traitItemIds) return false
        val tier = GearPieceTier.fromRowId(sourceRowId) ?: return false
        return familyIds.none { id ->
            val other = GearPieceTier.fromRowId(id) ?: return@none false
            other > tier && id in traitItemIds
        }
    }

    private fun statsCompete(facts: Facts): Boolean {
        val power = facts.basePower ?: return false
        if (!facts.higherGenExists) return false
        val later = facts.higherGenMaxPower ?: return false
        if (later <= 0L) return false
        return power.toDouble() >= later.toDouble() * COMPETE_RATIO
    }
}
