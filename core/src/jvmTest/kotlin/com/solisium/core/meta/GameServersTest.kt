package com.solisium.core.meta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameServersTest {
    @Test
    fun matchesTypedCharacterServerText() {
        assertEquals("Adentus", GameServers.find("adentus")?.name)
        assertEquals("na-west", GameServers.find("west")?.region)
        assertTrue(GameServers.find("korea")?.region == "kr")
    }
}
