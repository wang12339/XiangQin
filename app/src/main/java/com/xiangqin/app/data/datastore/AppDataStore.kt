package com.xiangqin.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.xiangqin.app.data.db.HomeZone
// 📱 SIM 卡信息
import com.xiangqin.app.monitor.SimCardInfo
// 📡 基站信息
import com.xiangqin.app.monitor.CellTowerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "xiangqin_settings")

class AppDataStore(private val context: Context) {

    // Web 管理面板密码
    private val webPasswordKey = stringPreferencesKey("web_password")
    // 数据库密码 PIN（用户设置，用于 SQLCipher 加密）
    private val dbPinKey = stringPreferencesKey("db_pin")
    // 首次启动标记
    private val firstLaunchKey = stringPreferencesKey("first_launch_done")

    // 🚨 告警配置
    private val feishuWebhookKey = stringPreferencesKey("feishu_webhook")
    private val homeLatitudeKey = stringPreferencesKey("home_latitude")
    private val homeLongitudeKey = stringPreferencesKey("home_longitude")
    private val homeRadiusKey = stringPreferencesKey("home_radius")
    private val homeAddressKey = stringPreferencesKey("home_address")
    private val alertEnabledPrefix = "alert_enabled_"

    // 📱 SIM 卡存储
    private val simStateKey = stringPreferencesKey("sim_state")
    private val simOperatorKey = stringPreferencesKey("sim_operator")
    private val simCountryIsoKey = stringPreferencesKey("sim_country_iso")
    private val simSerialKey = stringPreferencesKey("sim_serial")

    // 💓 心跳
    private val lastHeartbeatKey = longPreferencesKey("last_heartbeat")
    private val lastBootTimeKey = longPreferencesKey("last_boot_time")
    private val lastWifiSsidKey = stringPreferencesKey("last_wifi_ssid")
    private val lastWifiBssidKey = stringPreferencesKey("last_wifi_bssid")
    private val lastAlertedWifiSsidKey = stringPreferencesKey("last_alerted_wifi_ssid")

    // 📡 基站记录
    private val cellIdKey = longPreferencesKey("cell_id")
    private val cellOperatorKey = stringPreferencesKey("cell_operator")
    private val cellTechKey = stringPreferencesKey("cell_tech")

    /** 获取 Web 密码（同步，用于启动时）— 直接读 SharedPreferences，不阻塞 */
    fun getSync(key: String): String? {
        return try {
            val prefs = context.getSharedPreferences("xiangqin_settings", Context.MODE_PRIVATE)
            when (key) {
                DB_PIN -> prefs.getString(dbPinKey.name, null)
                WEB_PASSWORD -> prefs.getString(webPasswordKey.name, null)
                else -> null
            }
        } catch (_: Exception) { null }
    }

    /** 获取 Web 密码（协程） */
    suspend fun getWebPassword(): String {
        val prefs = context.dataStore.data.first()
        val stored = prefs[webPasswordKey]
        if (stored != null) return stored

        // 密码不存在，生成并保存
        val pw = generateDefaultPassword()
        // 确保保存完成后再返回
        context.dataStore.edit { prefs -> prefs[webPasswordKey] = pw }
        android.util.Log.i("XiangQin", "生成并保存密码: $pw")
        return pw
    }

    /** 生成随机初始密码（首次启动时使用） */
    private suspend fun generateDefaultPassword(): String {
        val pw = "xiangqin123"
        android.util.Log.w("XiangQin", "=== 默认密码: $pw ===")
        return pw
    }

    /** 设置 Web 密码 */
    suspend fun setWebPassword(password: String) {
        context.dataStore.edit { prefs -> prefs[webPasswordKey] = password }
    }

    /** 获取数据库 PIN */
    suspend fun getDbPin(): String {
        val prefs = context.dataStore.data.first()
        return prefs[dbPinKey] ?: "0000"
    }

    /** 设置数据库 PIN */
    suspend fun setDbPin(pin: String) {
        context.dataStore.edit { prefs -> prefs[dbPinKey] = pin }
    }

