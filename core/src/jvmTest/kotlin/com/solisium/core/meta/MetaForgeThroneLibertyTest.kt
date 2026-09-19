package com.solisium.core.meta

import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class MetaForgeThroneLibertyTest {
    @Test
    fun `parse anchor from SSR blob`() {
        val html = """regions:{eu:{anchorIso:"2026-08-28T18:55:30+03:00",updatedAt:"2026-08-28T17:26:05.527+00:00"}"""
        assertEquals("2026-08-28T18:55:30+03:00", MetaForgeThroneLiberty.parseAnchorIso(html))
        assertEquals("2026-08-28T18:55:30+03:00", MetaForgeThroneLiberty.parseAnchorIso(html, "na"))
        assertEquals("2026-09-17T19:41:00-06:00", MetaForgeThroneLiberty.FALLBACK_ANCHOR_ISO_NA)
        assertEquals(
            Instant.parse("2026-08-28T15:55:30Z"),
            Instant.ofEpochMilli(MetaForgeThroneLiberty.parseAnchorEpochMs(html)!!),
        )
    }

}
