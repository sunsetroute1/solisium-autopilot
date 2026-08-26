package com.solisium.core.query

import com.solisium.core.domain.ClassSource
import com.solisium.core.domain.GameClass
import com.solisium.core.domain.WeaponClassMatch
import com.solisium.core.domain.WeaponTypeLabel
import com.solisium.core.meta.CommunityWeaponClasses
import com.solisium.core.meta.TextNorm

object WeaponClassResolver {
    fun resolve(
        extracted: List<GameClass>,
        weaponA: String?,
        weaponB: String?,
    ): WeaponClassMatch {
        val canonical = WeaponTypeLabel.canonical(weaponA, weaponB)
        val a = canonical?.first
        val b = canonical?.second
        val base = WeaponClassMatch(weaponA = a, weaponB = b)
        if (a == null || b == null) return base
        val fromGame = extracted.firstOrNull { row ->
            WeaponTypeLabel.pairKey(row.weaponA, row.weaponB) == WeaponTypeLabel.pairKey(a, b) &&
                !row.name.isNullOrBlank()
        }
        if (fromGame != null) {
            return base.copy(name = fromGame.name, source = ClassSource.EXTRACTED)
        }
        val community = CommunityWeaponClasses.lookup(a, b)
        if (community != null) {
            return base.copy(name = community.name, source = ClassSource.COMMUNITY)
        }
        return base
    }

    fun applyStored(
        storedName: String?,
        storedSource: String?,
        suggested: WeaponClassMatch,
    ): WeaponClassMatch {
        if (ClassSource.isManual(storedSource)) {
            return suggested.copy(name = storedName, source = ClassSource.MANUAL)
        }
        if (suggested.name != null) return suggested
        if (!storedName.isNullOrBlank()) {
            return suggested.copy(name = storedName, source = storedSource)
        }
        return suggested
    }

    fun sameTitle(left: String?, right: String?): Boolean {
        val a = TextNorm.fold(left.orEmpty())
        val b = TextNorm.fold(right.orEmpty())
        return a.isNotEmpty() && a == b
    }
}
