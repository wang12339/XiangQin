package com.xiangqin.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** 🔔 通知 DAO */
@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY capturedTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int = 50, offset: Int = 0): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE packageName = :pkg ORDER BY capturedTime DESC LIMIT :limit")
    suspend fun getByPackage(pkg: String, limit: Int = 50): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE capturedTime >= :since ORDER BY capturedTime DESC LIMIT :limit")
    suspend fun getSince(since: Long, limit: Int = 50): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications WHERE capturedTime >= :since")
    suspend fun countSince(since: Long): Int

    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Insert
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM notifications WHERE capturedTime < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}
