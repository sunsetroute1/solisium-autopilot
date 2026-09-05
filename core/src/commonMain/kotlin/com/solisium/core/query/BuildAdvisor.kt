package com.solisium.core.query

import com.solisium.core.domain.AxisScore
import com.solisium.core.domain.BuildAdvice
import com.solisium.core.domain.BuildClassOption
import com.solisium.core.domain.CommunitySnapshot
import com.solisium.core.domain.DisplayName
import com.solisium.core.domain.GameAccessory
import com.solisium.core.domain.GameItemPower
import com.solisium.core.domain.GameItemStat
import com.solisium.core.domain.GameWeapon
import com.solisium.core.domain.RankedGear
import com.solisium.core.domain.LoadoutKeys
import com.solisium.core.domain.ResolvedCharacterSheet
import com.solisium.core.domain.ResolvedLoadoutLine
import com.solisium.core.domain.SkillShare
import com.solisium.core.domain.SlotAdvice
import com.solisium.core.domain.StatContribution
import com.solisium.core.domain.StatKeyLabel
import com.solisium.core.query.CombatBuildFeedback
import com.solisium.core.meta.MetaBriefing
import com.solisium.core.meta.TextNorm
import com.solisium.core.source.EquipCategory

/**
 * Ranks extracted catalog gear for a chosen goal. Community data is an overlay, never
 * a substitute for warehouse numbers. Curves and extra rolls are not summed in.
 */
class BuildAdvisor(private val query: CatalogQuery) {
    fun advise(
        snapshotId: String,
        goal: BuildGoal,
        characterId: String? = null,
        sheet: ResolvedCharacterSheet? = null,
        community: CommunitySnapshot? = null,
        perSlot: Int = 5,
        extraKeys: Set<String> = emptySet(),
        axes: List<StatAxis> = emptyList(),
        classOption: BuildClassOption? = null,
    ): BuildAdvice {
        val snapshot = query.snapshotService().get(snapshotId)
        val stats = query.allItemStats(snapshotId).filter { it.scope == "main_base" }
        val byRow = stats.groupBy { it.sourceRowId }
        val availableKeys = stats.map { it.statKey }.toSet()
        val keys = goal.keysOn(availableKeys) +
            extraKeys.filter { it in availableKeys } +
            axes.flatMap { it.keysOn(availableKeys) }
        val weaponTokens = classOption?.tokens ?: goal.weaponTokens
        val weapons = query.weapons(snapshotId)
        val armor = query.armor(snapshotId)
        val accessories = query.accessories(snapshotId)
        val grades = query.items(snapshotId)
            .mapNotNull { item -> item.sourceRowId to item.grade.takeUnless { it.isNullOrBlank() } }
            .toMap()
        val weaponsById = weapons.associateBy { it.sourceRowId }
        val itemPower = query.itemPowerByRow(snapshotId)
        val icons = query.itemIcons(snapshotId)

        val weaponRanks = rankWeapons(weapons, byRow, keys, weaponTokens, community, grades, itemPower, icons)
        val armorRanks = rankSlotted(
            armor.map { Triple(slotLabel(it.slot) ?: "armor", it.name, it) },
            byRow,
            keys,
            community,
            kind = "armor",
            tableOf = { it.sourceTable },
            idOf = { it.sourceRowId },
            grades = grades,
            itemPower = itemPower,
            icons = icons,
        )
        val accessoryRanks = rankSlotted(
            accessories.map { Triple(slotLabel(it.slot) ?: "accessory", it.name, it) },
            byRow,
            keys,
            community,
            kind = "accessory",
            tableOf = { it.sourceTable },
            idOf = { it.sourceRowId },
            grades = grades,
            itemPower = itemPower,
            icons = icons,
        )

        val rankedBySlot = linkedMapOf<String, MutableList<RankedGear>>()
        fun put(slot: String, item: RankedGear) {
            rankedBySlot.getOrPut(slot) { mutableListOf() }.add(item)
        }
        weaponRanks.forEach { put(it.slot, it) }
        armorRanks.forEach { put(it.slot, it) }
        accessoryRanks.forEach { put(it.slot, it) }
        rankedBySlot.values.forEach { list ->
            list.sortWith(compareByDescending<RankedGear> { it.score }.thenByDescending { it.communityHits }.thenBy { it.name })
        }

        val resolvedSheet = sheet ?: characterId?.let { query.resolveCharacter(it, snapshotId) }
        ensureEquippedSlots(rankedBySlot, resolvedSheet, weaponsById)
        val slots = rankedBySlot.map { (slot, ranked) ->
            val equipped = equippedIn(resolvedSheet, slot, byRow, keys, community, weaponsById, grades, itemPower, icons)
            val top = ranked.take(perSlot)
            SlotAdvice(
                slot = slot,
                equipped = equipped,
                recommended = top,
                gap = when {
                    equipped == null || top.isEmpty() -> null
                    else -> (top.first().score - equipped.score).coerceAtLeast(0)
                },
            )
        }.sortedBy { SLOT_ORDER.indexOf(it.slot).takeIf { idx -> idx >= 0 } ?: 99 }

        val axes = axisScores(keys, stats, resolvedSheet, rankedBySlot)
        val shares = skillShares(snapshotId, community)
        val insights = CombatBuildFeedback.analyze(shares, resolvedSheet?.sheet?.skills.orEmpty())
        val advice = BuildAdvice(
            snapshotId = snapshotId,
            snapshotBuild = snapshot?.gameBuild,
            goalId = goal.id,
            goalLabel = goal.label,
            scoringNote = SCORING_NOTE,
            slots = slots,
            axes = axes,
            skillShares = shares,
            combatInsights = insights,
            community = community,
            briefing = emptyList(),
            characterName = resolvedSheet?.sheet?.character?.name,
            className = classOption?.name ?: resolvedSheet?.weaponClass?.name,
            classSource = classOption?.source ?: resolvedSheet?.weaponClass?.source,
            classWeaponsLabel = classOption?.weaponsLabel ?: resolvedSheet?.weaponClass?.weaponsLabel,
            weaponTokens = weaponTokens.toList(),
            loadoutLines = resolvedSheet?.lines.orEmpty(),
        )
        return advice.copy(briefing = MetaBriefing.lines(advice, goal, classOption))
    }

