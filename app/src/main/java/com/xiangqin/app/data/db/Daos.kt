package com.xiangqin.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY callTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getCalls(limit: Int = 50, offset: Int = 0): List<CallEntity>

    @Query("SELECT * FROM calls WHERE callTime >= :from AND callTime <= :to ORDER BY callTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getCallsByDate(from: Long, to: Long, limit: Int = 50, offset: Int = 0): List<CallEntity>

    @Query("SELECT COUNT(*) FROM calls WHERE callTime >= :from AND callTime <= :to")
    suspend fun countByDate(from: Long, to: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(calls: List<CallEntity>)

    @Query("SELECT MAX(callTime) FROM calls")
    suspend fun getLastSyncTime(): Long?

    @Query("DELETE FROM calls WHERE callTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms ORDER BY receivedTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getSms(limit: Int = 50, offset: Int = 0): List<SmsEntity>

    @Query("SELECT * FROM sms WHERE receivedTime >= :from AND receivedTime <= :to ORDER BY receivedTime DESC")
    suspend fun getSmsByDate(from: Long, to: Long): List<SmsEntity>

    @Query("SELECT COUNT(*) FROM sms WHERE receivedTime >= :from AND receivedTime <= :to")
    suspend fun countByDate(from: Long, to: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sms: List<SmsEntity>)

    @Query("SELECT MAX(receivedTime) FROM sms")
    suspend fun getLastSyncTime(): Long?

    @Query("DELETE FROM sms WHERE receivedTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage WHERE usageDate = :date ORDER BY totalTimeForeground DESC")
    suspend fun getByDate(date: String): List<AppUsageEntity>

    @Query("SELECT * FROM app_usage WHERE usageDate >= :from AND usageDate <= :to ORDER BY usageDate ASC, totalTimeForeground DESC")
    suspend fun getByDateRange(from: String, to: String): List<AppUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<AppUsageEntity>)

    @Query("DELETE FROM app_usage WHERE usageDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM app_usage WHERE usageDate < :before")
    suspend fun deleteOlderThan(before: String)
}

@Dao
interface TrafficDao {
    @Query("SELECT * FROM traffic_stats WHERE statsDate = :date ORDER BY (rxBytes + txBytes) DESC")
    suspend fun getByDate(date: String): List<TrafficEntity>

    @Query("SELECT SUM(rxBytes) as rx, SUM(txBytes) as tx FROM traffic_stats WHERE statsDate = :date")
    suspend fun getTotalByDate(date: String): TrafficSummary

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(traffic: List<TrafficEntity>)

    @Query("DELETE FROM traffic_stats WHERE statsDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM traffic_stats WHERE statsDate < :before")
    suspend fun deleteOlderThan(before: String)
}

@Dao
interface SystemLogDao {
    @Query("SELECT * FROM system_logs ORDER BY createdTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<SystemLogEntity>

    @Insert
    suspend fun insert(log: SystemLogEntity)

    @Query("DELETE FROM system_logs WHERE createdTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 📍 位置 ======================

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY recordedTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE recordedTime >= :from AND recordedTime <= :to ORDER BY recordedTime ASC")
    suspend fun getByDateRange(from: Long, to: Long): List<LocationEntity>

    @Query("SELECT COUNT(*) FROM locations WHERE recordedTime >= :from AND recordedTime <= :to")
    suspend fun countByDateRange(from: Long, to: Long): Int

    @Insert
    suspend fun insert(location: LocationEntity)

    @Insert
    suspend fun insertAll(locations: List<LocationEntity>)

    @Query("DELETE FROM locations WHERE recordedTime < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM locations ORDER BY recordedTime DESC LIMIT 1")
    suspend fun getLastLocation(): LocationEntity?
}

// ====================== 📡 蓝牙 ======================

@Dao
interface BluetoothDeviceDao {
    @Query("SELECT * FROM bluetooth_devices ORDER BY lastSeen DESC")
    suspend fun getAll(): List<BluetoothDeviceEntity>

    @Query("SELECT * FROM bluetooth_devices ORDER BY lastSeen DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<BluetoothDeviceEntity>

    @Query("SELECT * FROM bluetooth_devices WHERE deviceAddress = :address LIMIT 1")
    suspend fun getByAddress(address: String): BluetoothDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: BluetoothDeviceEntity)

    @Update
    suspend fun update(device: BluetoothDeviceEntity)

    @Query("DELETE FROM bluetooth_devices WHERE lastSeen < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 📶 WiFi ======================

@Dao
interface WifiNetworkDao {
    @Query("SELECT * FROM wifi_networks ORDER BY lastSeen DESC")
    suspend fun getAll(): List<WifiNetworkEntity>

    @Query("SELECT * FROM wifi_networks ORDER BY lastSeen DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<WifiNetworkEntity>

    @Query("SELECT * FROM wifi_networks WHERE bssid = :bssid LIMIT 1")
    suspend fun getByBssid(bssid: String): WifiNetworkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(network: WifiNetworkEntity)

    @Update
    suspend fun update(network: WifiNetworkEntity)

    @Query("DELETE FROM wifi_networks WHERE lastSeen < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 🏃 活动 ======================

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_records ORDER BY recordedTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<ActivityEntity>

    @Query("SELECT * FROM activity_records WHERE recordedTime >= :from AND recordedTime <= :to ORDER BY recordedTime DESC")
    suspend fun getByDateRange(from: Long, to: Long): List<ActivityEntity>

    @Insert
    suspend fun insert(activity: ActivityEntity)

    @Query("DELETE FROM activity_records WHERE recordedTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 💪 传感器 ======================

@Dao
interface SensorDao {
    @Query("SELECT * FROM sensor_data ORDER BY recordedTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<SensorEntity>

    @Query("SELECT * FROM sensor_data WHERE sensorType = :type ORDER BY recordedTime DESC LIMIT :limit")
    suspend fun getByType(type: String, limit: Int = 50): List<SensorEntity>

    @Query("SELECT * FROM sensor_data WHERE recordedTime >= :from AND recordedTime <= :to ORDER BY recordedTime ASC")
    suspend fun getByDateRange(from: Long, to: Long): List<SensorEntity>

    @Insert
    suspend fun insert(sensor: SensorEntity)

    @Query("DELETE FROM sensor_data WHERE recordedTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 📅 日历 ======================

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE startTime >= :from AND startTime <= :to ORDER BY startTime ASC")
    suspend fun getByDateRange(from: Long, to: Long): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: Long): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 🖼️ 媒体 ======================

@Dao
interface MediaFileDao {
    @Query("SELECT * FROM media_files ORDER BY dateAdded DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int = 50, offset: Int = 0): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE mediaType = :type ORDER BY dateAdded DESC LIMIT :limit OFFSET :offset")
    suspend fun getByType(type: String, limit: Int = 50, offset: Int = 0): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): MediaFileEntity?

    @Query("SELECT COUNT(*) FROM media_files WHERE mediaType = :type")
    suspend fun countByType(type: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(file: MediaFileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(files: List<MediaFileEntity>)

    @Query("DELETE FROM media_files WHERE dateAdded < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM media_files ORDER BY dateAdded DESC")
    suspend fun getAll(): List<MediaFileEntity>

    @Query("DELETE FROM media_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM media_files")
    suspend fun deleteAll()

    @Query("SELECT * FROM media_files WHERE filePath = :path")
    suspend fun getAllByPath(path: String): List<MediaFileEntity>
}

// ====================== 📋 账户 ======================

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY lastSeen DESC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE accountName = :name AND accountType = :type LIMIT 1")
    suspend fun getByKey(name: String, type: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("DELETE FROM accounts WHERE lastSeen < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 📸 拍照 ======================

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY takenTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<PhotoEntity>

    @Insert
    suspend fun insert(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE takenTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 🎤 录音 ======================

@Dao
interface AudioRecordingDao {
    @Query("SELECT * FROM audio_recordings ORDER BY recordedTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<AudioRecordingEntity>

    @Insert
    suspend fun insert(recording: AudioRecordingEntity)

    @Query("DELETE FROM audio_recordings WHERE recordedTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ====================== 🚨 告警 ======================

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY triggeredTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getAlerts(limit: Int = 50, offset: Int = 0): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE pushed = 0 ORDER BY triggeredTime ASC")
    suspend fun getUnpushedAlerts(): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE type = :type ORDER BY triggeredTime DESC LIMIT :limit")
    suspend fun getByType(type: String, limit: Int = 20): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE triggeredTime >= :since ORDER BY triggeredTime DESC")
    suspend fun getRecent(since: Long): List<AlertEntity>

    @Query("SELECT COUNT(*) FROM alerts WHERE triggeredTime >= :since AND type = :type")
    suspend fun countByTypeSince(since: Long, type: String): Int

    @Query("SELECT COUNT(*) FROM alerts WHERE triggeredTime >= :since")
    suspend fun countSince(since: Long): Int

    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Query("UPDATE alerts SET pushed = 1, pushChannel = :channel, pushTime = :pushTime WHERE id = :id")
    suspend fun markPushed(id: Long, channel: String, pushTime: Long)

    @Query("UPDATE alerts SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)

    @Query("DELETE FROM alerts WHERE triggeredTime < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}
