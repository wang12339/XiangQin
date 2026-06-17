package com.xiangqin.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.IntentFilter
import android.os.Build
import com.xiangqin.app.data.db.AppDatabase
import com.xiangqin.app.data.datastore.AppDataStore
import com.xiangqin.app.receiver.ConnectivityReceiver
import com.xiangqin.app.receiver.KeepAliveReceiver
import com.xiangqin.app.worker.DataCleanupWorker
import com.xiangqin.app.worker.DailyReportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XiangQinApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var dataStore: AppDataStore
        private set

    private var connectivityReceiver: ConnectivityReceiver? = null
    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize DataStore
        dataStore = AppDataStore(this)

        // 密码日志异步写入，不阻塞主线程
        appScope.launch {
            val pw = dataStore.getWebPassword()
            android.util.Log.i("XiangQin", "初始化密码: $pw")
        }

        // Initialize encrypted database (passphrase comes from DataStore/user)
        database = AppDatabase.create(this, dataStore)

        // Create notification channels
        createNotificationChannels()

        // Schedule periodic tasks
        DataCleanupWorker.schedule(this)
        DailyReportWorker.schedule(this)

        // ========== 保活初始化 ==========

        // 1. 注册网络变化监听 Receiver
        registerConnectivityReceiver()

        // 2. 注册 AlarmManager 看门狗（首次启动，确保系统有注册）
        KeepAliveReceiver.scheduleNextAlarm(this)
    }

    private fun registerConnectivityReceiver() {
        val receiver = ConnectivityReceiver()
        connectivityReceiver = receiver
        val filter = IntentFilter(ConnectivityReceiver.ACTION_CONNECTIVITY_CHANGE)
        registerReceiver(receiver, filter)
    }

    override fun onTerminate() {
        super.onTerminate()
        connectivityReceiver?.let { unregisterReceiver(it) }
        connectivityReceiver = null
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val monitoringChannel = NotificationChannel(
            CHANNEL_MONITORING,
            getString(R.string.channel_monitoring),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.channel_monitoring_desc)
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            getString(R.string.channel_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_alert_desc)
        }

        manager.createNotificationChannel(monitoringChannel)
        manager.createNotificationChannel(alertChannel)
    }

    companion object {
        const val CHANNEL_MONITORING = "monitoring"
        const val CHANNEL_ALERT = "alert"

        lateinit var instance: XiangQinApp
            private set
    }
}
