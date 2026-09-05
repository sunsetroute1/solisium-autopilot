package com.solisium.core.query

import com.solisium.core.domain.GearWatermarkCategory
import com.solisium.core.domain.GearWatermarkInput
import com.solisium.core.domain.GearWatermarkPlan
import com.solisium.core.domain.WatermarkDropChance
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Gear drop watermark — datamined drop table (Aragon & Kninebeat), as used by
 * [MetaForge](https://metaforge.app/throne-and-liberty/timer) and community calculators.
 *
 * Watermark = floor((weapon + armor + accessory) / 3). Only drops count, not equipped gear.
 */
object GearWatermarkCalculator {
    const val MIN_LEVEL = 45
    const val MAX_LEVEL = 80

    fun plan(input: GearWatermarkInput): GearWatermarkPlan {
        val normalized = GearWatermarkInput(
            weapon = clamp(input.weapon),
            armor = clamp(input.armor),
            accessory = clamp(input.accessory),
        )
        val average = (normalized.weapon + normalized.armor + normalized.accessory) / 3.0
        val watermark = floor(average).roundToInt()
        val mayRoundUp = average > watermark
        val dropChances = dropChancesFor(watermark)
        val upgradeChance = upgradeChancePercent(watermark)
        val farmCategories = farmPriority(normalized)
        val expectedPerCategory = expectedDropsPerCategoryTo80(watermark)
        val notes = buildList {
            add("Watermark = floor(average of highest weapon, armor, and accessory drops).")
            add("Drop table from Aragon & Kninebeat (community datamine). Not an official API.")
            if (mayRoundUp) {
                add("Fractional average ${"%.2f".format(average)} — the game may round up and skip a level.")
            }
            if (normalized.weapon < 51 || farmCategories.contains(GearWatermarkCategory.WEAPON)) {
                add("New expansion dungeons drop no weapons — Hall of Illusion when weapon lags.")
            }
        }
        return GearWatermarkPlan(
            input = normalized,
            average = average,
            watermark = watermark,
            mayRoundUp = mayRoundUp,
            dropChances = dropChances,
            upgradeChancePercent = upgradeChance,
            farmCategories = farmCategories,
            expectedDropsToUpgrade = if (upgradeChance > 0) 100.0 / upgradeChance else null,
            expectedDropsPerCategoryTo80 = expectedPerCategory,
            expectedTotalDropsTo80 = expectedPerCategory * 3,
            atCap = watermark >= MAX_LEVEL,
            notes = notes,
        )
    }

    fun clamp(level: Int): Int = min(MAX_LEVEL, max(MIN_LEVEL, level))

    private fun farmPriority(input: GearWatermarkInput): List<GearWatermarkCategory> {
        val minValue = minOf(input.weapon, input.armor, input.accessory)
        return GearWatermarkCategory.entries.filter { category ->
            when (category) {
                GearWatermarkCategory.WEAPON -> input.weapon == minValue
                GearWatermarkCategory.ARMOR -> input.armor == minValue
                GearWatermarkCategory.ACCESSORY -> input.accessory == minValue
            }
        }.let { tied ->
            // MetaForge highlights one lane when categories tie; weapon wins, then armor.
            tied.firstOrNull()?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun dropChancesFor(watermark: Int): List<WatermarkDropChance> =
        DROP_TABLE[watermark].orEmpty()
            .map { (level, percent) ->
                WatermarkDropChance(
                    delta = level - watermark,
                    gearLevel = level,
                    percent = percent,
                )
            }
            .sortedByDescending { it.gearLevel }

    private fun upgradeChancePercent(watermark: Int): Double =
        if (watermark < 51) {
            100.0
        } else {
            dropChancesFor(watermark)
                .filter { it.delta > 0 }
                .sumOf { it.percent }
        }

    private fun expectedDropsPerCategoryTo80(watermark: Int): Double {
        var total = 0.0
        var level = watermark
        if (level < 51) {
            total += 1.0
            level = 51
        }
        while (level < MAX_LEVEL) {
            val chance = upgradeChancePercent(level)
            if (chance > 0) total += 100.0 / chance
            level += 1
        }
        return total
    }

    /**
     * Datamined per-watermark drop distribution (% per resulting item level).
     * Source: Aragon & Kninebeat spreadsheet (via community calculators).
     */
    private val DROP_TABLE: Map<Int, Map<Int, Double>> = mapOf(
        45 to mapOf(42 to 0.41, 43 to 0.81, 44 to 4.88, 45 to 34.39, 46 to 53.56, 47 to 3.97, 48 to 1.98),
        46 to mapOf(43 to 0.46, 44 to 0.93, 45 to 5.56, 46 to 37.8, 47 to 49.72, 48 to 3.69, 49 to 1.84),
        47 to mapOf(44 to 0.53, 45 to 1.05, 46 to 6.3, 47 to 41.53, 48 to 45.53, 49 to 3.37, 50 to 1.69),
        48 to mapOf(45 to 0.59, 46 to 1.19, 47 to 7.12, 48 to 45.59, 49 to 40.96, 50 to 4.55),
        49 to mapOf(46 to 0.67, 47 to 1.33, 48 to 8.0, 49 to 50.0, 50 to 40.0),
        50 to mapOf(47 to 5.0, 48 to 10.0, 49 to 35.0, 50 to 50.0),
        51 to mapOf(51 to 33.33, 52 to 66.67),
        52 to mapOf(52 to 33.33, 53 to 66.67),
        53 to mapOf(51 to 0.01, 52 to 0.01, 53 to 33.34, 54 to 66.64),
        54 to mapOf(52 to 0.01, 53 to 0.04, 54 to 33.35, 55 to 66.6),
        55 to mapOf(52 to 0.01, 53 to 0.02, 54 to 0.1, 55 to 33.38, 56 to 66.49),
        56 to mapOf(53 to 0.02, 54 to 0.04, 55 to 0.19, 56 to 33.42, 57 to 66.33),
        57 to mapOf(54 to 0.04, 55 to 0.07, 56 to 0.33, 57 to 33.49, 58 to 66.07),
        58 to mapOf(55 to 0.06, 56 to 0.12, 57 to 0.52, 58 to 33.59, 59 to 65.71),
        59 to mapOf(56 to 0.09, 57 to 0.17, 58 to 0.78, 59 to 33.72, 60 to 65.24),
        60 to mapOf(57 to 0.12, 58 to 0.25, 59 to 1.11, 60 to 33.88, 61 to 64.64),
        61 to mapOf(58 to 0.17, 59 to 0.34, 60 to 1.53, 61 to 34.08, 62 to 63.88),
        62 to mapOf(59 to 0.23, 60 to 0.45, 61 to 2.03, 62 to 34.33, 63 to 62.96),
        63 to mapOf(60 to 0.29, 61 to 0.59, 62 to 2.64, 63 to 34.63, 64 to 61.85),
        64 to mapOf(61 to 0.37, 62 to 0.75, 63 to 3.36, 64 to 34.99, 65 to 60.53),
        65 to mapOf(62 to 0.47, 63 to 0.93, 64 to 4.2, 65 to 35.4, 66 to 59.0),
        66 to mapOf(63 to 0.57, 64 to 1.15, 65 to 5.17, 66 to 35.88, 67 to 57.23),
        67 to mapOf(64 to 0.7, 65 to 1.39, 66 to 6.27, 67 to 36.43, 68 to 55.21),
        68 to mapOf(65 to 0.84, 66 to 1.67, 67 to 7.53, 68 to 37.05, 69 to 52.91),
        69 to mapOf(66 to 0.99, 67 to 1.99, 68 to 8.94, 69 to 37.75, 70 to 50.33),
        70 to mapOf(67 to 1.17, 68 to 2.34, 69 to 10.51, 70 to 38.52, 71 to 47.46),
        71 to mapOf(68 to 1.36, 69 to 2.73, 70 to 12.27, 71 to 39.39, 72 to 44.25),
        72 to mapOf(69 to 1.58, 70 to 3.16, 71 to 14.21, 72 to 40.35, 73 to 40.7),
        73 to mapOf(70 to 1.82, 71 to 3.63, 72 to 16.34, 73 to 41.4, 74 to 36.81),
        74 to mapOf(71 to 2.08, 72 to 4.15, 73 to 18.68, 74 to 42.56, 75 to 32.53),
        75 to mapOf(72 to 2.36, 73 to 4.72, 74 to 21.23, 75 to 43.82, 76 to 27.87),
        76 to mapOf(73 to 2.67, 74 to 5.33, 75 to 24.0, 76 to 45.19, 77 to 22.81),
        77 to mapOf(74 to 3.0, 75 to 6.0, 76 to 27.01, 77 to 46.67, 78 to 17.32),
        78 to mapOf(75 to 3.36, 76 to 6.73, 77 to 30.25, 78 to 48.27, 79 to 11.39),
        79 to mapOf(76 to 3.75, 77 to 7.5, 78 to 33.75, 79 to 50.0, 80 to 5.0),
        80 to mapOf(77 to 7.0, 78 to 14.0, 79 to 49.0, 80 to 30.0),
    )
}
