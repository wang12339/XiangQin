package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.LocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun capture(): LocationEntity? {
        if (!hasPermission()) return null

        return withContext(Dispatchers.IO) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                // 1. 优先尝试网络定位（省电，5 秒超时）
                var location = requestProviderLocation(locationManager, LocationManager.NETWORK_PROVIDER, 5_000L)

                // 2. 网络定位失败，用 GPS（15 秒超时）
                if (location == null) {
                    location = requestProviderLocation(locationManager, LocationManager.GPS_PROVIDER, 15_000L)
                }

                // 3. 回退到缓存（10 分钟内有效）
                if (location == null) {
                    location = getLastKnown(locationManager, LocationManager.NETWORK_PROVIDER)
                }
                if (location == null) {
                    location = getLastKnown(locationManager, LocationManager.GPS_PROVIDER)
                }

                location?.let {
                    LocationEntity(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        accuracy = it.accuracy,
                        altitude = if (it.hasAltitude()) it.altitude else null,
                        speed = if (it.hasSpeed()) it.speed else null,
                        bearing = if (it.hasBearing()) it.bearing else null,
                        provider = it.provider,
                        recordedTime = System.currentTimeMillis()
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 请求指定 Provider 的位置 — 限频 5 秒间隔 + 50 米最小距离
     */
    private suspend fun requestProviderLocation(
        locationManager: LocationManager,
        provider: String,
        timeoutMs: Long
    ): Location? {
        if (!locationManager.isProviderEnabled(provider)) return null
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                locationManager.removeUpdates(this)
                                if (continuation.isActive) continuation.resume(location)
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(p: String?, s: Int, extras: android.os.Bundle?) {}
                            override fun onProviderEnabled(p: String) {}
                            override fun onProviderDisabled(p: String) {
                                locationManager.removeUpdates(this)
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }
                        locationManager.requestLocationUpdates(
                            provider, 5000L, 50f, listener, Looper.getMainLooper()
                        )
                        continuation.invokeOnCancellation {
                            locationManager.removeUpdates(listener)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            null
        }
    }

    /** 缓存位置 — 10 分钟内有效（原来 5 分钟） */
    private fun getLastKnown(locationManager: LocationManager, provider: String): Location? {
        return try {
            if (locationManager.isProviderEnabled(provider)) {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null && System.currentTimeMillis() - loc.time < 10 * 60_000) loc else null
            } else null
        } catch (_: SecurityException) { null }
        catch (_: Exception) { null }
    }

    suspend fun sync(): LocationEntity? {
        val loc = capture() ?: return null
        try { app.database.locationDao().insert(loc) } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
        return loc
    }
}
