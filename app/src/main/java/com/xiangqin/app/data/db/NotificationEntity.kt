package com.xiangqin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 🔔 通知记录 */
@Serializable
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,         // 发送通知的应用包名
    val appName: String? = null,     // 应用名称
    val title: String? = null,       // 通知标题
    val text: String? = null,        // 通知内容
    val postTime: Long,              // 通知发出时间
    val capturedTime: Long,          // 捕获时间
    val read: Boolean = false        // 是否已读
)
