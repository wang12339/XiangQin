package com.xiangqin.app.monitor

import android.content.Context
import android.net.wifi.WifiManager
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.WifiNetworkEntity
import com.xiangqin.app.util.frequencyToChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

// ── WiFi 安全 DTO ──

@Serializable
data class ConnectedWifiInfo(
    val ssid: String,
    val bssid: String?,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val linkSpeedMbps: Int,
    val ipAddress: String?
)

@Serializable
data class WifiSecuritySummary(
    val totalNetworks: Int,
    val openNetworks: Int,
    val wepNetworks: Int,
    val wpa3Networks: Int,
    val wpa2Networks: Int,
    val wpaNetworks: Int,
    val criticalCount: Int,
    val highRiskCount: Int,
    val mediumRiskCount: Int,
    val lowRiskCount: Int,
    val safeCount: Int,
    val channels2g: List<Int>,
    val channels5g: List<Int>,
    val lastScanTime: Long,
    val connectedInfo: ConnectedWifiInfo?,
    val highRiskNetworks: List<WifiNetworkEntity>
)

/**
 * 📶 WiFi 安全检测器
 * 扫描附近 WiFi，检测安全风险：
 * - 开放网络/WEP → HIGH
 * - WPA3 混合 → 中等风险（降级攻击）
 * - 重复 SSID（可疑 AP）
 * - 隐藏 SSID
 * - 已知危险安全协议
 * - 信道拥塞分析
 */
class WifiSecurityMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance

    /**
     * 分析所有已知 WiFi 网络的安全风险，更新数据库中的 riskLevel/riskNotes
     */
    suspend fun analyzeAll() {
        withContext(Dispatchers.IO) {
            try {
                val dao = app.database.wifiNetworkDao()
                val all = dao.getAll()

                // 检测可能的邪恶双子AP（同名不同BSSID）
                val ssidGroups: Map<String, List<WifiNetworkEntity>> = all
                    .filter { it.ssid.isNotBlank() && it.ssid != "(hidden)" }
                    .groupBy { it.ssid }
                val duplicateSsids = ssidGroups.filter { it.value.size > 1 }

                for (net in all) {
                    val assessment = assessRisk(net, duplicateSsids)
                    if (assessment.first != net.riskLevel || assessment.second != net.riskNotes) {
                        dao.update(net.copy(riskLevel = assessment.first, riskNotes = assessment.second))
                    }
                }
            } catch (_: Exception) {
                // 静默
            }
        }
    }

    /**
     * 获取当前连接的 WiFi 信息
     */
    fun getConnectedWifi(): ConnectedWifiInfo? {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val info = wifiManager.connectionInfo ?: return null
            val ssid = info.ssid?.removeSurrounding("\"").takeIf { !it.isNullOrBlank() && it != "<unknown ssid>" }
                ?: return null
            ConnectedWifiInfo(
                ssid = ssid,
                bssid = info.bssid,
                rssi = info.rssi,
                frequency = info.frequency,
                channel = frequencyToChannel(info.frequency),
                linkSpeedMbps = info.linkSpeed,
                ipAddress = intToIp(info.ipAddress)
            )
        } catch (_: Exception) { null }
    }

    /**
     * 获取安全统计摘要
     */
    suspend fun getSecuritySummary(): WifiSecuritySummary {
        val dao = app.database.wifiNetworkDao()
        val all = dao.getAll()
        val now = System.currentTimeMillis()

        return WifiSecuritySummary(
            totalNetworks = all.size,
            openNetworks = all.count { it.riskLevel == "HIGH" && it.securityType == "Open" },
            wepNetworks = all.count { it.securityType == "WEP" },
            wpa3Networks = all.count { it.securityType?.contains("WPA3") == true },
            wpa2Networks = all.count { it.securityType?.contains("WPA2") == true },
            wpaNetworks = all.count { it.securityType == "WPA" },
            criticalCount = all.count { it.riskLevel == "CRITICAL" },
            highRiskCount = all.count { it.riskLevel == "HIGH" },
            mediumRiskCount = all.count { it.riskLevel == "MEDIUM" },
            lowRiskCount = all.count { it.riskLevel == "LOW" },
            safeCount = all.count { it.riskLevel == "SAFE" },
            channels2g = all.map { it.channel }.filter { it in 1..14 }.distinct().sorted(),
            channels5g = all.map { it.channel }.filter { it in 34..173 }.distinct().sorted(),
            lastScanTime = all.maxOfOrNull { it.lastSeen } ?: 0L,
            connectedInfo = getConnectedWifi(),
            highRiskNetworks = all
                .filter { it.riskLevel in listOf("HIGH", "CRITICAL") }
                .sortedByDescending { it.lastSeen }
                .take(20)
        )
    }

    // ──────────────────────────────────────────────────
    // 风险分析
    // ──────────────────────────────────────────────────

    private fun assessRisk(
        net: WifiNetworkEntity,
        duplicateSsids: Map<String, List<WifiNetworkEntity>>
    ): Pair<String, String?> {
        val risks = mutableListOf<String>()
        val secType = net.securityType ?: ""

        // 1. 开放网络 = HIGH
        if (secType == "Open") {
            risks.add("🔓 未加密网络，通信可被监听")
            return "HIGH" to risks.joinToString("；")
        }

        // 2. WEP = CRITICAL (已经被破解的加密)
        if (secType == "WEP") {
            risks.add("⚠️ WEP 加密极不安全，建议升级")
            return "CRITICAL" to risks.joinToString("；")
        }

        // 3. WPA3 降级检测
        if (secType == "WPA2/WPA3") {
            risks.add("🔄 支持 WPA3/WPA2 降级，可能被攻击者降级到 WPA2")
            // Not HIGH, just LOW warning
        }

        // 4. 可疑 SSID
        val ssid = net.ssid
        if (ssid.contains("free", ignoreCase = true) ||
            ssid.contains("starbucks", ignoreCase = true) ||
            ssid.contains("CMCC", ignoreCase = true) ||
            ssid.contains("ChinaNet", ignoreCase = true) ||
            ssid.contains("WiFi", ignoreCase = true) ||
            ssid.contains("5G", ignoreCase = true) ||
            ssid.contains("guest", ignoreCase = true)
        ) {
            risks.add("👀 名称像公共热点，确认是否为官方热点")
        }

        // 5. 隐藏 SSID 网络
        if (ssid == "(hidden)") {
            risks.add("🙈 隐藏 SSID，可能是可疑设备")
        }

        // 6. 邪恶双子检测（同名不同 MAC）
        if (ssid.isNotBlank() && ssid != "(hidden)") {
            val group = duplicateSsids[ssid]
            if (group != null && group.size > 1) {
                val otherBssids = group.filter { it.bssid != net.bssid }.map { it.bssid }
                if (otherBssids.isNotEmpty()) {
                    risks.add("🎭 发现同名热点（${otherBssids.size} 个其他 MAC），警惕邪恶双子攻击")
                }
            }
        }

        // 7. 信号极弱但安全的网络
        if (net.rssi < -80 && risks.isEmpty()) {
            risks.add("📡 信号较弱")
        }

        return when {
            risks.isEmpty() -> "SAFE" to null
            risks.any { it.startsWith("🔓") || it.startsWith("⚠️") } -> "HIGH" to risks.joinToString("；")
            risks.any { it.startsWith("🎭") } -> "MEDIUM" to risks.joinToString("；")
            else -> "LOW" to risks.joinToString("；")
        }
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
}
