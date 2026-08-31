package com.solisium.core.domain

/**
 * Player-facing skill / mastery / sidebar families from the Weapon Skills screen.
 * [family] on catalog rows is derived from extracted row-id prefixes, not a
 * warehouse foreign key.
 */
enum class SkillFamily(val id: String, val label: String) {
    Weapon("weapon_skill", "Weapon skills"),
    Mastery("mastery", "Weapon mastery"),
    Equipment("equipment_skill", "Equipment skills"),
    Gemstone("gemstone", "Gemstone skills"),
    Morph("morph", "Guardian / morph"),
    Other("other", "Other"),
    ;

    companion object {
        fun fromId(raw: String?): SkillFamily =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: Other
    }
}

/**
 * Typed character-build layers from the skills screen. Catalog matching uses
 * [catalogFamily] when one exists; otherwise the user types a name.
 */
enum class BuildLayer(
    val id: String,
    val label: String,
    val blurb: String,
    val catalogFamily: SkillFamily?,
) {
    WeaponSkill(
        "weapon_skill",
        "Weapon skills",
        "Active, passive, and defense skills on the two equipped weapons.",
        SkillFamily.Weapon,
    ),
    Specialization(
        "specialization",
        "Skill specialization",
        "Per-skill specialization picks. Presence only; no warehouse CP delta.",
        null,
    ),
    Mastery(
        "mastery",
        "Mastery nodes",
        "Weapon-mastery tree nodes (WM_ rows). The 167 / 151 numbers are typed levels, not these nodes.",
        SkillFamily.Mastery,
    ),
    MaterialEffect(
        "material_effect",
        "Material effect",
        "Sidebar material-effect slots. TLItemMaterialStat is not in the current warehouse collect.",
        null,
    ),
    EquipmentSkill(
        "equipment_skill",
        "Equipment skills",
        "Item-granted skills (WP_Item_ rows).",
        SkillFamily.Equipment,
    ),
    Gemstone(
        "gemstone",
        "Gemstone skills",
        "Gemstone sidebar skills (Gem_ rows).",
        SkillFamily.Gemstone,
    ),
    Guardian(
        "guardian",
        "Guardian skills",
        "Guardian / morph slot. NPC Guardian boss skills are not this family.",
        SkillFamily.Morph,
    ),
    Transcendence(
        "transcendence",
        "Transcendence skills",
        "Transcendence sidebar. No dedicated warehouse table on this collect; names stay typed.",
        null,
    ),
    SkillCore(
        "skill_core",
        "Skill cores",
        "Perk items named Skill Core: … plus the equipment skill they grant.",
        SkillFamily.Equipment,
    ),
    ;

    companion object {
        fun fromId(raw: String?): BuildLayer? =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
    }
}

data class UserWeaponMastery(
    val weapon: String,
    val level: Long?,
)

data class UserBuildLayer(
    val layer: String,
    val slot: String?,
    val sourceTable: String?,
    val sourceRowId: String?,
    val name: String?,
    val level: Long?,
)

data class LayerCoverage(
    val layer: String,
    val label: String,
    val slotted: Int,
    val catalogNamed: Int,
    val resolved: Int,
    val note: String,
    val newThisPatch: Boolean = false,
)

/**
 * A skill-screen family observed in the warehouse that is not one of the
 * hardcoded [BuildLayer] ids. Presence only; no combat-power formula.
 */
data class DiscoveredInfluence(
    val id: String,
    val label: String,
    val prefix: String,
    val namedCount: Int,
    val totalCount: Int,
    val newThisPatch: Boolean,
    val note: String,
    val names: List<String> = emptyList(),
)
