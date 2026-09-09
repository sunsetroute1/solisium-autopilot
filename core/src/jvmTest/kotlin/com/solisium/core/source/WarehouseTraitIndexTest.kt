package com.solisium.core.source

import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WarehouseTraitIndexTest {
    @Test
    fun indexesTraitCandidatesByItem() {
        val warehouse = Files.createTempFile("tl-trait-index", ".sqlite")
        DriverManager.getConnection("jdbc:sqlite:${warehouse.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE records (
                      record_id TEXT, row_id TEXT, record_type TEXT, table_name TEXT,
                      name_loc TEXT, game_build TEXT, game_version TEXT, decoder_version TEXT, raw_json TEXT
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO records(record_id, row_id, table_name, raw_json) VALUES
                    ('g1','bow_aa_t2_raid_001','TLItemTraitGroup',
                     '{"TraitCandidates":[{"TraitId":"kRangedEndurance","BaseSeed":3}]}'),
                    ('g2','head_leather_aa_t3_001','TLItemTraitGroup',
                     '{"TraitCandidates":[{"TraitId":"kRangedEndurance"},{"TraitId":"kMaxMana"}]}')
                    """.trimIndent(),
                )
            }
        }
        val index = WarehouseTraitIndex.load(
            warehouse,
            mapOf("kRangedEndurance" to "Ranged Endurance", "kMaxMana" to "Max Mana"),
        )
        assertNotNull(index)
        val trait = index.resolve("ranged endurance")
        assertEquals("kRangedEndurance", trait?.traitId)
        assertEquals(
            setOf("bow_aa_t2_raid_001", "head_leather_aa_t3_001"),
            index.itemIds("kRangedEndurance"),
        )
        assertTrue(index.itemIds("kMaxMana").contains("head_leather_aa_t3_001"))
    }
}
