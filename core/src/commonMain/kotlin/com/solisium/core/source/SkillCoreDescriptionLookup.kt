package com.solisium.core.source

/**
 * Resolves a skill-core tooltip from already-extracted locres strings.
 * Does not invent numbers for `$[row.tooltip]` placeholders.
 */
object SkillCoreDescriptionLookup {
    fun skillNameFromCore(name: String?): String? {
        val label = name?.trim().orEmpty()
        if (label.startsWith("Skill Core:", ignoreCase = true)) {
            return label.substringAfter(':').trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    fun equipRowId(perkRowId: String?): String? {
        val id = perkRowId?.trim().orEmpty()
        if (id.startsWith("perk_", ignoreCase = true)) return id.substring(5)
        return null
    }

    fun skillIdCandidates(complexId: String?): List<String> {
        val raw = presentId(complexId) ?: return emptyList()
        val rem = when {
            raw.startsWith("SkillSet_", ignoreCase = true) -> raw.substring("SkillSet_".length)
            else -> raw
        }
        val out = LinkedHashSet<String>()
        out += rem
        if (rem.startsWith("WP_Item_", ignoreCase = true)) {
            val rest = rem.substring("WP_Item_".length)
            out += rest
            out += rem.replace("_Upgrade", "")
            out += rest.replace("_Upgrade", "")
        }
        return out.toList()
    }

    fun description(table: LocresLookup, name: String?, complexId: String?): String? {
        skillIdCandidates(complexId).forEach { id ->
            table.skillDescription(id)?.let { return clean(it) }
        }
        skillNameFromCore(name)?.let { skill ->
            table.skillDescriptionByName(skill)?.let { return clean(it) }
        }
        return null
    }

    fun clean(raw: String): String {
        var text = raw.replace("\r\n", "\n")
        text = UNREAL_TAG.replace(text, "")
        text = HTML_TAG.replace(text, "")
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    private fun presentId(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("None", ignoreCase = true)) return null
        return trimmed
    }

    private val UNREAL_TAG = Regex("""\^<[^>]+>""")
    private val HTML_TAG = Regex("""<[^>]+>""")
}

/** Already-decoded locres strings. Keys are namespace + row key, not pak paths. */
interface LocresLookup {
    fun get(namespace: String, key: String): String?

    fun skillDescription(skillId: String): String? {
        val trimmed = skillId.trim()
        if (trimmed.isEmpty()) return null
        val ids = linkedSetOf(trimmed)
        if (trimmed.startsWith("WP_Item_", ignoreCase = true)) {
            ids += trimmed.substring("WP_Item_".length)
        }
        ids.forEach { id ->
            get("TLStringSkillDesc", "TEXT_SKILL_DESC_$id")?.present()?.let { return it }
            get("TLSkillPcLooks_Item", "${id}_RankDescription_ValueIndex0")?.present()?.let { return it }
            get("TLSkillPcLooks_Item", "${id}_UIDescription")?.present()?.let { return it }
        }
        return null
    }

    fun skillDescriptionByName(skillName: String): String? = null

    fun genericPerkItemDescription(): String? =
        get("TLItemLooks_Equip", "Perk_Item_Description")?.present()

    fun String.present(): String? = trim().takeIf { it.isNotEmpty() }
}