    private fun rankWeapons(
        weapons: List<GameWeapon>,
        byRow: Map<String, List<GameItemStat>>,
        keys: Set<String>,
        weaponTokens: Set<String>,
        community: CommunitySnapshot?,
        grades: Map<String, String?>,
        itemPower: Map<String, GameItemPower>,
        icons: Map<String, String>,
    ): List<RankedGear> = weapons.mapNotNull { weapon ->
        val name = DisplayName.of(weapon.name, weapon.sourceRowId) ?: return@mapNotNull null
        if (!acceptsWeapon(weapon.weaponType, weaponTokens)) return@mapNotNull null
        val row = byRow[weapon.sourceRowId] ?: emptyList()
        scored(
            slot = slotLabel(weapon.weaponType) ?: "weapon",
            name = name,
            table = weapon.sourceTable,
            id = weapon.sourceRowId,
            kind = DisplayName.prettyEnum(weapon.weaponType) ?: "weapon",
            grade = grades[weapon.sourceRowId],
            row = row,
            keys = keys,
            community = community,
            itemPower = itemPower[weapon.sourceRowId],
            itemLevel = null,
            iconPath = icons[weapon.sourceRowId],
        )
    }

    private fun <T> rankSlotted(
        rows: List<Triple<String, String?, T>>,
        byRow: Map<String, List<GameItemStat>>,
        keys: Set<String>,
        community: CommunitySnapshot?,
        kind: String,
        tableOf: (T) -> String,
        idOf: (T) -> String,
        grades: Map<String, String?>,
        itemPower: Map<String, GameItemPower>,
        icons: Map<String, String>,
    ): List<RankedGear> = rows.mapNotNull { (slot, rawName, item) ->
        val id = idOf(item)
        val name = DisplayName.of(rawName, id) ?: return@mapNotNull null
        scored(
            slot = slot,
            name = name,
            table = tableOf(item),
            id = id,
            kind = kind,
            grade = grades[id],
            row = byRow[id] ?: emptyList(),
            keys = keys,
            community = community,
            itemPower = itemPower[id],
            itemLevel = null,
            iconPath = icons[id],
        )
    }

    private fun scored(
        slot: String,
        name: String,
        table: String,
        id: String,
        kind: String,
        grade: String?,
        row: List<GameItemStat>,
        keys: Set<String>,
        community: CommunitySnapshot?,
        itemPower: GameItemPower?,
        itemLevel: Long?,
        iconPath: String?,
    ): RankedGear? {
        val contributions = row.filter { it.statKey in keys && it.rawValue != 0L }
            .map { StatContribution(it.statKey, it.rawValue, it.scope) }
        val score = contributions.sumOf { it.rawValue }
        if (score <= 0L) return null
        val weights = ModeledCombatPower.warehouseWeights(itemPower, itemLevel)
        return RankedGear(
            slot = slot,
            name = name,
            sourceTable = table,
            sourceRowId = id,
            score = score,
            grade = grade,
            kind = kind,
            contributions = contributions.sortedByDescending { it.rawValue },
            communityHits = communityHits(name, community),
            itemPower = weights?.current,
            itemPowerEvidence = itemPower?.evidence,
            potentialPower = weights?.potential,
            iconPath = iconPath,
        )
    }

