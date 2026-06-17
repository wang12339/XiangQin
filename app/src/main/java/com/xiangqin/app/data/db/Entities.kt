package com.xiangqin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val callerName: String?,
    val callType: Int,           // 1=来电 2=去电 3=未接
    val durationSeconds: Int,
    val callTime: Long,          // epoch millis
    val synced: Boolean = false
)

@Serializable
@Entity(tableName = "sms")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val senderName: String?,
    val body: String,
    val smsType: Int,            // 1=收件箱 2=发件箱
    val receivedTime: Long,      // epoch millis
    val synced: Boolean = false,
    val smsCategory: String? = null  // 'verification','bank','promo','notification','personal'
)

@Serializable
@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String?,
    val totalTimeForeground: Long,  // milliseconds
    val usageDate: String,          // '2026-06-08'
    val lastUsedTime: Long?         // epoch millis
)

@Serializable
@Entity(tableName = "traffic_stats")
data class TrafficEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String?,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val statsDate: String           // '2026-06-08'
)

@Serializable
@Entity(tableName = "system_logs")
data class SystemLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val logType: String,            // 'service_heartbeat' / 'error' / 'permission_change'
    val message: String,
    val createdTime: Long           // epoch millis
)

// ====================== 新增权限对应实体 ======================

/** 📍 位置轨迹 */
@Serializable
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,            // 精度（米）
    val altitude: Double? = null,   // 海拔
    val speed: Float? = null,       // 速度（m/s）
    val bearing: Float? = null,     // 方向角
    val provider: String? = "gps",  // gps/network/passive
    val recordedTime: Long          // epoch millis
)

/** 📡 蓝牙设备 */
@Serializable
@Entity(tableName = "bluetooth_devices")
data class BluetoothDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceName: String?,
    val deviceAddress: String,
    val bondState: Int,             // 10=none 11=bonding 12=bonded
    val rssi: Int? = null,          // 信号强度
    val firstSeen: Long,            // 首次发现时间
    val lastSeen: Long,             // 最后发现时间
    val deviceClass: String? = null // 设备类别
)

/** 📶 WiFi 热点 */
@Serializable
@Entity(tableName = "wifi_networks")
data class WifiNetworkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ssid: String,
    val bssid: String? = null,         // MAC地址
    val rssi: Int = 0,                 // 信号强度(dBm)
    val frequency: Int = 0,            // 频率(MHz)
    val channel: Int = 0,              // 信道号（从频率推算）
    val securityType: String? = null,  // WPA/WPA2/WPA3/Open
    val capabilities: String? = null,  // 原始 capabilities 字符串
    val riskLevel: String = "unknown", // SAFE / LOW / MEDIUM / HIGH / CRITICAL
    val riskNotes: String? = null,     // 风险描述
    val firstSeen: Long,
    val lastSeen: Long
)

/** 🏃 活动识别 */
@Serializable
@Entity(tableName = "activity_records")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val activityType: String,       // 'in_vehicle','on_bicycle','walking','running','still','tilting','unknown'
    val confidence: Int,            // 置信度 0-100
    val recordedTime: Long
)

/** 💪 传感器数据 */
@Serializable
@Entity(tableName = "sensor_data")
data class SensorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sensorType: String,         // 'step_counter','heart_rate','light','pressure','proximity',etc
    val value: Float,
    val recordedTime: Long
)

/** 📅 日历事件 */
@Serializable
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val calendarTitle: String?,
    val eventTitle: String,
    val eventDescription: String? = null,
    val eventLocation: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val allDay: Boolean = false,
    val syncId: Long? = null       // 日历系统的eventId，用于去重
)

/** 🖼️ 媒体文件索引 */
@Serializable
@Entity(tableName = "media_files")
data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val fileSize: Long = 0,
    val mimeType: String? = null,   // image/jpeg, video/mp4, audio/mp3
    val dateAdded: Long,
    val dateModified: Long,
    val mediaType: String,          // 'image','video','audio','document'
    val durationMs: Long? = null,   // 音视频时长
    val latitude: Double? = null,   // 拍摄位置
    val longitude: Double? = null
)

/** 📋 账户信息 */
@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountName: String,
    val accountType: String,        // 'com.google','com.miui','com.android.email',etc
    val firstSeen: Long,
    val lastSeen: Long
)

/** 📸 远程拍照记录 */
@Serializable
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileSize: Long = 0,
    val takenTime: Long,
    val uploaded: Boolean = false,
    val triggerSource: String = "remote" // 'remote','timer'
)

/** 🎤 远程录音记录 */
@Serializable
@Entity(tableName = "audio_recordings")
data class AudioRecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val durationMs: Long = 0,
    val fileSize: Long = 0,
    val recordedTime: Long,
    val triggerSource: String = "remote"
)
