package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files

class TlLocalProgressReaderTest {
    @Test
    fun readsProgressionFromRealClientFixtureSnippet() {
        val fixture = this::class.java.getResourceAsStream("/ncstorage-fixture.ini")!!.bufferedReader().readText()
        val file = Files.createTempFile("ncstorage", ".ini")
        try {
            Files.writeString(file, fixture)
            val snap = TlLocalProgressReader().readFile(file)
            assertTrue(snap.items.any { it.id == "monthly_season" }, snap.items.toString())
            assertTrue(snap.items.any { it.id == "live:time_attack" }, snap.items.toString())
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
