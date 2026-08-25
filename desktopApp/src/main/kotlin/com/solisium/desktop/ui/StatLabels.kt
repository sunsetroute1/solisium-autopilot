package com.solisium.desktop.ui

import com.solisium.core.domain.DisplayName

/**
 * Builds display labels for stat keys.
 *
 * Several distinct client stat keys share one localized name: `attack_power_main_hand`
 * and `bonus_attack_power_main_hand` are both "Damage". Collapsing them under that
 * shared name would merge unrelated series and show two identically labelled rows, so
 * where a name is ambiguous within the set being displayed, the raw key is appended.
 *
 * Keys are never merged. Only the label changes.
 */
/**
 * Strips Unreal enum syntax for display: `EItemGrade::kAA` becomes `AA`, `kCrossbow`
 * becomes `Crossbow`. Purely syntactic — the token is not translated or interpreted,
 * so nothing is invented. Values that do not look like enum tokens pass through.
 */
fun prettyEnum(value: String?): String? = DisplayName.prettyEnum(value)

fun statLabels(keyToName: List<Pair<String, String?>>): Map<String, String> {
    val keysPerName = keyToName
        .filter { it.second != null }
        .groupBy({ it.second!! }, { it.first })
        .mapValues { (_, keys) -> keys.distinct().size }
    return keyToName.associate { (key, name) ->
        val label = when {
            name == null -> key
            (keysPerName[name] ?: 0) > 1 -> "$name ($key)"
            else -> name
        }
        key to label
    }
}
