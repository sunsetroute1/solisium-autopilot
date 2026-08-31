package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TLHelperRunLogTest {
    @Test
    fun decodePreflightNamesTheMissingTableFolder() {
        val status = TLHelperRunLog.parse(
            """
            {
              "status": "preflight-failed",
              "gameBuild": "24958745",
              "finishedAtUtc": "2026-08-31T00:57:11.588Z",
              "preflight": [
                {"name": "decode:input", "ok": false, "detail": "D:\\TL_Data\\raw\\24958745\\extracted\\data"},
                {"name": "warehouse:input", "ok": false, "detail": "D:\\TL_Data\\decoded\\24958745\\tables"}
              ],
              "stages": []
            }
            """.trimIndent(),
        )
        requireNotNull(status)
        assertFalse(status.succeeded)
        assertTrue(status.summary().contains("unpacked Table folder is missing"))
        assertEquals("24958745", status.gameBuild)
    }
}
