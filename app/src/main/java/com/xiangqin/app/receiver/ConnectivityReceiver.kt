package com.xiangqin.app.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.service.MonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 网络连接变化监听器
 *
 * 当设备连接到 WiFi 时，检查监控服务是否还活着。
 * 如果服务已被 MIUI 杀掉，利用网络变化这个系统广播把它拉起来。
 * 同时记录 WiFi SSID 变化用于告警。
 *
 * MIUI 机制：即使 APP 被杀了，系统仍然会广播 CONNECTIVITY_CHANGE
 * 给已注册的 Receiver（只要 APP 在上次启动时注册过）
 *
 * 注意：Android 8+ 使用 NetworkCallback 替代废弃的 CONNECTIVITY_ACTION
 */
class ConnectivityReceiver : BroadcastReceiver() {

    // NetworkCallback 实例，用于 Android 8+
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onReceive(context: Context, intent: Intent) {
        // Android 8+ 通过动态注册方式处理网络变化
        // 静态注册的 BroadcastReceiver 无法接收 CONNECTIVITY_ACTION
        // 这里只处理 Android 7 及以下的场景
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return

        @Suppress("DEPRECATION")
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return

        handleNetworkChange(context)
    }

    /**
     * 处理网络变化的逻辑
     * 提取为独立方法，供 BroadcastReceiver(Android 7-) 和 NetworkCallback(Android 8+) 共用
     */
    private fun handleNetworkChange(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val network = cm.activeNetwork ?: return
        val caps = cm.getNetworkCapabilities(network) ?: return

        // 记录 WiFi 连接信息（服务运行与否都记录）
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            recordWifiInfo(context, cm, network)
        }

        // 服务还在跑就不需要复活
        if (MonitoringService.isRunning) return

        // 检查是否真的有网络连接（避免在断网时也画蛇添足）
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return

        // 有网络连接但服务挂了 → 复活
        restartService(context)
    }

    /**
     * 记录 WiFi 信息，根据 API 版本使用不同的获取方式
     */
    private fun recordWifiInfo(context: Context, cm: ConnectivityManager, network: Network) {
        try {
            var ssid: String? = null
            var bssid: String? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: 使用 NetworkCallback 传入的 Network 获取 WiFi 信息
                // 或者通过 WifiManager 获取
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                // Android 10+ 需要位置权限才能获取 WiFi 信息
                // 这里使用备选方案：通过 Network 获取
                val linkProperties = cm.getLinkProperties(network)
                val wifiInfo = linkProperties?.interfaceName?.let {
                    wifiManager?.scanResults?.find { scan -> scan.SSID.isNotEmpty() }
                }
                ssid = wifiInfo?.SSID
                bssid = wifiInfo?.BSSID
            } else {
                // Android 9 及以下：使用 WifiManager.connectionInfo
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val connectionInfo = wifiManager?.connectionInfo
                ssid = connectionInfo?.ssid?.removeSurrounding("\"")
                bssid = connectionInfo?.bssid
            }

            if (!ssid.isNullOrEmpty()) {
                try {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        XiangQinApp.instance.dataStore.setLastWifiInfo(ssid, bssid)
                    }
                } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
            }
        } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
    }

    /**
     * 启动服务
     */
    private fun restartService(context: Context) {
        val serviceIntent = Intent(context, MonitoringService::class.java)
        serviceIntent.putExtra("RESTART_REASON", "connectivity_change")

        val isEmulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("sdk_gphone")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isEmulator) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        /**
         * 动态注册 NetworkCallback（适用于 Android 8+）
         * 需要在 Application 或 Service 中调用
         */
        @SuppressLint("NewApi")
        fun registerNetworkCallback(context: Context): ConnectivityManager.NetworkCallback {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handleNetworkChangeCompat(context)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    // WiFi 连接变化时也尝试记录信息
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        handleNetworkChangeCompat(context)
                    }
                }

                override fun onLost(network: Network) {
                    // 网络断开时不需要特殊处理
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, callback)
            return callback
        }

        /**
         * 取消注册 NetworkCallback
         */
        @SuppressLint("NewApi")
        fun unregisterNetworkCallback(context: Context, callback: ConnectivityManager.NetworkCallback?) {
            if (callback != null) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    cm.unregisterNetworkCallback(callback)
                } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
            }
        }

        /**
         * 处理网络变化的兼容实现
         */
        private fun handleNetworkChangeCompat(context: Context) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val network = cm.activeNetwork ?: return
            val caps = cm.getNetworkCapabilities(network) ?: return

            // 记录 WiFi 连接信息
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                recordWifiInfoCompat(context, cm, network)
            }

            // 服务还在跑就不需要复活
            if (MonitoringService.isRunning) return

            // 检查是否真的有网络连接
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!hasInternet) return

            // 有网络连接但服务挂了 → 复活
            restartServiceCompat(context)
        }

        /**
         * 记录 WiFi 信息（兼容版本）
         */
        @SuppressLint("NewApi")
        private fun recordWifiInfoCompat(context: Context, cm: ConnectivityManager, network: Network) {
            try {
                var ssid: String? = null
                var bssid: String? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+: 通过 WifiManager 获取最近的扫描结果作为备选
                    // 更好的方案是在运行时动态请求位置权限
                    val wifiManager = context.applicationContext
                        .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    // 尝试获取当前连接的 WiFi 信息
                    val wifiInfo = wifiManager?.connectionInfo as? WifiInfo
                    // Android 10+ connectionInfo 需要 ACCESS_FINE_LOCATION 权限
                    // 如果没有权限，ssid 会返回 <unknown ssid>
                    ssid = wifiInfo?.ssid?.removeSurrounding("\"")
                    bssid = wifiInfo?.bssid
                } else {
                    // Android 9 及以下
                    @Suppress("DEPRECATION")
                    val wifiManager = context.applicationContext
                        .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    @Suppress("DEPRECATION")
                    val connectionInfo = wifiManager?.connectionInfo
                    ssid = connectionInfo?.ssid?.removeSurrounding("\"")
                    bssid = connectionInfo?.bssid
                }

                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                    try {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            XiangQinApp.instance.dataStore.setLastWifiInfo(ssid, bssid)
                        }
                    } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
                }
            } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
        }

        /**
         * 启动服务（兼容版本）
         */
        private fun restartServiceCompat(context: Context) {
            val serviceIntent = Intent(context, MonitoringService::class.java)
            serviceIntent.putExtra("RESTART_REASON", "connectivity_change")

            val isEmulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("sdk_gphone")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isEmulator) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        /**
         * 动态注册时使用的 IntentFilter action（Android 7 及以下）
         */
        const val ACTION_CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE"
    }
}
