package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.WifiNetworkEntity
import com.xiangqin.app.util.frequencyToChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 📶 WiFi 热点扫描器
 * 扫描附近 WiFi 热点并存储到数据库
 * Android 10+ 需要 ACCESS_FINE_LOCATION 权限
 */
class WifiMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance

    /** 是否有 WiFi 扫描所需的位置权限 */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 扫描附近 WiFi 并 upsert 到数据库 */
    suspend fun sync() {
        if (!hasPermission()) return

        withContext(Dispatchers.IO) {
            try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return@withContext

                // 尝试启动扫描（Android 10+ 已废弃，但仍然可以尝试）
                // 注意：Android 10+ startScan() 总是返回 false，需要依赖系统自动扫描
                @Suppress("DEPRECATION")
                val scanSuccess = try {
                    wifiManager.startScan()
                } catch (_: SecurityException) {
                    false
                } catch (_: Exception) {
                    false
                }

                // 获取扫描结果（可能是缓存）
                val results = try {
                    wifiManager.scanResults
                } catch (_: SecurityException) {
                    emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                // 如果扫描失败但有缓存结果，仍然使用缓存
                if (results.isEmpty() && !scanSuccess) {
                    // 缓存为空且扫描失败，静默返回
                }

                if (results.isEmpty()) return@withContext

                val now = System.currentTimeMillis()
                val dao = app.database.wifiNetworkDao()

                for (result in results) {
                    val bssid = result.BSSID ?: continue
                    val existing = dao.getByBssid(bssid)

                    if (existing != null) {
                        // 更新已存在的网络（信号强度和最后发现时间）
                        dao.update(
                            existing.copy(
                                rssi = result.level,
                                frequency = result.frequency,
                                channel = frequencyToChannel(result.frequency),
                                capabilities = result.capabilities,
                                lastSeen = now
                            )
                        )
                    } else {
                        // 插入新发现的网络
                        // 使用 wifiSsid 替代废弃的 SSID (Android 10+)
                        val ssid = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            result.wifiSsid?.let { it.toString().takeIf { s -> s.isNotEmpty() } } ?: "(hidden)"
                        } else {
                            @Suppress("DEPRECATION")
                            result.SSID?.ifEmpty { "(hidden)" } ?: "(hidden)"
                        }
                        dao.insert(
                            WifiNetworkEntity(
                                ssid = ssid,
                                bssid = bssid,
                                rssi = result.level,
                                frequency = result.frequency,
                                channel = frequencyToChannel(result.frequency),
                                securityType = parseSecurityType(result.capabilities),
                                capabilities = result.capabilities,
                                firstSeen = now,
                                lastSeen = now
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // 静默处理所有异常，不影响主流程
            }
        }
    }

    /**
     * 从 ScanResult.capabilities 字符串中解析安全类型
     *
     * capabilities 示例：
     *   "[WPA2-PSK-CCMP][ESS]"           -> "WPA2"
     *   "[WPA2-PSK-CCMP][WPA-PSK-CCMP]"   -> "WPA/WPA2"
     *   "[WPA3-SAE-CCMP][ESS]"            -> "WPA3"
     *   "[ESS]"                           -> "Open"
     *   "[WPA2-EAP-CCMP][ESS]"            -> "WPA2-Enterprise"
     */
    internal fun parseSecurityType(capabilities: String): String {
        val caps = capabilities.uppercase()

        val hasWPA3 = caps.contains("WPA3")
        val hasWPA2 = caps.contains("WPA2")
        val hasWPA = caps.contains("WPA") && !caps.contains("WPA2") && !caps.contains("WPA3")
        val hasWEP = caps.contains("WEP")

        return when {
            hasWPA3 && hasWPA2 -> "WPA2/WPA3"
            hasWPA3 -> "WPA3"
            hasWPA2 && hasWPA -> "WPA/WPA2"
            hasWPA2 -> "WPA2"
            hasWPA -> "WPA"
            hasWEP -> "WEP"
            caps.contains("OWE") || caps.contains("OWE_TRANSITION") -> "OWE"
            caps.contains("SAE") -> "SAE"
            // 有加密但没有已知协议标记
            caps.contains("PSK") || caps.contains("EAP") || caps.contains("CCMP") || caps.contains("TKIP") -> "WPA2"
            // 没有安全标记则为开放网络
            else -> "Open"
        }
    }
}
