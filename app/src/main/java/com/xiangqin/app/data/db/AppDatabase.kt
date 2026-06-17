package com.xiangqin.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xiangqin.app.data.datastore.AppDataStore
import net.sqlcipher.database.SupportFactory
import java.security.MessageDigest

@Database(
    entities = [
        CallEntity::class, SmsEntity::class, AppUsageEntity::class,
        TrafficEntity::class, SystemLogEntity::class,
        LocationEntity::class, BluetoothDeviceEntity::class,
        WifiNetworkEntity::class, ActivityEntity::class,
        SensorEntity::class, CalendarEventEntity::class,
        MediaFileEntity::class, AccountEntity::class,
        PhotoEntity::class, AudioRecordingEntity::class,
        AlertEntity::class, NotificationEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun callDao(): CallDao
    abstract fun smsDao(): SmsDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun trafficDao(): TrafficDao
    abstract fun systemLogDao(): SystemLogDao

    // 新增
    abstract fun locationDao(): LocationDao
    abstract fun bluetoothDeviceDao(): BluetoothDeviceDao
    abstract fun wifiNetworkDao(): WifiNetworkDao
    abstract fun activityDao(): ActivityDao
    abstract fun sensorDao(): SensorDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun mediaFileDao(): MediaFileDao
    abstract fun accountDao(): AccountDao
    abstract fun photoDao(): PhotoDao
    abstract fun audioRecordingDao(): AudioRecordingDao

    // 🚨 告警
    abstract fun alertDao(): AlertDao

    // 🔔 通知
    abstract fun notificationDao(): NotificationDao

    companion object {
        private const val DB_NAME = "xiangqin.db"

        fun create(context: Context, dataStore: AppDataStore): AppDatabase {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "xiangqin_default"

            val userPin = dataStore.getSync(AppDataStore.DB_PIN) ?: "0000"
            val passphrase = sha256("$androidId:$userPin")

            val factory = SupportFactory(passphrase.toByteArray())

            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .build()
        }

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
