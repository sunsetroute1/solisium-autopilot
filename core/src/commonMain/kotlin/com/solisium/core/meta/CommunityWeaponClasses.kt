package com.solisium.core.meta

import com.solisium.core.domain.WeaponClassPair
import com.solisium.core.domain.WeaponTypeLabel

/**
 * Community weapon-pair titles (Questlog / Metabattle / ArzyeL). Never written to
 * `game_*`. Extracted `TLPcClass` rows win at resolve time. Unknown pairs,
 * including new weapons that have no published title yet, stay unnamed rather
 * than invented.
 *
 * [merge] is how a later Questlog fetch or a new patch overlay extends the table
 * without a schema change.
 */
object CommunityWeaponClasses {
    private val bundled = listOf(
        pair("kCrossbow", "kDagger", "Scorpion"),
        pair("kCrossbow", "kSword2h", "Outrider"),
        pair("kCrossbow", "kSword", "Raider"),
        pair("kBow", "kCrossbow", "Scout"),
        pair("kCrossbow", "kStaff", "Battleweaver"),
        pair("kCrossbow", "kWand", "Fury"),
        pair("kSword2h", "kWand", "Paladin"),
        pair("kDagger", "kSword2h", "Ravager"),
        pair("kSword", "kSword2h", "Crusader"),
        pair("kBow", "kSword2h", "Ranger"),
        pair("kStaff", "kSword2h", "Sentinel"),
        pair("kBow", "kDagger", "Infiltrator"),
        pair("kBow", "kStaff", "Liberator"),
        pair("kBow", "kWand", "Seeker"),
        pair("kDagger", "kStaff", "Spellblade"),
        pair("kStaff", "kWand", "Invocator"),
        pair("kDagger", "kSword", "Berserker"),
        pair("kBow", "kSword", "Warden"),
        pair("kStaff", "kSword", "Disciple"),
        pair("kSword", "kWand", "Templar"),
        pair("kDagger", "kWand", "Darkblighter"),
        pair("kCrossbow", "kSpear", "Cavalier"),
        pair("kDagger", "kSpear", "Shadowdancer"),
        pair("kSpear", "kSword2h", "Gladiator"),
        pair("kBow", "kSpear", "Impaler"),
        pair("kSpear", "kStaff", "Eradicator"),
        pair("kSpear", "kSword", "Steelheart"),
        pair("kSpear", "kWand", "Voidlance"),
        pair("kCrossbow", "kOrb", "Crucifix"),
        pair("kDagger", "kOrb", "Lunarch"),
        pair("kOrb", "kSword2h", "Justicar"),
        pair("kBow", "kOrb", "Scryer"),
        pair("kOrb", "kStaff", "Enigma"),
        pair("kOrb", "kSpear", "Polaris"),
        pair("kOrb", "kSword", "Guardian"),
        pair("kOrb", "kWand", "Oracle"),
    )

    private val extra = LinkedHashMap<String, WeaponClassPair>()

    fun pairs(): List<WeaponClassPair> {
        val seen = LinkedHashMap<String, WeaponClassPair>()
        bundled.forEach { seen[it.key] = it }
        extra.values.forEach { seen[it.key] = it }
        return seen.values.toList()
    }

    fun names(): List<String> = pairs().map { it.name }.distinct().sorted()

    fun lookup(weaponA: String?, weaponB: String?): WeaponClassPair? {
        val key = WeaponTypeLabel.pairKey(weaponA, weaponB) ?: return null
        return extra[key] ?: bundled.firstOrNull { it.key == key }
    }

    fun suggest(query: String, limit: Int = 16): List<String> {
        val folded = TextNorm.fold(query)
        if (folded.length < 2) return emptyList()
        return names().filter { TextNorm.fold(it).contains(folded) }.take(limit)
    }

    fun merge(rows: List<WeaponClassPair>) {
        for (row in rows) {
            val key = row.key
            if (key.isNotEmpty() && row.name.isNotBlank()) extra[key] = row
        }
    }

    fun clearOverlay() {
        extra.clear()
    }

    /**
     * Pulls `Gladiator – Spear + Greatsword` / `Spear and Greatsword – Gladiator`
     * style lines out of community HTML or markdown. New class titles are kept;
     * weapon tokens that are not in [WeaponTypeLabel] are ignored.
     */
    fun parseFromText(text: String): List<WeaponClassPair> {
        val out = LinkedHashMap<String, WeaponClassPair>()
        val weaponAlt = weaponAliases.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val classPart = "[A-Z][A-Za-z]+(?:[ -][A-Z][A-Za-z]+)?"
        val sep = """\s*(?:and|&|\+|\/|,)\s*"""
        val dash = """\s*[–—\-:]\s*"""
        val patterns = listOf(
            Regex("($classPart)$dash($weaponAlt)$sep($weaponAlt)", RegexOption.IGNORE_CASE),
            Regex("($weaponAlt)$sep($weaponAlt)$dash($classPart)", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val g = match.groupValues
                val parsed = if (weaponToken(g[1]) == null) {
                    triple(g[1], g[2], g[3])
                } else {
                    triple(g[3], g[1], g[2])
                } ?: continue
                out[parsed.key] = parsed
            }
        }
        return out.values.toList()
    }

    private fun triple(name: String, left: String, right: String): WeaponClassPair? {
        val a = weaponToken(left) ?: return null
        val b = weaponToken(right) ?: return null
        val title = name.trim().takeIf { it.length >= 3 } ?: return null
        if (a == b) return null
        val canonical = WeaponTypeLabel.canonical(a, b) ?: return null
        return WeaponClassPair(canonical.first, canonical.second, title)
    }

    private fun pair(a: String, b: String, name: String): WeaponClassPair {
        val canonical = WeaponTypeLabel.canonical(a, b) ?: error("bad community pair $a $b")
        return WeaponClassPair(canonical.first, canonical.second, name)
    }

    private fun weaponToken(raw: String): String? {
        val folded = TextNorm.fold(raw)
        return weaponAliases[folded]
    }

    private val weaponAliases = mapOf(
        "longbow" to "kBow",
        "bow" to "kBow",
        "greatsword" to "kSword2h",
        "two handed sword" to "kSword2h",
        "two-handed sword" to "kSword2h",
        "sword2h" to "kSword2h",
        "crossbow" to "kCrossbow",
        "staff" to "kStaff",
        "daggers" to "kDagger",
        "dagger" to "kDagger",
        "sword and shield" to "kSword",
        "sns" to "kSword",
        "sword" to "kSword",
        "wand and tome" to "kWand",
        "wand" to "kWand",
        "tome" to "kWand",
        "spear" to "kSpear",
        "orb" to "kOrb",
        "gauntlet" to "kGauntlet",
        "gauntlets" to "kGauntlet",
    )
}
