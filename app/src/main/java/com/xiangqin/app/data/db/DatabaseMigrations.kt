package com.xiangqin.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `locations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `latitude` REAL NOT NULL, `longitude` REAL NOT NULL,
                `accuracy` REAL NOT NULL, `altitude` REAL, `speed` REAL,
                `bearing` REAL, `provider` TEXT, `recordedTime` INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `bluetooth_devices` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `deviceName` TEXT, `deviceAddress` TEXT NOT NULL,
                `bondState` INTEGER NOT NULL, `rssi` INTEGER,
                `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL,
                `deviceClass` TEXT)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `wifi_networks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `ssid` TEXT NOT NULL, `bssid` TEXT, `rssi` INTEGER NOT NULL,
                `frequency` INTEGER NOT NULL, `securityType` TEXT,
                `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `activity_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `activityType` TEXT NOT NULL, `confidence` INTEGER NOT NULL,
                `recordedTime` INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `sensor_data` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sensorType` TEXT NOT NULL, `value` REAL NOT NULL,
                `recordedTime` INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `calendar_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `calendarTitle` TEXT, `eventTitle` TEXT NOT NULL,
                `eventDescription` TEXT, `eventLocation` TEXT,
                `startTime` INTEGER NOT NULL, `endTime` INTEGER,
                `allDay` INTEGER NOT NULL DEFAULT 0, `syncId` INTEGER)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `media_files` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `filePath` TEXT NOT NULL, `fileName` TEXT NOT NULL,
                `fileSize` INTEGER NOT NULL DEFAULT 0, `mimeType` TEXT,
                `dateAdded` INTEGER NOT NULL, `dateModified` INTEGER NOT NULL,
                `mediaType` TEXT NOT NULL, `durationMs` INTEGER,
                `latitude` REAL, `longitude` REAL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `accounts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `accountName` TEXT NOT NULL, `accountType` TEXT NOT NULL,
                `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `photos` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `filePath` TEXT NOT NULL, `fileSize` INTEGER NOT NULL DEFAULT 0,
                `takenTime` INTEGER NOT NULL, `uploaded` INTEGER NOT NULL DEFAULT 0,
                `triggerSource` TEXT NOT NULL DEFAULT 'remote')""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `audio_recordings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `filePath` TEXT NOT NULL, `durationMs` INTEGER NOT NULL DEFAULT 0,
                `fileSize` INTEGER NOT NULL DEFAULT 0, `recordedTime` INTEGER NOT NULL,
                `triggerSource` TEXT NOT NULL DEFAULT 'remote')""")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `alerts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL,
                `severity` TEXT NOT NULL DEFAULT 'info', `triggeredTime` INTEGER NOT NULL,
                `acknowledged` INTEGER NOT NULL DEFAULT 0, `pushed` INTEGER NOT NULL DEFAULT 0,
                `pushChannel` TEXT, `pushTime` INTEGER)""")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `notifications` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `packageName` TEXT NOT NULL, `appName` TEXT,
                `title` TEXT, `text` TEXT, `postTime` INTEGER NOT NULL,
                `capturedTime` INTEGER NOT NULL, `read` INTEGER NOT NULL DEFAULT 0)""")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `wifi_networks` ADD COLUMN `channel` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `wifi_networks` ADD COLUMN `capabilities` TEXT")
            db.execSQL("ALTER TABLE `wifi_networks` ADD COLUMN `riskLevel` TEXT NOT NULL DEFAULT 'unknown'")
            db.execSQL("ALTER TABLE `wifi_networks` ADD COLUMN `riskNotes` TEXT")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 添加索引优化查询性能
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_callTime` ON `calls` (`callTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_phoneNumber` ON `calls` (`phoneNumber`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_receivedTime` ON `sms` (`receivedTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_phoneNumber` ON `sms` (`phoneNumber`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_usage_usageDate` ON `app_usage` (`usageDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_traffic_stats_statsDate` ON `traffic_stats` (`statsDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_system_logs_createdTime` ON `system_logs` (`createdTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_locations_recordedTime` ON `locations` (`recordedTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bluetooth_devices_lastSeen` ON `bluetooth_devices` (`lastSeen`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bluetooth_devices_deviceAddress` ON `bluetooth_devices` (`deviceAddress`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wifi_networks_lastSeen` ON `wifi_networks` (`lastSeen`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wifi_networks_bssid` ON `wifi_networks` (`bssid`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_records_recordedTime` ON `activity_records` (`recordedTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sensor_data_recordedTime` ON `sensor_data` (`recordedTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sensor_data_sensorType` ON `sensor_data` (`sensorType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_startTime` ON `calendar_events` (`startTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_syncId` ON `calendar_events` (`syncId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_dateAdded` ON `media_files` (`dateAdded`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_mediaType` ON `media_files` (`mediaType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_filePath` ON `media_files` (`filePath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_lastSeen` ON `accounts` (`lastSeen`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_accountName_accountType` ON `accounts` (`accountName`, `accountType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_takenTime` ON `photos` (`takenTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_recordings_recordedTime` ON `audio_recordings` (`recordedTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_triggeredTime` ON `alerts` (`triggeredTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_type` ON `alerts` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_pushed` ON `alerts` (`pushed`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_postTime` ON `notifications` (`postTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName` ON `notifications` (`packageName`)")
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
