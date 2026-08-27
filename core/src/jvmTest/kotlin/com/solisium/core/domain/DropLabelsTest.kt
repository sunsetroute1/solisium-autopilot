package com.solisium.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DropLabelsTest {
    @Test
    fun conditionLabelsAreHumanReadable() {
        assertEquals("Normal loot", DropLabels.conditionLabel("normalDrop"))
        assertEquals("Normal · 01", DropLabels.conditionLabel("normal_01"))
        assertEquals("Luck · 02", DropLabels.conditionLabel("luck_02"))
    }

    @Test
    fun rateContextExplainsVariantRows() {
        val sources = listOf(
            sampleSource("staff_aa_t1_001"),
            sampleSource("staff_aa_t5_boss_001"),
        )
        assertNotNull(DropLabels.rateContextNote(sources))
    }

    @Test
    fun rateContextSilentForSingleVariant() {
        val sources = listOf(
            sampleSource("staff_aa_t5_boss_001", "normalDrop"),
            sampleSource("staff_aa_t5_boss_001", "luck_01"),
        )
        assertNotNull(DropLabels.rateContextNote(sources))
        assertNull(
            DropLabels.rateContextNote(
                listOf(sampleSource("staff_aa_t5_boss_001", "normalDrop")),
            ),
        )
    }

    private fun sampleSource(rowId: String, condition: String = "normalDrop") = ItemDropSource(
        itemSourceTable = "TLItemLooks",
        itemSourceRowId = rowId,
        itemName = "Staff",
        sourceKind = "npc",
        sourceId = "boss_1",
        sourceName = "Boss",
        sourceLevel = 50,
        sourceCategory = "Boss",
        mapId = null,
        locationLabel = null,
        probability = 0.01,
        quantity = 1,
        dropType = "normalDrop",
        dropCondition = condition,
        confidence = "community",
    )
}
