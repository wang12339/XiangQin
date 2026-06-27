package com.xiangqin.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xiangqin.app.data.datastore.AppDataStore
import net.sqlcipher.database.SupportFactory
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256

        fun create(context: Context, dataStore: AppDataStore): AppDatabase {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "xiangqin_default"

            val userPin = dataStore.getSync(AppDataStore.DB_PIN) ?: run {
                // 首次启动，生成随机强密码（32 位字母数字）
                val rng = java.security.SecureRandom()
                val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                val pin = (1..32).map { chars[rng.nextInt(chars.length)] }.joinToString("")
                dataStore.saveSync(AppDataStore.DB_PIN, pin)
                android.util.Log.i("XiangQin/DB", "数据库密钥已自动生成并保存")
                pin
            }
            val keyInput = "$androidId:$userPin"

            // 新安装使用 PBKDF2，已有数据库兼容 SHA-256
            val passphrase = deriveKeyPBKDF2(keyInput, androidId)
            val legacyPassphrase = sha256(keyInput)

            val factory = SupportFactory(passphrase.toByteArray())

            val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .build()

            // 尝试打开数据库，如果失败则回退到旧版 SHA-256 密钥
            return try {
                db.openHelper.readableDatabase
                db
            } catch (e: Exception) {
                android.util.Log.w("XiangQin/DB", "PBKDF2 密钥打开失败，回退到 SHA-256")
                db.close()
                val legacyFactory = SupportFactory(legacyPassphrase.toByteArray())
                val legacyDb = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                    .openHelperFactory(legacyFactory)
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    .build()
                try {
                    legacyDb.openHelper.readableDatabase
                    legacyDb
                } catch (e2: Exception) {
                    android.util.Log.w("XiangQin/DB", "SHA-256 也失败，删除旧数据库重建")
                    legacyDb.close()
                    context.deleteDatabase(DB_NAME)
                    // 用新 PIN 重新创建
                    val freshFactory = SupportFactory(passphrase.toByteArray())
                    Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                        .openHelperFactory(freshFactory)
                        .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                        .build()
                }
            }
        }

        /**
         * PBKDF2 密钥派生 — 比单次 SHA-256 更抗暴力破解。
         * 注意：使用 androidId 作为 salt，同一设备上 salt 固定，
         * 但迭代次数 (10000) 大幅增加了暴力破解成本。
         */
        private fun deriveKeyPBKDF2(input: String, deviceId: String = ""): String {
            val salt = java.security.MessageDigest.getInstance("SHA-256")
                .digest(("xiangqin_salt:$deviceId").toByteArray())
                .copyOf(16) // 使用设备 ID 派生唯一 salt
            val spec = PBEKeySpec(input.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val key = factory.generateSecret(spec).encoded
            return key.joinToString("") { "%02x".format(it) }
        }

        /** 兼容旧版 SHA-256 派生（用于迁移期间） */
        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
