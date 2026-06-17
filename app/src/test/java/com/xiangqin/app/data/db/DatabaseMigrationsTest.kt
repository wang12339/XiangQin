package com.xiangqin.app.data.db

import org.junit.Assert.*
import org.junit.Test

class DatabaseMigrationsTest {

    @Test
    fun `ALL_MIGRATIONS contains 4 migration entries`() {
        assertEquals(4, DatabaseMigrations.ALL_MIGRATIONS.size)
    }

    @Test
    fun `MIGRATION_1_2 has correct version range`() {
        assertEquals(1, DatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, DatabaseMigrations.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `MIGRATION_2_3 has correct version range`() {
        assertEquals(2, DatabaseMigrations.MIGRATION_2_3.startVersion)
        assertEquals(3, DatabaseMigrations.MIGRATION_2_3.endVersion)
    }

    @Test
    fun `MIGRATION_3_4 has correct version range`() {
        assertEquals(3, DatabaseMigrations.MIGRATION_3_4.startVersion)
        assertEquals(4, DatabaseMigrations.MIGRATION_3_4.endVersion)
    }

    @Test
    fun `MIGRATION_4_5 has correct version range`() {
        assertEquals(4, DatabaseMigrations.MIGRATION_4_5.startVersion)
        assertEquals(5, DatabaseMigrations.MIGRATION_4_5.endVersion)
    }

    @Test
    fun `migrations are sequential without gaps`() {
        val versions = DatabaseMigrations.ALL_MIGRATIONS.map { it.startVersion }.toMutableList()
        versions.add(DatabaseMigrations.ALL_MIGRATIONS.last().endVersion)
        assertEquals(listOf(1, 2, 3, 4, 5), versions)
    }
}
