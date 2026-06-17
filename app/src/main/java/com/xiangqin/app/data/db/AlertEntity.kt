package com.xiangqin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 🚨 告警记录
 *
 * 存储所有触发的异常告警，包括告警类型、详细信息、触发时间和推送状态。
 * 告警类型（type）:
 *   - late_night_leave     深夜离开常驻区域
 *   - low_battery          低电量未充电
 *   - no_heartbeat         长时间无心跳（可能关机/服务被杀）
 *   - off_hour_call        凌晨通话
 *   - device_boot          设备重启
 */
@Serializable
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,                    // 告警类型标识
    val title: String,                   // 标题（如 "深夜离家"）
    val message: String,                 // 详细信息
    val severity: String = "warning",    // info / warning / critical
    val triggeredTime: Long,             // 触发时间（epoch millis）
    val pushed: Boolean = false,         // 是否已推送
    val pushChannel: String? = null,     // 推送通道（feishu / sms / etc）
    val pushTime: Long? = null,          // 推送时间
    val acknowledged: Boolean = false,   // 是否已确认
    val extraData: String? = null        // 附加 JSON 数据
)

/** 常驻区域（家）的配置 */
@Serializable
data class HomeZone(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 200f,      // 半径 200 米
    val address: String? = null
)
