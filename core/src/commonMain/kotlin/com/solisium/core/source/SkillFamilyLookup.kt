package com.solisium.core.source

import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.SkillFamily
import com.solisium.core.domain.WeaponTypeLabel

/**
 * Derives skill-screen families from extracted `TLSkill` row ids. Prefixes were
 * observed on Steam build 24829515. Unknown prefixes stay [SkillFamily.Other]
 * rather than being guessed as Guardian or Transcendence.
 */
object SkillFamilyLookup {
    data class Classification(
        val family: SkillFamily,
        val weaponToken: String?,
        val confidence: String,
    )

    fun classify(rowId: String?, skillCategory: String? = null): Classification {
        val id = rowId.orEmpty()
        when {
            id.startsWith("WM_", ignoreCase = true) -> {
                val weapon = weaponCode(id.removePrefix("WM_").removePrefix("wm_").substringBefore("_"))
                return Classification(SkillFamily.Mastery, weapon, "derived")
            }
            id.startsWith("Gem_", ignoreCase = true) || id.startsWith("Gemstone", ignoreCase = true) ->
                return Classification(SkillFamily.Gemstone, null, "derived")
            id.startsWith("WP_Item_", ignoreCase = true) || id.startsWith("WP_item_", ignoreCase = true) ->
                return Classification(SkillFamily.Equipment, weaponFromItemSkill(id), "derived")
            id.startsWith("WP_Polymorph", ignoreCase = true) || id.startsWith("Polymorph", ignoreCase = true) ->
                return Classification(SkillFamily.Morph, null, "derived")
            id.startsWith("WP_", ignoreCase = true) -> {
                val code = id.substringAfter("_").substringBefore("_")
                val weapon = weaponCode(code)
                return if (weapon != null) {
                    Classification(SkillFamily.Weapon, weapon, "derived")
                } else {
                    Classification(SkillFamily.Other, null, "unresolved")
                }
            }
        }
        val category = EquipCategory.token(skillCategory)
        if (category == "kFo" || category == "kPotion") {
            return Classification(SkillFamily.Other, null, "extracted")
        }
        return Classification(SkillFamily.Other, null, "unresolved")
    }

    fun isSkillCoreItem(rowId: String?, name: String?): Boolean {
        val id = rowId.orEmpty()
        if (id.startsWith("perk_", ignoreCase = true) || id.startsWith("Perk_")) return true
        val label = DisplayName.of(name, rowId) ?: name.orEmpty()
        return label.startsWith("Skill Core:", ignoreCase = true)
    }

    /** `perk_orb_aa_t3_boss_001` → `kOrb` when the token is a known weapon. */
    fun skillCoreWeaponHint(rowId: String?): String? {
        val id = rowId?.trim().orEmpty()
        if (!id.startsWith("perk_", ignoreCase = true)) return null
        val token = id.drop(5).substringBefore("_")
        return parseWeaponToken(token) ?: weaponCode(token)
    }

    /**
     * Stable prefix used to group skills across patches. Unknown `WP_XX` codes
     * stay grouped rather than being guessed as a named weapon.
     */
    fun prefixGroup(rowId: String?): String? {
        val id = rowId?.trim().orEmpty()
        if (id.isEmpty()) return null
        return when {
            id.startsWith("WP_Item_", ignoreCase = true) -> "WP_Item"
            id.startsWith("WP_Polymorph", ignoreCase = true) -> "WP_Polymorph"
            id.startsWith("Gem_", ignoreCase = true) || id.startsWith("Gemstone", ignoreCase = true) -> "Gem"
            id.startsWith("WM_", ignoreCase = true) -> {
                val code = id.removePrefix("WM_").removePrefix("wm_").substringBefore("_")
                "WM_${code.uppercase()}"
            }
            id.startsWith("WP_", ignoreCase = true) -> {
                val code = id.substringAfter("_").substringBefore("_")
                "WP_${code.uppercase()}"
            }
            else -> id.substringBefore("_").ifBlank { null }
        }
    }

    fun isCataloguedPrefix(group: String?): Boolean {
        val token = group ?: return false
        return when {
            token.equals("WP_Item", ignoreCase = true) -> true
            token.equals("WP_Polymorph", ignoreCase = true) -> true
            token.equals("Gem", ignoreCase = true) -> true
            token.startsWith("WM_", ignoreCase = true) -> weaponCode(token.removePrefix("WM_").removePrefix("wm_")) != null
            token.startsWith("WP_", ignoreCase = true) -> weaponCode(token.removePrefix("WP_").removePrefix("wp_")) != null
            else -> false
        }
    }

    fun isBlockedPrefix(group: String?): Boolean {
        val token = group?.uppercase() ?: return true
        val head = token.substringBefore("_")
        return head in BLOCKED_PREFIX_HEADS
    }

    fun parseWeaponToken(raw: String?): String? {
        WeaponTypeLabel.combatToken(raw)?.let { return it }
        val folded = raw?.trim()?.lowercase()?.replace(" ", "")?.replace("_", "") ?: return null
        return when (folded) {
            "bow", "longbow" -> "kBow"
            "greatsword", "gs", "sword2h" -> "kSword2h"
            "crossbow" -> "kCrossbow"
            "staff" -> "kStaff"
            "dagger", "daggers" -> "kDagger"
            "sword", "swordandshield", "sns" -> "kSword"
            "wand", "wandandtome" -> "kWand"
            "spear" -> "kSpear"
            "orb" -> "kOrb"
            "gauntlet", "gauntlets" -> "kGauntlet"
            else -> null
        }
    }

    private fun weaponFromItemSkill(rowId: String): String? {
        val parts = rowId.split("_")
        parts.asReversed().forEach { part ->
            weaponCode(part)?.let { return it }
        }
        return null
    }

    private fun weaponCode(raw: String?): String? {
        val code = raw?.uppercase() ?: return null
        return WEAPON_CODES[code]
    }

    private val WEAPON_CODES = mapOf(
        "BO" to "kBow",
        "CR" to "kCrossbow",
        "DA" to "kDagger",
        "ST" to "kStaff",
        "WA" to "kWand",
        "SW2" to "kSword2h",
        "SW" to "kSword",
        "SP" to "kSpear",
        "ORB" to "kOrb",
        "GT" to "kGauntlet",
        "SH" to "kSword",
    )

    private val BLOCKED_PREFIX_HEADS = setOf(
        "SKILL", "FIXTURE", "NPC", "MONSTER", "BOSS", "AB", "BUFF", "DEBUFF",
        "PROJECTILE", "TL", "ESKILL",
    )
}
