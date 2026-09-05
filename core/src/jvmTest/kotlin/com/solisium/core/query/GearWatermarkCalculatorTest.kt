package com.solisium.core.query

import com.solisium.core.domain.GearWatermarkCategory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GearWatermarkCalculatorTest {
    @Test
    fun `metaforge sample watermark 53`() {
        val plan = GearWatermarkCalculator.plan(
            com.solisium.core.domain.GearWatermarkInput(53, 53, 54),
        )
        assertEquals(53, plan.watermark)
        assertEquals(53.33, plan.average, 0.01)
        assertTrue(plan.mayRoundUp)
        val plusOne = plan.dropChances.first { it.delta == 1 }
        assertEquals(66.64, plusOne.percent, 0.01)
        assertEquals(GearWatermarkCategory.WEAPON, plan.farmCategories.single())
    }

    @Test
    fun `scaryel sample watermark 59`() {
        val plan = GearWatermarkCalculator.plan(
            com.solisium.core.domain.GearWatermarkInput(60, 60, 59),
        )
        assertEquals(59, plan.watermark)
        val plusOne = plan.dropChances.first { it.delta == 1 }
        assertEquals(65.24, plusOne.percent, 0.01)
    }

    @Test
    fun `watermark 79 has five percent chance for il 80`() {
        val plan = GearWatermarkCalculator.plan(
            com.solisium.core.domain.GearWatermarkInput(79, 79, 79),
        )
        val plusOne = plan.dropChances.first { it.gearLevel == 80 }
        assertEquals(5.0, plusOne.percent, 0.01)
    }
}
