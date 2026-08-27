package com.solisium.core.source

import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue

/** Parses reward-profile lottery pointers from `TLRewardNpcFoItem.raw_json`. */
object RewardLotteryParser {
    fun lotterySlots(rawJson: String): Map<String, String> {
        val json = runCatching { JsonParser.parse(rawJson) }.getOrNull() ?: return emptyMap()
        val groups = json.obj("public_lottery_group_id") ?: return emptyMap()
        return groups.fields.mapNotNull { (slot, value) ->
            val id = (value as? JsonValue.Str)?.value
                ?.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
            id?.let { slot to it }
        }.toMap()
    }
}