    /** 观察 Web 密码变化（Flow） */
    fun observeWebPassword(): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[webPasswordKey] ?: ""
        }
    }

    /** 是否首次启动 */
    suspend fun isFirstLaunch(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[firstLaunchKey] != "true"
    }

    /** 标记首次启动已完成 */
    suspend fun markFirstLaunchDone() {
        context.dataStore.edit { prefs ->
            prefs[firstLaunchKey] = "true"
        }
    }

    // ====================== 🚨 飞书 Webhook ======================

    suspend fun getFeishuWebhook(): String? {
        return context.dataStore.data.first()[feishuWebhookKey]
    }

    suspend fun setFeishuWebhook(url: String) {
        context.dataStore.edit { prefs ->
            prefs[feishuWebhookKey] = url
        }
    }

    // ====================== 🏠 家位置 ======================

    suspend fun setHomeZone(zone: HomeZone) {
        context.dataStore.edit { prefs ->
            prefs[homeLatitudeKey] = zone.latitude.toString()
            prefs[homeLongitudeKey] = zone.longitude.toString()
            prefs[homeRadiusKey] = zone.radiusMeters.toString()
            if (zone.address != null) prefs[homeAddressKey] = zone.address
        }
    }

    suspend fun getHomeZone(): HomeZone? {
        val prefs = context.dataStore.data.first()
        val lat = prefs[homeLatitudeKey]?.toDoubleOrNull() ?: return null
        val lng = prefs[homeLongitudeKey]?.toDoubleOrNull() ?: return null
        val radius = prefs[homeRadiusKey]?.toFloatOrNull() ?: 200f
        val address = prefs[homeAddressKey]
        return HomeZone(lat, lng, radius, address)
    }

    // ====================== ⚙️ 告警开关 ======================

    suspend fun isAlertEnabled(type: String): Boolean {
        val key = booleanPreferencesKey(alertEnabledPrefix + type)
        return context.dataStore.data.first()[key] ?: true
    }

    suspend fun setAlertEnabled(type: String, enabled: Boolean) {
        val key = booleanPreferencesKey(alertEnabledPrefix + type)
        context.dataStore.edit { prefs -> prefs[key] = enabled }
    }

    /** 获取所有告警开关状态 */
    suspend fun getAllAlertSettings(): Map<String, Boolean> {
        val prefs = context.dataStore.data.first()
        val result = mutableMapOf<String, Boolean>()
        for (alertType in ALERT_TYPES) {
            result[alertType] = prefs[booleanPreferencesKey(alertEnabledPrefix + alertType)] ?: true
        }
        return result
    }

    /** 批量更新告警开关 */
    suspend fun setAllAlertSettings(settings: Map<String, Boolean>) {
        context.dataStore.edit { prefs ->
            for ((type, enabled) in settings) {
                prefs[booleanPreferencesKey(alertEnabledPrefix + type)] = enabled
            }
        }
    }

    // ====================== 💓 心跳 ======================

    suspend fun getLastHeartbeatTime(): Long {
        return context.dataStore.data.first()[lastHeartbeatKey] ?: 0L
    }

    suspend fun updateHeartbeat() {
        context.dataStore.edit { prefs ->
            prefs[lastHeartbeatKey] = System.currentTimeMillis()
        }
    }

    suspend fun getLastBootTime(): Long {
        return context.dataStore.data.first()[lastBootTimeKey] ?: 0L
    }

    suspend fun setLastBootTime(time: Long) {
        context.dataStore.edit { prefs ->
            prefs[lastBootTimeKey] = time
        }
    }

    // ====================== 📱 SIM 卡信息 ======================

    suspend fun getStoredSimInfo(): SimCardInfo? {
        val prefs = context.dataStore.data.first()
        val state = prefs[simStateKey] ?: return null
        return SimCardInfo(
            state = state,
            operator = prefs[simOperatorKey],
            countryIso = prefs[simCountryIsoKey],
            simSerial = prefs[simSerialKey]
        )
    }

    suspend fun setStoredSimInfo(info: SimCardInfo) {
        context.dataStore.edit { prefs ->
            prefs[simStateKey] = info.state
            if (info.operator != null) prefs[simOperatorKey] = info.operator
            if (info.countryIso != null) prefs[simCountryIsoKey] = info.countryIso
            if (info.simSerial != null) prefs[simSerialKey] = info.simSerial
        }
    }

    // ====================== 📶 WiFi 连接记录 ======================

    suspend fun getLastWifiSsid(): String? {
        return context.dataStore.data.first()[lastWifiSsidKey]
    }

    suspend fun setLastWifiInfo(ssid: String, bssid: String?) {
        context.dataStore.edit { prefs ->
            prefs[lastWifiSsidKey] = ssid
            if (bssid != null) prefs[lastWifiBssidKey] = bssid
        }
    }

    suspend fun getLastAlertedWifiSsid(): String? {
        return context.dataStore.data.first()[lastAlertedWifiSsidKey]
    }

    suspend fun setLastAlertedWifiSsid(ssid: String) {
        context.dataStore.edit { prefs ->
            prefs[lastAlertedWifiSsidKey] = ssid
        }
    }

    // ====================== 📡 基站信息 ======================

    suspend fun getStoredCellId(): Long? {
        return context.dataStore.data.first()[cellIdKey]
    }

    suspend fun getStoredCellOperator(): String? {
        return context.dataStore.data.first()[cellOperatorKey]
    }

    suspend fun setCellInfo(info: CellTowerInfo) {
        context.dataStore.edit { prefs ->
            if (info.cellId != null) prefs[cellIdKey] = info.cellId
            if (info.operator != null) prefs[cellOperatorKey] = info.operator
            if (info.tech != null) prefs[cellTechKey] = info.tech
        }
    }

    companion object {
        const val DB_PIN = "db_pin"
        const val WEB_PASSWORD = "web_password"

        // 告警类型标识（用于配置存储）
        const val ALERT_LATE_NIGHT_LEAVE = "late_night_leave"
        const val ALERT_LOW_BATTERY = "low_battery"
        const val ALERT_NO_HEARTBEAT = "no_heartbeat"
        const val ALERT_OFF_HOUR_CALL = "off_hour_call"
        const val ALERT_BOOT = "device_boot"
        const val ALERT_SIM_CHANGE = "sim_change"
        const val ALERT_APP_INSTALL = "app_install"
        const val ALERT_WIFI_CHANGE = "wifi_change"
        const val ALERT_CELL_CHANGE = "cell_change"

        val ALERT_TYPES = listOf(
            ALERT_LATE_NIGHT_LEAVE,
            ALERT_LOW_BATTERY,
            ALERT_NO_HEARTBEAT,
            ALERT_OFF_HOUR_CALL,
            ALERT_BOOT,
            ALERT_SIM_CHANGE,
            ALERT_APP_INSTALL,
            ALERT_WIFI_CHANGE,
            ALERT_CELL_CHANGE
        )
    }
}
