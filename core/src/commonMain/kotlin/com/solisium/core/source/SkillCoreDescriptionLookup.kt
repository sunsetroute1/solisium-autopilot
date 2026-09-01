package com.solisium.core.source

/**
 * Resolves a skill-core tooltip from already-extracted locres strings.
 * `$[row.tooltip1]` tokens are replaced with warehouse `TLFormulaParameterNew`
 * display values when present; unresolved tokens stay as-is.
 */
fun interface TooltipFieldLookup {
    fun get(rowId: String, field: String): Double?

    companion object {
        val Empty = TooltipFieldLookup { _, _ -> null }
    }
}

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

    fun description(
        table: LocresLookup,
        name: String?,
        complexId: String?,
        tooltips: TooltipFieldLookup = TooltipFieldLookup.Empty,
    ): String? {
        skillIdCandidates(complexId).forEach { id ->
            table.skillDescription(id)?.let { return substitute(clean(it), tooltips) }
        }
        skillNameFromCore(name)?.let { skill ->
            table.skillDescriptionByName(skill)?.let { return substitute(clean(it), tooltips) }
        }
        return null
    }

    fun substitute(text: String, tooltips: TooltipFieldLookup): String {
        if (!text.contains("$[")) return text
        return PLACEHOLDER.replace(text) { match ->
            formatPlaceholder(match.groupValues[1], tooltips) ?: MISSING_VALUE
        }
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

    private fun formatPlaceholder(inner: String, tooltips: TooltipFieldLookup): String? {
        val parts = inner.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val value = evalExpr(parts[0], tooltips) ?: return null
        val scaled = if (parts.size >= 2) {
            val factor = parts[1].toDoubleOrNull() ?: return null
            value * factor
        } else {
            value
        }
        val format = parts.getOrNull(2)
        return displayNumber(scaled, format)
    }

    private fun evalExpr(expr: String, tooltips: TooltipFieldLookup): Double? {
        val compact = expr.replace(" ", "")
        if (compact.isEmpty()) return null
        val numeric = StringBuilder()
        var index = 0
        while (index < compact.length) {
            val rest = compact.substring(index)
            val field = SIMPLE_FIELD.find(rest)
            if (field != null && field.range.first == 0) {
                val value = tooltips.get(field.groupValues[1], field.groupValues[2]) ?: return null
                numeric.append(value)
                index += field.value.length
                continue
            }
            val ch = compact[index]
            if (ch.isDigit() || ch == '.' || ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                numeric.append(ch)
                index++
                continue
            }
            return null
        }
        return evalArithmetic(numeric.toString())
    }

    private fun evalArithmetic(expr: String): Double? {
        val tokens = ARITH_TOKEN.findAll(expr).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        val rebuilt = tokens.joinToString("")
        if (rebuilt != expr) return null
        val values = ArrayList<Double>()
        val ops = ArrayList<Char>()
        var expectNumber = true
        tokens.forEach { token ->
            val number = token.toDoubleOrNull()
            if (number != null) {
                if (!expectNumber) return null
                values += number
                expectNumber = false
            } else if (token.length == 1 && token[0] in "+-*/") {
                if (expectNumber) return null
                ops += token[0]
                expectNumber = true
            } else {
                return null
            }
        }
        if (expectNumber || values.size != ops.size + 1) return null
        var i = 0
        while (i < ops.size) {
            if (ops[i] == '*' || ops[i] == '/') {
                val right = values[i + 1]
                if (ops[i] == '/' && right == 0.0) return null
                values[i] = if (ops[i] == '*') values[i] * right else values[i] / right
                values.removeAt(i + 1)
                ops.removeAt(i)
            } else {
                i++
            }
        }
        var result = values[0]
        ops.forEachIndexed { idx, op ->
            result = if (op == '+') result + values[idx + 1] else result - values[idx + 1]
        }
        return result
    }

    private fun displayNumber(value: Double, format: String?): String {
        val rounded = if (format != null && format.contains(".1")) {
            kotlin.math.round(value * 10.0) / 10.0
        } else {
            value
        }
        val whole = kotlin.math.round(rounded).toLong()
        return if (kotlin.math.abs(rounded - whole.toDouble()) < 0.0000001) {
            whole.toString()
        } else {
            rounded.toString()
        }
    }

    private val UNREAL_TAG = Regex("""\^<[^>]+>""")
    private val HTML_TAG = Regex("""<[^>]+>""")
    private val PLACEHOLDER = Regex("""\$\[([^\]]+)\]""")
    private val SIMPLE_FIELD = Regex("""([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z0-9_]+)""")
    private val ARITH_TOKEN = Regex("""-?\d+(?:\.\d+)?|[+\-*/]""")
    private const val MISSING_VALUE = "—"
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
