package com.solisium.core.source

import com.solisium.core.testutil.WarehouseFixtures
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillCoreDescriptionServiceTest {
    @Test
    fun resolvesPerkTooltipFromLocresAndEquipLink() {
        val warehouse = WarehouseFixtures.withSkillFamilies(WarehouseFixtures.writeMiniWarehouse())
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO records VALUES
                    ('TLItemEquip:orb_aa_t3_boss_001','orb_aa_t3_boss_001','item','TLItemEquip',null,'24118850','1.431.22.7761','0.2.0',
                     '{"unique_skill_complex_id":"SkillSet_WP_Item_core","equip_category":"EItemCategory::kOrb"}')
                    """.trimIndent(),
                )
            }
        }
        val locres = Files.createTempFile("game-locres", ".json")
        Files.writeString(
            locres,
            """{"TLStringSkillDesc|TEXT_SKILL_DESC_WP_Item_core":"Raises a barrier after 2s."}""",
        )
        try {
            val service = SkillCoreDescriptionService(
                locresLocator = LocresLocator(
                    env = { if (it == "SOLISIUM_LOCRES") locres.toString() else null },
                    isFile = { Files.isRegularFile(it) },
                    listGameLocres = { emptyList() },
                ),
            )
            val text = service.description(
                rowId = "perk_orb_aa_t3_boss_001",
                name = "Skill Core: Talus's Transcendent Barrier",
                warehousePath = warehouse.toString(),
                gameBuild = "24118850",
            )
            assertEquals("Raises a barrier after 2s.", text)
        } finally {
            Files.deleteIfExists(warehouse)
            Files.deleteIfExists(locres)
        }
    }

    @Test
    fun reloadsWhenLocresFileChangesOrInvalidateIsCalled() {
        val locres = Files.createTempFile("game-locres", ".json")
        Files.writeString(
            locres,
            """{"TLStringSkillDesc|TEXT_SKILL_NAME_WP_Item_core":"Talus's Transcendent Barrier","TLStringSkillDesc|TEXT_SKILL_DESC_WP_Item_core":"Old barrier text."}""",
        )
        val service = SkillCoreDescriptionService(
            locresLocator = LocresLocator(
                env = { if (it == "SOLISIUM_LOCRES") locres.toString() else null },
                isFile = { Files.isRegularFile(it) },
                listGameLocres = { emptyList() },
            ),
        )
        val first = service.description(
            rowId = "perk_orb_aa_t3_boss_001",
            name = "Skill Core: Talus's Transcendent Barrier",
            warehousePath = null,
            gameBuild = "24118850",
        )
        assertEquals("Old barrier text.", first)
        Files.writeString(
            locres,
            """{"TLStringSkillDesc|TEXT_SKILL_NAME_WP_Item_core":"Talus's Transcendent Barrier","TLStringSkillDesc|TEXT_SKILL_DESC_WP_Item_core":"New barrier text."}""",
        )
        Files.setLastModifiedTime(
            locres,
            java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(locres).toMillis() + 2_000),
        )
        val afterWrite = service.description(
            rowId = "perk_orb_aa_t3_boss_001",
            name = "Skill Core: Talus's Transcendent Barrier",
            warehousePath = null,
            gameBuild = "24118850",
        )
        assertEquals("New barrier text.", afterWrite)
        Files.writeString(
            locres,
            """{"TLStringSkillDesc|TEXT_SKILL_NAME_WP_Item_core":"Talus's Transcendent Barrier","TLStringSkillDesc|TEXT_SKILL_DESC_WP_Item_core":"After invalidate."}""",
        )
        service.invalidate()
        val afterInvalidate = service.description(
            rowId = "perk_orb_aa_t3_boss_001",
            name = "Skill Core: Talus's Transcendent Barrier",
            warehousePath = null,
            gameBuild = "24118850",
        )
        assertEquals("After invalidate.", afterInvalidate)
        Files.deleteIfExists(locres)
    }

    @Test
    fun locresLocatorPrefersExplicitThenCacheThenBuild() {
        val root = Files.createTempDirectory("tl-data")
        val cached = root.resolve("cache").resolve("game-locres.json")
        Files.createDirectories(cached.parent)
        Files.writeString(cached, "{}")
        val buildLocres = root.resolve("raw").resolve("24958745")
            .resolve("collector").resolve("localization").resolve("en").resolve("Game.locres")
        Files.createDirectories(buildLocres.parent)
        Files.write(buildLocres, byteArrayOf(1, 2, 3))
        try {
            val locator = LocresLocator(
                env = { if (it == "TL_DATA_ROOT") root.toString() else null },
            )
            assertEquals(buildLocres.toAbsolutePath(), locator.find("24958745")!!.toAbsolutePath())
            Files.deleteIfExists(buildLocres)
            assertEquals(cached.toAbsolutePath(), locator.find("24958745")!!.toAbsolutePath())
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun locresParserReadsCollectorGameFileWhenPresent() {
        val locres = Path.of("D:\\TL_Data\\raw\\24958745\\collector\\localization\\en\\Game.locres")
        if (!Files.isRegularFile(locres)) return
        val table = LocresTable.load(locres)
        val desc = table.get("TLStringSkillDesc", "TEXT_SKILL_DESC_WP_Item_FieldBoss_T2_Upgrade_ORB_01")
        assertTrue(!desc.isNullOrBlank())
    }

    @Test
    fun locresTableReadsJsonAliases() {
        val table = LocresTable.fromMap(
            mapOf("TLSkillPcLooks_Item|WP_Item_Nix_Crack_BO_01_RankDescription_ValueIndex0" to "Cry text"),
        )
        assertEquals(
            "Cry text",
            table.get("TLSkillPcLooks_Item", "WP_Item_Nix_Crack_BO_01_RankDescription_ValueIndex0"),
        )
        assertEquals("Cry text", table.skillDescription("WP_Item_Nix_Crack_BO_01"))
    }
}
