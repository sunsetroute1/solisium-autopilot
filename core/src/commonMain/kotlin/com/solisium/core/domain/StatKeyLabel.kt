package com.solisium.core.domain

/**
 * Display labels for extracted stat keys. Names come from the warehouse; when two
 * keys share one name, the key is kept so series are not merged.
 */
object StatKeyLabel {
    fun of(key: String, name: String?): String {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() && !it.equals(key, ignoreCase = true) }
            ?: return key.replace('_', ' ')
        return trimmed
    }

    fun map(keyToName: List<Pair<String, String?>>): Map<String, String> {
        val keysPerName = keyToName
            .map { it.first to of(it.first, it.second) }
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, keys) -> keys.distinct().size }
        return keyToName.associate { (key, name) ->
            val label = of(key, name)
            key to if ((keysPerName[label] ?: 0) > 1) "$label ($key)" else label
        }
    }
}
