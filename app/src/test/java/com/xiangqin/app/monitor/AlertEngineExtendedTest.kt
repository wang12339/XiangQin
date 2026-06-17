package com.xiangqin.app.monitor

import com.xiangqin.app.data.db.HomeZone
import org.junit.Assert.*
import org.junit.Test

class AlertEngineExtendedTest {

    private fun makeHome(lat: Double, lng: Double, radius: Float = 200f) =
        HomeZone(latitude = lat, longitude = lng, radiusMeters = radius, address = null)

    private fun haversine(home: HomeZone, lat: Double, lng: Double): Float {
        val R = 6371000.0
        val dLat = Math.toRadians(lat - home.latitude)
        val dLon = Math.toRadians(lng - home.longitude)
        val a = Math.pow(Math.sin(dLat / 2), 2.0) +
                Math.cos(Math.toRadians(home.latitude)) * Math.cos(Math.toRadians(lat)) *
                Math.pow(Math.sin(dLon / 2), 2.0)
        return (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }

    @Test
    fun `point at equator and prime meridian`() {
        val home = makeHome(0.0, 0.0)
        val dist = haversine(home, 0.001, 0.001)
        assertTrue("Should be ~157m, got $dist", dist in 150f..170f)
    }

    @Test
    fun `antipodal points are ~20000km apart`() {
        val home = makeHome(0.0, 0.0)
        val dist = haversine(home, 0.0, 180.0)
        assertTrue("Should be ~20000km, got ${dist / 1000}km", dist in 20_000_000f..20_100_000f)
    }

    @Test
    fun `home zone radius check - within radius`() {
        val home = makeHome(39.9042, 116.4074, 200f)
        val dist = haversine(home, 39.9045, 116.4074)
        assertTrue("Point within 200m radius", dist <= home.radiusMeters)
    }

    @Test
    fun `home zone radius check - outside radius`() {
        val home = makeHome(39.9042, 116.4074, 100f)
        val dist = haversine(home, 39.9060, 116.4074)
        assertTrue("Point outside 100m radius", dist > home.radiusMeters)
    }

    @Test
    fun `negative coordinates work correctly`() {
        val home = makeHome(-33.8688, 151.2093) // Sydney
        val dist = haversine(home, -33.8698, 151.2093)
        assertTrue("Distance should be ~111m, got $dist", dist in 100f..120f)
    }
}
