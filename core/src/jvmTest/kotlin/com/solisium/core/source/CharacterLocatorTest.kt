package com.solisium.core.source

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterLocatorTest {
    @Test
    fun firstRunCreatesAStarterDocumentInTheSolisiumHome() {
        val home = Files.createTempDirectory("solisium-char-home")
        val locator = CharacterLocator(
            env = { null },
            solisiumHome = home,
            cwd = home,
            exampleText = { STARTER },
        )
        val dir = locator.prepareHome()
        val seeded = dir.resolve("character.json")
        assertTrue(Files.isRegularFile(seeded))
        assertEquals(seeded, locator.find())
        locator.prepareHome()
        assertEquals(STARTER, Files.readString(seeded), "a second launch must not overwrite an existing sheet")
    }

    @Test
    fun rememberedPathBeatsTheStarterAndSaveGamesAreIgnored() {
        val home = Files.createTempDirectory("solisium-char-remember")
        val other = Files.createTempDirectory("solisium-char-other").resolve("main.json")
        Files.writeString(other, STARTER.replace("replace-me", "main"))
        val locator = CharacterLocator(
            env = { null },
            solisiumHome = home,
            cwd = home,
            exampleText = { STARTER },
        )
        locator.prepareHome()
        locator.remember(other)
        assertEquals(other.normalize(), locator.find()?.normalize())
        assertTrue(locator.findImportable().none { it.toString().contains("SaveGames") })
    }

    @Test
    fun envPathWinsAndNonCharacterJsonIsSkipped() {
        val home = Files.createTempDirectory("solisium-char-env")
        val chosen = Files.createTempDirectory("solisium-char-chosen").resolve("hero.json")
        Files.writeString(chosen, STARTER.replace("replace-me", "hero"))
        Files.writeString(home.resolve("noise.json"), """{"schema":"not-a-character"}""")
        val locator = CharacterLocator(
            env = { if (it == "SOLISIUM_CHARACTER") chosen.toString() else null },
            solisiumHome = home,
            cwd = home,
            exampleText = { STARTER },
        )
        assertEquals(chosen.normalize(), locator.find()?.normalize())
        assertEquals(chosen.parent, locator.pickerDirectory())
    }

    companion object {
        private val STARTER = """
            {
              "schema": "solisium.manual-character",
              "schemaVersion": 1,
              "character": { "id": "replace-me", "name": "Your character" }
            }
        """.trimIndent()
    }
}
