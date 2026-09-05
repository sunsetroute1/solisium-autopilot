package com.solisium.core.source

import kotlin.test.Test
import kotlin.test.assertTrue

class NcStorageIniTest {
    @Test
    fun parsesRealClientFixtureSnippet() {
        val fixture = this::class.java.getResourceAsStream("/ncstorage-fixture.ini")!!.bufferedReader().readText()
        val entries = NcStorageIni.parse(fixture)
        assertTrue(entries.containsKey("kWeeklyRewardPass"), entries.keys.toString())
        assertTrue(entries.containsKey("KTIMEATTACKDUNGEON"), entries.keys.toString())
    }

    @Test
    fun readsRealNcStorageWhenPresent() {
        val path = TlLocalPaths.ncStorageLocalData()
        if (path == null) return
        val snap = TlLocalProgressReader().readFile(path)
        assertTrue(snap.sources.isNotEmpty(), "expected parsed NCStorage keys on this machine")
        assertTrue(snap.items.isNotEmpty(), "expected live progression items from NCStorage")
    }
}