    private fun ensureEquippedSlots(
        rankedBySlot: MutableMap<String, MutableList<RankedGear>>,
        sheet: ResolvedCharacterSheet?,
        weaponsById: Map<String, GameWeapon>,
    ) {
        sheet?.lines.orEmpty().forEach { line ->
            if (line.kind !in setOf("equipment", "weapon")) return@forEach
            if (!lineHasGear(line)) return@forEach
            buildSlotKey(line, weaponsById)?.let { slot ->
                rankedBySlot.putIfAbsent(slot, mutableListOf())
            }
        }
    }

    private fun lineHasGear(line: ResolvedLoadoutLine): Boolean =
        !line.empty && (!line.name.isNullOrBlank() || !LoadoutKeys.isUnspecified(line.sourceRowId))

    private fun equippedLineFor(
        sheet: ResolvedCharacterSheet,
        slot: String,
        weaponsById: Map<String, GameWeapon>,
    ): ResolvedLoadoutLine? =
        sheet.lines.firstOrNull { line -> matchesSlot(line, slot, weaponsById) && lineHasGear(line) }

    private fun buildSlotKey(line: ResolvedLoadoutLine, weaponsById: Map<String, GameWeapon>): String? {
        if (line.kind == "weapon") {
            return line.sourceRowId?.let { weaponsById[it] }?.let { slotLabel(it.weaponType) }
        }
        if (line.kind == "equipment") {
            return equipmentBuildSlot(line.label)
        }
        return null
    }

    private fun equippedIn(
        sheet: ResolvedCharacterSheet?,
        slot: String,
        byRow: Map<String, List<GameItemStat>>,
        keys: Set<String>,
        community: CommunitySnapshot?,
        weaponsById: Map<String, GameWeapon>,
        grades: Map<String, String?>,
        itemPower: Map<String, GameItemPower>,
        icons: Map<String, String>,
    ): RankedGear? {
        if (sheet == null) return null
        val line = equippedLineFor(sheet, slot, weaponsById) ?: return null
        val id = line.sourceRowId?.takeUnless { LoadoutKeys.isUnspecified(it) }
        val name = DisplayName.of(line.hit?.name, id)
            ?: line.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val gearKind = when (line.kind) {
            "equipment" -> "armor"
            else -> line.kind
        }
        if (id == null) {
            return RankedGear(
                slot = slot,
                name = name,
                sourceTable = line.sourceTable.orEmpty(),
                sourceRowId = "",
                score = 0,
                grade = line.hit?.detail,
                kind = gearKind,
                contributions = emptyList(),
                communityHits = communityHits(name, community),
            )
        }
        val itemLevel = itemLevelOf(sheet, id)
        val weights = ModeledCombatPower.warehouseWeights(itemPower[id], itemLevel)
        val iconPath = icons[id]
        return scored(
            slot = slot,
            name = name,
            table = line.sourceTable ?: "",
            id = id,
            kind = gearKind,
            grade = grades[id] ?: line.hit?.detail,
            row = byRow[id] ?: emptyList(),
            keys = keys,
            community = community,
            itemPower = itemPower[id],
            itemLevel = itemLevel,
            iconPath = iconPath,
        ) ?: RankedGear(
            slot = slot,
            name = name,
            sourceTable = line.sourceTable ?: "",
            sourceRowId = id,
            score = 0,
            grade = grades[id] ?: line.hit?.detail,
            kind = gearKind,
            contributions = emptyList(),
            communityHits = communityHits(name, community),
            itemPower = weights?.current,
            itemPowerEvidence = itemPower[id]?.evidence,
            potentialPower = weights?.potential,
            iconPath = iconPath,
        )
    }

    private fun itemLevelOf(sheet: ResolvedCharacterSheet, id: String): Long? =
        sheet.sheet.weapons.firstOrNull { it.sourceRowId == id }?.itemLevel
            ?: sheet.sheet.equipment.firstOrNull { it.sourceRowId == id }?.itemLevel

