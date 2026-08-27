package com.solisium.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FarmEstimatorTest {
    @Test
    fun expectedKillsFromBestRate() {
        val estimate = FarmEstimator.estimate(
            listOf(
                sampleSource("Boss", 0.01),
                sampleSource("Mob", 0.05),
            ),
        )
        assertNotNull(estimate)
        assertEquals("Mob", estimate.sourceName)
        assertEquals(20L, estimate.expectedKills)
    }

    @Test
    fun returnsNullWhenNoRates() {
        assertNull(FarmEstimator.estimate(listOf(sampleSource("Boss", null))))
    }

    private fun sampleSource(name: String, rate: Double?) = ItemDropSource(
        itemSourceTable = "TLItemLooks",
        itemSourceRowId = "item_1",
        itemName = "Item",
        sourceKind = "npc",
        sourceId = "npc_1",
        sourceName = name,
        sourceLevel = 50,
        sourceCategory = "Boss",
        mapId = null,
        locationLabel = null,
        probability = rate,
        quantity = 1,
        dropType = "normalDrop",
        dropCondition = "normalDrop",
        confidence = "community",
    )
}
