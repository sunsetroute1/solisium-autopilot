package com.solisium.core.source

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TLHelperLocatorTest {
    @Test
    fun checkoutUrlPointsAtThePublicMirror() {
        assertTrue(TLHelperLocator.CHECKOUT_URL.startsWith("https://github.com/sunsetroute1/"))
    }

    @Test
    fun envPathWinsWhenItIsACheckout() {
        val root = checkout("from-env")
        val locator = TLHelperLocator(
            env = { if (it == "SOLISIUM_TL_HELPER") root.toString() else null },
            isFile = { Files.isRegularFile(it) },
            solisiumHome = Files.createTempDirectory("solisium-tlh-home"),
            userHome = Files.createTempDirectory("solisium-tlh-unused"),
        )
        assertEquals(root, locator.find())
    }

    @Test
    fun rememberedPathIsUsedWhenEnvIsUnset() {
        val home = Files.createTempDirectory("solisium-tlh-remember")
        val root = checkout("remembered")
        val locator = TLHelperLocator(
            env = { null },
            isFile = { Files.isRegularFile(it) },
            solisiumHome = home,
            userHome = Files.createTempDirectory("solisium-tlh-unused"),
        )
        locator.remember(root)
        assertEquals(root.normalize(), locator.find()?.normalize())
    }

    @Test
    fun folderWithoutTheUpdateScriptIsIgnored() {
        val empty = Files.createTempDirectory("solisium-tlh-empty")
        val locator = TLHelperLocator(
            env = { if (it == "SOLISIUM_TL_HELPER") empty.toString() else null },
            isFile = { Files.isRegularFile(it) },
            solisiumHome = Files.createTempDirectory("solisium-tlh-home"),
            userHome = empty,
        )
        assertNull(locator.resolveCheckout(empty))
    }

    @Test
    fun updateScriptPathResolvesToTheCheckout() {
        val root = checkout("script-path")
        val locator = TLHelperLocator(
            env = { null },
            isFile = { Files.isRegularFile(it) },
            solisiumHome = Files.createTempDirectory("solisium-tlh-home"),
            userHome = Files.createTempDirectory("solisium-tlh-unused"),
        )
        assertEquals(root, locator.resolveCheckout(root.resolve("scripts").resolve(TLHelperLocator.UPDATE_SCRIPT)))
    }

    @Test
    fun windowsSpecOpensAVisibleConsoleWithoutTreatingTlHelperAsTheProgram() {
        val checkout = Path.of("D:", "TL_Helper")
        val data = Path.of("D:", "TL_Data")
        val node = Path.of("C:", "Program Files", "nodejs", "node.exe")
        val spec = TLHelperLauncher.spec(checkout, data, node, windows = true)
        val nodeCmd = "\"${node.toAbsolutePath()}\" scripts\\update-tl-helper.mjs"
        assertEquals("cmd.exe", spec.command[0])
        assertEquals("/c", spec.command[1])
        val line = spec.command[2]
        assertTrue(line.startsWith("start \"${TLHelperLauncher.WINDOW_TITLE}\" cmd.exe /k "))
        assertTrue(line.contains("$nodeCmd --only collector"))
        assertTrue(line.contains("$nodeCmd --only decode"))
        assertTrue(line.contains("$nodeCmd --only warehouse"))
        assertTrue(line.contains("scripts\\prepare-warehouse-inputs.mjs"))
        assertEquals(checkout.toAbsolutePath(), spec.workingDir)
        assertEquals(data.toAbsolutePath().toString(), spec.environment["TL_DATA_ROOT"])
        assertTrue(spec.command.none { it.equals("TL-Helper", ignoreCase = true) })
    }

    @Test
    fun launchUsesTheResolvedCheckout() {
        val root = checkout("launch")
        val home = Files.createTempDirectory("solisium-tlh-home")
        val started = mutableListOf<TLHelperLauncher.LaunchSpec>()
        val locator = TLHelperLocator(
            env = { if (it == "SOLISIUM_TL_HELPER") root.toString() else null },
            isFile = { Files.isRegularFile(it) },
            solisiumHome = home,
            userHome = Files.createTempDirectory("solisium-tlh-unused"),
        )
        val launcher = TLHelperLauncher(
            locator = locator,
            env = { null },
            isFile = { Files.isRegularFile(it) },
            start = { started.add(it) },
            isWindows = true,
            solisiumHome = home,
        )
        assertEquals(root, launcher.launch().getOrThrow())
        assertEquals(1, started.size)
        assertEquals("cmd.exe", started.single().command[0])
        assertEquals("/c", started.single().command[1])
        val line = started.single().command[2]
        assertTrue(line.startsWith("start \"${TLHelperLauncher.WINDOW_TITLE}\" cmd.exe /k "))
        assertTrue(line.contains("run-tl-extract.cmd"))
        val script = home.resolve("run-tl-extract.cmd")
        val body = Files.readString(script)
        assertTrue(body.contains("echo {\"stage\":\"collector\""))
        assertTrue(body.contains("prepare-warehouse-inputs.mjs"))
        assertTrue(!body.contains("powershell", ignoreCase = true))
        assertEquals(root.toAbsolutePath(), started.single().workingDir)
    }

    private fun checkout(label: String): Path {
        val root = Files.createTempDirectory("solisium-tlh-$label")
        Files.createDirectories(root.resolve("scripts"))
        Files.writeString(root.resolve("scripts").resolve(TLHelperLocator.UPDATE_SCRIPT), "// fixture")
        return root
    }
}