    private fun matchesSlot(
        line: ResolvedLoadoutLine,
        slot: String,
        weaponsById: Map<String, GameWeapon>,
    ): Boolean {
        if (line.kind == "weapon") {
            val typeSlot = line.sourceRowId?.let { weaponsById[it] }?.let { slotLabel(it.weaponType) }
            return typeSlot == slot || (typeSlot == null && slot == "weapon")
        }
        if (line.kind == "equipment") {
            val mapped = equipmentBuildSlot(line.label) ?: return false
            if (mapped == slot) return true
            if (slot == "earring" && line.label?.lowercase() == "earring2") return true
            if (slot == "ring" && line.label?.lowercase() == "ring2") return true
            return false
        }
        return lineSlot(line.kind, line.label) == slot
    }

    private fun axisScores(
        keys: Set<String>,
        stats: List<GameItemStat>,
        sheet: ResolvedCharacterSheet?,
        rankedBySlot: Map<String, List<RankedGear>>,
    ): List<AxisScore> {
        val equippedIds = sheet?.lines?.mapNotNull { it.sourceRowId }?.toSet().orEmpty()
        val yours = stats.filter { it.sourceRowId in equippedIds && it.statKey in keys }
            .groupBy { it.statKey }
            .mapValues { (_, rows) -> rows.sumOf { it.rawValue } }
        val recommendedIds = rankedBySlot.values.mapNotNull { it.firstOrNull()?.sourceRowId }.toSet()
        val best = stats.filter { it.sourceRowId in recommendedIds && it.statKey in keys }
            .groupBy { it.statKey }
            .mapValues { (_, rows) -> rows.sumOf { it.rawValue } }
        val labels = StatKeyLabel.map(stats.filter { it.statKey in keys }.map { it.statKey to it.statName })
        return keys.map { key ->
            AxisScore(
                key = key,
                label = labels[key] ?: StatKeyLabel.of(key, null),
                yours = yours[key] ?: 0L,
                recommended = best[key] ?: 0L,
            )
        }.sortedByDescending { it.recommended + it.yours }
    }

    private fun skillShares(
        snapshotId: String,
        community: CommunitySnapshot?,
    ): List<SkillShare> {
        val sessions = query.combatSessions()
        if (sessions.isEmpty()) return emptyList()
        val totals = sessions.flatMap { it.skillTotals }
            .groupBy { it.skillName ?: it.skillId ?: "unnamed" }
            .map { (name, rows) ->
                Triple(name, rows.sumOf { it.observedDamageSum }, rows.sumOf { it.hits })
            }
        val damage = totals.sumOf { it.second }.coerceAtLeast(1L)
        val catalog = query.skills(snapshotId)
        return totals.sortedByDescending { it.second }.take(12).map { (name, dmg, hits) ->
            val catalogName = catalog.firstOrNull { TextNorm.likelySame(it.name, name) }?.name
            val questlogName = community?.skills?.firstOrNull { TextNorm.likelySame(it.name, name) }?.name
            SkillShare(
                name = name,
                observedDamage = dmg,
                hits = hits,
                share = dmg.toDouble() / damage,
                catalogName = catalogName,
                questlogName = questlogName,
            )
        }
    }

    companion object {
        const val SCORING_NOTE =
            "Score is the sum of extracted main_base raw values for this goal. " +
                "Not DPS. Enchant curves and extra rolls are not included."

        private val SLOT_ORDER = listOf(
            "bow", "crossbow", "sword", "sword2h", "dagger", "spear", "gauntlet",
            "staff", "wand", "orb", "weapon",
            "head", "chest", "hands", "legs", "feet", "cloak",
            "necklace", "earring", "ring", "bracelet", "belt", "brooch",
        )

        private fun acceptsWeapon(token: String?, allowed: Set<String>): Boolean {
            val normalized = EquipCategory.token(token) ?: return false
            return normalized in allowed
        }

        private fun slotLabel(raw: String?): String? =
            DisplayName.prettyEnum(raw)?.lowercase() ?: raw?.substringAfterLast("::")?.removePrefix("k")?.lowercase()

        private fun equipmentBuildSlot(label: String?): String? {
            val slot = label?.lowercase()?.trim().orEmpty()
            if (slot.isEmpty()) return null
            return when (slot) {
                "earring2" -> "earring"
                "ring2" -> "ring"
                else -> slotLabel(slot) ?: slot
            }
        }

        private fun lineSlot(kind: String, label: String?): String {
            if (kind == "weapon") return "weapon"
            val slot = label?.lowercase() ?: return kind
            if (kind == "equipment") return equipmentBuildSlot(label) ?: kind
            return slotLabel(slot) ?: slot
        }

        private fun communityHits(name: String, community: CommunitySnapshot?): Int {
            if (community == null) return 0
            return community.items.count { TextNorm.likelySame(it.name, name) }
        }
    }
}
