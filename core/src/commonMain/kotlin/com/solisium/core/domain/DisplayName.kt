package com.solisium.core.domain

/**
 * Display names as a player would see them.
 *
 * Warehouse `name_loc` is the localized string when the decoder resolved one. Many
 * tables never get that field, and earlier imports stored the row id in `name` so a
 * list sorted by name opened on `Ammo_kA_001` instead of "Sparring Longbow". A value
 * equal to the row id is not a name.
 */
object DisplayName {
    fun of(raw: String?, sourceRowId: String? = null): String? {
        val name = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
            ?: return null
        if (sourceRowId != null && name == sourceRowId) return null
        return name
    }

    /**
     * Looks tables are what the client shows in inventories. Config tables that share
     * the same row id (`TLItemEquip`, `TLItemStats`) are not a second item.
     */
    fun isItemLooks(sourceTable: String): Boolean =
        sourceTable == "TLItemLooks" || sourceTable == "TLItemLooks_Equip"

    /**
     * Strips Unreal enum syntax: `EItemGrade::kAA` → `AA`, `kCrossbow` → `Crossbow`.
     * Purely syntactic — the token is not translated.
     */
    fun prettyEnum(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val afterNamespace = raw.substringAfterLast("::")
        return if (afterNamespace.length > 1 &&
            afterNamespace[0] == 'k' &&
            afterNamespace[1].isUpperCase()
        ) {
            afterNamespace.substring(1)
        } else {
            afterNamespace
        }
    }

    /** Joins enum tokens into a short label, e.g. Attack · Weapon. */
    fun fromEnums(vararg tokens: String?): String? {
        val parts = tokens.mapNotNull { prettyEnum(it) }
            .filter { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
