package com.solisium.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StatLabelsTest {
    /**
     * The real case this exists for: on a bow, `attack_power_main_hand` and
     * `bonus_attack_power_main_hand` are both localized "Damage". Labelling both rows
     * "Damage" hid the fact that they are different stats, and grouping a chart by that
     * shared label interleaved two series into a meaningless zigzag.
     */
    @Test
    fun keysSharingANameAreDisambiguatedByKey() {
        val labels = statLabels(
            listOf(
                "attack_power_main_hand" to "Damage",
                "bonus_attack_power_main_hand" to "Damage",
                "attack_range" to "Range",
            ),
        )
        assertEquals("Damage (attack_power_main_hand)", labels["attack_power_main_hand"])
        assertEquals("Damage (bonus_attack_power_main_hand)", labels["bonus_attack_power_main_hand"])
        assertEquals("Range", labels["attack_range"])
    }

    @Test
    fun unnamedStatsFallBackToTheirRawKey() {
        val labels = statLabels(listOf("some_unmapped_stat" to null))
        assertEquals("some_unmapped_stat", labels["some_unmapped_stat"])
    }

    /** A name used by only one key must stay clean; no gratuitous suffixes. */
    @Test
    fun uniqueNamesAreLeftAlone() {
        val labels = statLabels(listOf("hp_max" to "Max Health", "melee_armor" to "Defense"))
        assertEquals("Max Health", labels["hp_max"])
        assertEquals("Defense", labels["melee_armor"])
    }

    /** Two unnamed keys must not be treated as sharing a name. */
    @Test
    fun multipleUnnamedKeysDoNotCollide() {
        val labels = statLabels(listOf("a" to null, "b" to null))
        assertEquals("a", labels["a"])
        assertEquals("b", labels["b"])
    }

    @Test
    fun enumTokensAreStrippedToTheirMemberName() {
        assertEquals("AA", prettyEnum("EItemGrade::kAA"))
        assertEquals("Crossbow", prettyEnum("kCrossbow"))
        assertEquals("Bow", prettyEnum("EItemCategory::kBow"))
    }

    @Test
    fun rarityColoursCoverBothClientEnumsAndLooksWords() {
        assertEquals(com.solisium.desktop.theme.Palette.Epic, rarityColor("EItemGrade::kAA"))
        assertEquals(com.solisium.desktop.theme.Palette.Epic, rarityColor("Epic"))
        assertEquals(com.solisium.desktop.theme.Palette.Gold, rarityColor("kAAA"))
        assertEquals(com.solisium.desktop.theme.Palette.Gold, rarityColor("Heroic"))
    }

    /** Anything that is not an enum token must survive untouched. */
    @Test
    fun nonEnumValuesPassThrough() {
        assertEquals("Epic", prettyEnum("Epic"))
        assertEquals("extracted", prettyEnum("extracted"))
        assertEquals("kraken", prettyEnum("kraken"), "lowercase after k is not an enum prefix")
        assertEquals(null, prettyEnum(null))
        assertEquals(null, prettyEnum("   "))
    }
}
