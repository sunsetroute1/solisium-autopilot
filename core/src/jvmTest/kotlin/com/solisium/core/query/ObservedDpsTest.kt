package com.solisium.core.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservedDpsTest {
    @Test
    fun computesObservedRateFromLogTimestampsOnly() {
        val dps = CatalogQuery.observedDps(
            startedAt = "2026-07-11T01:31:17.139",
            endedAt = "2026-07-11T01:31:18.139",
            damage = 1000,
        )
        assertEquals(1000.0, dps!!, 0.001)
    }

    @Test
    fun refusesDpsWhenDurationIsMissing() {
        assertNull(CatalogQuery.observedDps(null, "2026-07-11T01:31:18.139", 1000))
        assertNull(CatalogQuery.observedDps("2026-07-11T01:31:17.139", "2026-07-11T01:31:17.139", 1000))
    }

    @Test
    fun unixEpochDayZeroIs1970() {
        val dps = CatalogQuery.observedDps(
            startedAt = "1970-01-01T00:00:00",
            endedAt = "1970-01-01T00:00:02",
            damage = 10,
        )
        assertTrue(dps != null)
        assertEquals(5.0, dps!!, 0.001)
    }
}
