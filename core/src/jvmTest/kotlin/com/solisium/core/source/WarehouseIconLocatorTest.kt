package com.solisium.core.source

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WarehouseIconLocatorTest {
    @Test
    fun assetNameIsTheLastUnrealSegment() {
        assertEquals("bow", WarehouseIconLocator.assetName("/Game/Icon/bow"))
        assertEquals("T_UI_Icon_Head", WarehouseIconLocator.assetName("/Game/Icon/Item/T_UI_Icon_Head"))
        assertNull(WarehouseIconLocator.assetName("   "))
    }

    @Test
    fun findsAPngNamedAfterTheAssetInSolisiumIcons() {
        val home = Files.createTempDirectory("solisium-icon-home")
        val icons = home.resolve("icons")
        Files.createDirectories(icons)
        val png = icons.resolve("bow.png")
        Files.write(png, byteArrayOf(1, 2, 3))
        val locator = WarehouseIconLocator(
            env = { null },
            isFile = { Files.isRegularFile(it) },
            solisiumHome = home,
        )
        assertEquals(png, locator.find("/Game/Icon/bow"))
        assertNull(locator.find("/Game/Icon/missing"))
    }

    @Test
    fun explicitEnvFolderWins() {
        val folder = Files.createTempDirectory("solisium-icon-env")
        val png = folder.resolve("helm.png")
        Files.write(png, byteArrayOf(9))
        val locator = WarehouseIconLocator(
            env = { if (it == "SOLISIUM_ICONS") folder.toString() else null },
            isFile = { Files.isRegularFile(it) },
            solisiumHome = Files.createTempDirectory("solisium-icon-unused"),
        )
        assertEquals(png, locator.find("/Game/Icon/Item/helm"))
    }
}
