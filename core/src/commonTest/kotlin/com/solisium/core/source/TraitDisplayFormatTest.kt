package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertEquals

class TraitDisplayFormatTest {
    @Test
    fun formatsNineLivesTraitLabels() {
        assertEquals("Max Mana 150", TraitDisplayFormat.rollLabel("Max Mana", "cost_max", listOf("150", "300")))
        assertEquals("Mana Regen 15", TraitDisplayFormat.rollLabel("Mana Regen", "cost_regen", listOf("15000", "30000")))
        assertEquals(
            "Mana Cost Efficiency 3%",
            TraitDisplayFormat.rollLabel("Mana Cost Efficiency", "cost_consumption_modifier", listOf("300", "600")),
        )
        assertEquals(
            "Melee Endurance 40",
            TraitDisplayFormat.rollLabel("Melee Endurance", "melee_critical_defense", listOf("400", "800", "1200", "1600")),
        )
        assertEquals(
            "Magic Endurance 64",
            TraitDisplayFormat.rollLabel("Magic Endurance", "magic_critical_defense", listOf("640", "960", "1150", "1280")),
        )
    }

    @Test
    fun scalesEnduranceRawValuesByTen() {
        assertEquals(40.0, TraitDisplayFormat.scale("melee_critical_defense", 400L))
        assertEquals(64.0, TraitDisplayFormat.scale("magic_critical_defense", 640L))
    }
}
