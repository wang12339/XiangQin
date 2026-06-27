package com.xiangqin.app.data.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.xiangqin.app.data.db.HomeZone
import com.xiangqin.app.monitor.SimCardInfo
import com.xiangqin.app.monitor.CellTowerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class AppDataStore(private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "xiangqin_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val plainPrefs by lazy {
        context.getSharedPreferences("xiangqin_plain_prefs", Context.MODE_PRIVATE)
    }

    // Sensitive keys → EncryptedSharedPreferences
    private val webPasswordKey = "web_password"
    private val dbPinKey = "db_pin"
    private val relayTokenKey = "relay_token"

    // Non-sensitive keys → plain SharedPreferences
    private val firstLaunchKey = "first_launch_done"
    private val feishuWebhookKey = "feishu_webhook"
    private val homeLatitudeKey = "home_latitude"
    private val homeLongitudeKey = "home_longitude"
    private val homeRadiusKey = "home_radius"
    private val homeAddressKey = "home_address"
    private val alertEnabledPrefix = "alert_enabled_"
    private val simStateKey = "sim_state"
    private val simOperatorKey = "sim_operator"
    private val simCountryIsoKey = "sim_country_iso"
    private val simSerialKey = "sim_serial"
    private val lastHeartbeatKey = "last_heartbeat"
    private val lastBootTimeKey = "last_boot_time"
    private val lastWifiSsidKey = "last_wifi_ssid"
    private val lastWifiBssidKey = "last_wifi_bssid"
    private val lastAlertedWifiSsidKey = "last_alerted_wifi_ssid"
    private val cellIdKey = "cell_id"
    private val cellOperatorKey = "cell_operator"
    private val cellTechKey = "cell_tech"
    private val relayUrlKey = "relay_url"
    private val webPortKey = "web_port"

    private fun getSensitive(key: String): String? {
        return when (key) {
            DB_PIN -> encryptedPrefs.getString(dbPinKey, null)
            WEB_PASSWORD -> encryptedPrefs.getString(webPasswordKey, null)
            else -> null
        }
    }

    private fun saveSensitive(key: String, value: String) {
        when (key) {
            DB_PIN -> encryptedPrefs.edit().putString(dbPinKey, value).apply()
            WEB_PASSWORD -> encryptedPrefs.edit().putString(webPasswordKey, value).apply()
        }
    }

    fun getSync(key: String): String? {
        return try { getSensitive(key) } catch (_: Exception) { null }
    }

    fun saveSync(key: String, value: String) {
        try { saveSensitive(key, value) } catch (_: Exception) { }
    }

    suspend fun getWebPassword(): String {
        val stored = encryptedPrefs.getString(webPasswordKey, null)
        if (stored != null) return stored
        val pw = generateDefaultPassword()
        encryptedPrefs.edit().putString(webPasswordKey, pw).apply()
        android.util.Log.i("XiangQin", "Web 密码已初始化")
        return pw
    }

    private fun generateDefaultPassword(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#\$%^&*"
        val rng = java.security.SecureRandom()
        return (1..12).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun setWebPassword(password: String) {
        encryptedPrefs.edit().putString(webPasswordKey, password).apply()
    }

    suspend fun getDbPin(): String {
        val stored = encryptedPrefs.getString(dbPinKey, null)
        if (stored != null) return stored
        val pin = generateStrongPin()
        encryptedPrefs.edit().putString(dbPinKey, pin).apply()
        return pin
    }

    private fun generateStrongPin(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rng = java.security.SecureRandom()
        return (1..32).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun setDbPin(pin: String) {
        encryptedPrefs.edit().putString(dbPinKey, pin).apply()
    }

    fun observeWebPassword(): Flow<String> {
        return kotlinx.coroutines.flow.flow {
            while (true) {
                emit(encryptedPrefs.getString(webPasswordKey, "") ?: "")
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    suspend fun isFirstLaunch(): Boolean {
        return plainPrefs.getString(firstLaunchKey, null) != "true"
    }

    suspend fun markFirstLaunchDone() {
        plainPrefs.edit().putString(firstLaunchKey, "true").apply()
    }

    // ====================== Feishu Webhook ======================

    suspend fun getFeishuWebhook(): String? {
        return plainPrefs.getString(feishuWebhookKey, null)
    }

    suspend fun setFeishuWebhook(url: String) {
        plainPrefs.edit().putString(feishuWebhookKey, url).apply()
    }

    // ====================== Home Zone ======================

    suspend fun setHomeZone(zone: HomeZone) {
        plainPrefs.edit().apply {
            putString(homeLatitudeKey, zone.latitude.toString())
            putString(homeLongitudeKey, zone.longitude.toString())
            putString(homeRadiusKey, zone.radiusMeters.toString())
            if (zone.address != null) putString(homeAddressKey, zone.address)
        }.apply()
    }

    suspend fun getHomeZone(): HomeZone? {
        val lat = plainPrefs.getString(homeLatitudeKey, null)?.toDoubleOrNull() ?: return null
        val lng = plainPrefs.getString(homeLongitudeKey, null)?.toDoubleOrNull() ?: return null
        val radius = plainPrefs.getString(homeRadiusKey, null)?.toFloatOrNull() ?: 200f
        val address = plainPrefs.getString(homeAddressKey, null)
        return HomeZone(lat, lng, radius, address)
    }

    // ====================== Alert Settings ======================

    suspend fun isAlertEnabled(type: String): Boolean {
        return plainPrefs.getBoolean(alertEnabledPrefix + type, true)
    }

    suspend fun setAlertEnabled(type: String, enabled: Boolean) {
        plainPrefs.edit().putBoolean(alertEnabledPrefix + type, enabled).apply()
    }

    suspend fun getAllAlertSettings(): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        for (alertType in ALERT_TYPES) {
            result[alertType] = plainPrefs.getBoolean(alertEnabledPrefix + alertType, true)
        }
        return result
    }

    suspend fun setAllAlertSettings(settings: Map<String, Boolean>) {
        plainPrefs.edit().apply {
            for ((type, enabled) in settings) {
                putBoolean(alertEnabledPrefix + type, enabled)
            }
        }.apply()
    }

    // ====================== Heartbeat ======================

    suspend fun getLastHeartbeatTime(): Long {
        return plainPrefs.getLong(lastHeartbeatKey, 0L)
    }

    suspend fun updateHeartbeat() {
        plainPrefs.edit().putLong(lastHeartbeatKey, System.currentTimeMillis()).apply()
    }

    suspend fun getLastBootTime(): Long {
        return plainPrefs.getLong(lastBootTimeKey, 0L)
    }

    suspend fun setLastBootTime(time: Long) {
        plainPrefs.edit().putLong(lastBootTimeKey, time).apply()
    }

    // ====================== SIM Info ======================

    suspend fun getStoredSimInfo(): SimCardInfo? {
        val state = plainPrefs.getString(simStateKey, null) ?: return null
        return SimCardInfo(
            state = state,
            operator = plainPrefs.getString(simOperatorKey, null),
            countryIso = plainPrefs.getString(simCountryIsoKey, null),
            simSerial = plainPrefs.getString(simSerialKey, null)
        )
    }

    suspend fun setStoredSimInfo(info: SimCardInfo) {
        plainPrefs.edit().apply {
            putString(simStateKey, info.state)
            if (info.operator != null) putString(simOperatorKey, info.operator)
            if (info.countryIso != null) putString(simCountryIsoKey, info.countryIso)
            if (info.simSerial != null) putString(simSerialKey, info.simSerial)
        }.apply()
    }

    // ====================== WiFi ======================

    suspend fun getLastWifiSsid(): String? {
        return plainPrefs.getString(lastWifiSsidKey, null)
    }

    suspend fun setLastWifiInfo(ssid: String, bssid: String?) {
        plainPrefs.edit().apply {
            putString(lastWifiSsidKey, ssid)
            if (bssid != null) putString(lastWifiBssidKey, bssid)
        }.apply()
    }

    suspend fun getLastAlertedWifiSsid(): String? {
        return plainPrefs.getString(lastAlertedWifiSsidKey, null)
    }

    suspend fun setLastAlertedWifiSsid(ssid: String) {
        plainPrefs.edit().putString(lastAlertedWifiSsidKey, ssid).apply()
    }

    // ====================== Cell Tower ======================

    suspend fun getStoredCellId(): Long? {
        val v = plainPrefs.getLong(cellIdKey, -1L)
        return if (v == -1L) null else v
    }

    suspend fun getStoredCellOperator(): String? {
        return plainPrefs.getString(cellOperatorKey, null)
    }

    suspend fun setCellInfo(info: CellTowerInfo) {
        plainPrefs.edit().apply {
            if (info.cellId != null) putLong(cellIdKey, info.cellId)
            if (info.operator != null) putString(cellOperatorKey, info.operator)
            if (info.tech != null) putString(cellTechKey, info.tech)
        }.apply()
    }

    // ====================== Relay ======================

    suspend fun getRelayUrl(): String? {
        return plainPrefs.getString(relayUrlKey, null)
    }

    suspend fun setRelayUrl(url: String) {
        plainPrefs.edit().putString(relayUrlKey, url).apply()
    }

    suspend fun getRelayToken(): String? {
        return encryptedPrefs.getString(relayTokenKey, null)
    }

    suspend fun setRelayToken(token: String) {
        encryptedPrefs.edit().putString(relayTokenKey, token).apply()
    }

    // ====================== Web Port ======================

    suspend fun getWebPort(): Int {
        return plainPrefs.getInt(webPortKey, 8080)
    }

    suspend fun setWebPort(port: Int) {
        require(port in 1024..65535) { "端口必须在 1024-65535 范围内" }
        plainPrefs.edit().putInt(webPortKey, port).apply()
    }

    companion object {
        const val DB_PIN = "db_pin"
        const val WEB_PASSWORD = "web_password"

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
