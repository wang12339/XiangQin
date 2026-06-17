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

    val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
