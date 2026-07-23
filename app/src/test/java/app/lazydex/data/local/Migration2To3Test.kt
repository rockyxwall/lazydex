package app.lazydex.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class Migration2To3Test {

    @Test
    fun migration2To3_versionIs3() {
        val migration = MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }
}
