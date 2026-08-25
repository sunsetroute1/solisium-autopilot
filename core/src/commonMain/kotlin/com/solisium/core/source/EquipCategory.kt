package com.solisium.core.source

/**
 * Classifies `TLItemEquip.equip_category` values observed on Steam build 24829515.
 * Unknown tokens are left unclassified rather than guessed as armor or weapons.
 */
object EquipCategory {
    private val weapons = setOf(
        "kBow", "kSword2h", "kCrossbow", "kStaff", "kDagger", "kSword", "kWand",
        "kSpear", "kOrb", "kGauntlet", "kFishingRod",
    )
    private val armor = setOf("kHead", "kChest", "kHands", "kLegs", "kFeet", "kCloak")
    private val accessories = setOf("kRing", "kNecklace", "kBracelet", "kBelt", "kEarring", "kBrooch")

    fun token(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val token = raw.substringAfterLast("::")
        return token.takeIf { it.isNotBlank() }
    }

    fun kind(raw: String?): Kind? {
        val token = token(raw) ?: return null
        return when (token) {
            in weapons -> Kind.WEAPON
            in armor -> Kind.ARMOR
            in accessories -> Kind.ACCESSORY
            else -> null
        }
    }

    enum class Kind { WEAPON, ARMOR, ACCESSORY }
}
