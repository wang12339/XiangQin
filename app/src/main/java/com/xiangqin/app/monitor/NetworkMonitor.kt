package com.xiangqin.app.monitor

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.net.TrafficStats
import android.util.Log
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.TrafficEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * 流量统计采集器
 *
 * API 31+：遍历已安装 App，逐个 queryDetailsForUid 拿流量
 * API 30-：querySummary 返回 NetworkStats 直接遍历
 */
class NetworkMonitor(private val context: Context) {

    private val statsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val pm = context.packageManager

    suspend fun sync(date: LocalDate = LocalDate.now()) {
        val db = XiangQinApp.instance.database
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = start + 86_400_000L

        val uidTrafficMap = mutableMapOf<Int, TrafficBucket>()

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                collectByApp(start, end, uidTrafficMap)
            } else {
                @Suppress("DEPRECATION")
                collectLegacy(ConnectivityManager.TYPE_WIFI, start, end, uidTrafficMap)
                @Suppress("DEPRECATION")
                collectLegacy(ConnectivityManager.TYPE_MOBILE, start, end, uidTrafficMap)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "sync: SecurityException — 需要 PACKAGE_USAGE_STATS 权限", e)
            return
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            return
        }

        // 如果 NetworkStatsManager 没数据，用 TrafficStats 按 UID 采集
        if (uidTrafficMap.isEmpty()) {
            Log.d(TAG, "sync: NetworkStatsManager empty, trying TrafficStats fallback")
            collectTrafficStatsFallback(uidTrafficMap)
        }

        if (uidTrafficMap.isEmpty()) {
            Log.d(TAG, "sync: no data after all fallbacks")
            return
        }

        val entities = uidTrafficMap.mapNotNull { (uid, t) ->
            if (uid == Process.ROOT_UID || uid == Process.SYSTEM_UID) return@mapNotNull null
            val pkg = try { pm.getNameForUid(uid) } catch (_: Exception) { return@mapNotNull null }
            if (pkg.isNullOrEmpty() || pkg.startsWith("unknown")) return@mapNotNull null
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
            TrafficEntity(
                packageName = pkg, appName = appName,
                rxBytes = t.rx, txBytes = t.tx, statsDate = date.toString()
            )
        }

        if (entities.isNotEmpty()) {
            db.trafficDao().deleteByDate(date.toString())
            db.trafficDao().insertAll(entities)
            Log.d(TAG, "sync: ${entities.size} entries for $date")
        }
    }

    /**
     * API 31+：遍历所有已安装 App，逐个查询流量
     */
    private fun collectByApp(
        start: Long, end: Long,
        result: MutableMap<Int, TrafficBucket>
    ) {
        val installedApps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        } catch (_: Exception) { return }

        for (app in installedApps) {
            val uid = app.uid
            if (uid <= Process.ROOT_UID) continue
            try {
                // API 33+ 使用 NET_CAPABILITY_NOT_METERED 表示 WiFi
                // 移动数据没有单独的常量，需要用其他方式判断
                // API 33+ 使用标准 TYPE_WIFI(1) / TYPE_MOBILE(0)
                @Suppress("DEPRECATION")
                queryPerUidForNetwork(ConnectivityManager.TYPE_WIFI, uid, start, end, result)
                @Suppress("DEPRECATION")
                queryPerUidForNetwork(ConnectivityManager.TYPE_MOBILE, uid, start, end, result)
            } catch (e: SecurityException) {
                // 没权限就跳过
                Log.w(TAG, "collectByApp: SecurityException for uid=$uid — 需要 PACKAGE_USAGE_STATS")
                return
            } catch (_: Exception) {
                // 某些系统 App 可能查询失败，跳过
            }
        }
        Log.d(TAG, "collectByApp: scanned ${installedApps.size} apps")
    }

    /**
     * 对单个 UID 查询指定网络的流量
     */
    private fun queryPerUidForNetwork(
        networkType: Int, uid: Int, start: Long, end: Long,
        result: MutableMap<Int, TrafficBucket>
    ) {
        var ns: NetworkStats? = null
        try {
            ns = statsManager.queryDetailsForUid(networkType, "", start, end, uid)
            val bucket = NetworkStats.Bucket()
            while (ns.getNextBucket(bucket)) {
                val rx = bucket.rxBytes
                val tx = bucket.txBytes
                if (rx <= 0 && tx <= 0) continue
                val prev = result[uid]
                result[uid] = TrafficBucket(rx + (prev?.rx ?: 0), tx + (prev?.tx ?: 0))
            }
        } catch (_: Exception) {
        } finally {
            try { ns?.close() } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
        }
    }

    /**
     * API 30-：querySummary 遍历所有 UID
     */
    private fun collectLegacy(
        networkType: Int, start: Long, end: Long,
        result: MutableMap<Int, TrafficBucket>
    ) {
        var ns: NetworkStats? = null
        try {
            @Suppress("DEPRECATION")
            ns = statsManager.querySummary(networkType, "", start, end)
            val bucket = NetworkStats.Bucket()
            while (ns.getNextBucket(bucket)) {
                val uid = bucket.uid
                val rx = bucket.rxBytes
                val tx = bucket.txBytes
                if (uid <= Process.ROOT_UID || (rx <= 0 && tx <= 0)) continue
                val prev = result[uid]
                result[uid] = TrafficBucket(rx + (prev?.rx ?: 0), tx + (prev?.tx ?: 0))
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "collectLegacy: SecurityException — 需要 PACKAGE_USAGE_STATS 权限")
        } catch (e: Exception) {
            Log.d(TAG, "collectLegacy($networkType): ${e.message}")
        } finally {
            try { ns?.close() } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
        }
    }

    private data class TrafficBucket(val rx: Long, val tx: Long)

    /**
     * TrafficStats fallback：获取每个 UID 的累计流量快照
     * 无法区分日期，但能展示应用流量排名
     */
    private fun collectTrafficStatsFallback(result: MutableMap<Int, TrafficBucket>) {
        val installedApps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        } catch (_: Exception) { return }

        for (app in installedApps) {
            val uid = app.uid
            if (uid <= Process.ROOT_UID) continue
            try {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx <= 0 && tx <= 0) continue
                result[uid] = TrafficBucket(rx, tx)
            } catch (e: Exception) { android.util.Log.w("XiangQin/Network", "获取 UID=$uid 流量失败: ${e.message}") }
        }
        Log.d(TAG, "collectTrafficStatsFallback: ${result.size} UIDs with traffic")
    }

    companion object { private const val TAG = "NetworkMonitor" }
}
