package com.solisium.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharacterAttributesTest {
    @Test
    fun allocatedIsTheTypedSumAndDoesNotInventCombatPower() {
        assertNull(CharacterAttributes.Points().allocated)
        assertEquals(
            59L,
            CharacterAttributes.Points(
                strength = 30,
                dexterity = 12,
                wisdom = 9,
                perception = 5,
                fortitude = 3,
            ).allocated,
        )
        assertEquals(10L, CharacterAttributes.Points(strength = 10).allocated)
    }
}
