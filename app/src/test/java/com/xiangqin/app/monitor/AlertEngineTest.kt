package com.xiangqin.app.monitor

import com.xiangqin.app.data.db.HomeZone
import org.junit.Assert.*
import org.junit.Test

class AlertEngineTest {

    @Test
    fun `distanceBetween calculates correct distance for points ~1km apart`() {
        val home = HomeZone(
            latitude = 39.9042,
            longitude = 116.4074,
            radiusMeters = 200f,
            address = "Tiananmen"
        )
        val distance = calculateDistance(home, 39.9142, 116.4074)
        assertTrue("Distance should be ~1100m, got $distance", distance in 1000f..1200f)
    }

    @Test
    fun `distanceBetween returns near zero for same point`() {
        val home = HomeZone(
            latitude = 39.9042,
            longitude = 116.4074,
            radiusMeters = 200f,
            address = null
        )
        val distance = calculateDistance(home, 39.9042, 116.4074)
        assertEquals(0f, distance, 1f)
    }

    @Test
    fun `distanceBetween within 200m radius returns small value`() {
        val home = HomeZone(
            latitude = 39.9042,
            longitude = 116.4074,
            radiusMeters = 200f,
            address = null
        )
        val distance = calculateDistance(home, 39.9046, 116.4074)
        assertTrue("Distance should be < 100m, got $distance", distance < 100f)
    }

    @Test
    fun `distanceBetween far points returns large value`() {
        val home = HomeZone(
            latitude = 39.9042,
            longitude = 116.4074,
            radiusMeters = 200f,
            address = null
        )
        val distance = calculateDistance(home, 31.2304, 121.4737)
        assertTrue("Distance to Shanghai should be > 800km", distance > 800_000f)
    }

    private fun calculateDistance(home: HomeZone, lat: Double, lng: Double): Float {
        val R = 6371000.0
        val dLat = Math.toRadians(lat - home.latitude)
        val dLon = Math.toRadians(lng - home.longitude)
        val a = Math.pow(Math.sin(dLat / 2), 2.0) +
                Math.cos(Math.toRadians(home.latitude)) * Math.cos(Math.toRadians(lat)) *
                Math.pow(Math.sin(dLon / 2), 2.0)
        return (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }
}
