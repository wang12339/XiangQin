package com.xiangqin.app.data.db

import kotlinx.serialization.Serializable

@Serializable
data class TrafficSummary(
    val rx: Long = 0,
    val tx: Long = 0
)

@Serializable
data class StatsSummary(
    val date: String,
    val callCount: Int,
    val smsCount: Int,
    val totalRx: Long,
    val totalTx: Long,
    val latestCalls: List<CallEntity>,
    val latestSms: List<SmsEntity>,
    val serviceUptime: Long = 0,
    val locationCount: Int = 0,
    val alertToday: Int = 0,
    val localIp: String? = null,
    val port: Int = 8080
)

@Serializable
data class TrafficResponse(
    val items: List<TrafficEntity>,
    val totalRx: Long,
    val totalTx: Long
)

@Serializable
data class SettingsResponse(
    val hostname: String,
    val localIp: String,
    val port: Int,
    val serviceRunning: Boolean,
    val deviceAdminActive: Boolean = false
)
