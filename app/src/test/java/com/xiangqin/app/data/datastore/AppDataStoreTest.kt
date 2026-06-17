package com.xiangqin.app.data.datastore

import org.junit.Assert.*
import org.junit.Test

class AppDataStoreTest {

    @Test
    fun `ALERT_TYPES contains all expected types`() {
        val expected = setOf(
            "late_night_leave", "low_battery", "no_heartbeat",
            "off_hour_call", "device_boot", "sim_change",
            "app_install", "wifi_change", "cell_change"
        )
        assertEquals(expected, AppDataStore.ALERT_TYPES.toSet())
    }

    @Test
    fun `ALERT_TYPES has 9 entries`() {
        assertEquals(9, AppDataStore.ALERT_TYPES.size)
    }

    @Test
    fun `DB_PIN constant is db_pin`() {
        assertEquals("db_pin", AppDataStore.DB_PIN)
    }

    @Test
    fun `WEB_PASSWORD constant is web_password`() {
        assertEquals("web_password", AppDataStore.WEB_PASSWORD)
    }

    @Test
    fun `each ALERT_TYPE starts with valid prefix`() {
        val validPrefixes = setOf(
            "late_night_", "low_", "no_", "off_",
            "device_", "sim_", "app_", "wifi_", "cell_"
        )
        for (type in AppDataStore.ALERT_TYPES) {
            val prefix = type.split("_").first() + "_"
            assertTrue(
                "Alert type '$type' should start with a known prefix",
                validPrefixes.any { type.startsWith(it.dropLast(1)) }
            )
        }
    }
}
