package com.xiangqin.app.service

import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryState {

    data class Info(val level: Int, val isCharging: Boolean, val pluggedType: Int)

    fun get(context: Context): Info {
        val intent = context.registerReceiver(null, IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level < 0 || scale < 0) -1 else (level * 100 / scale)
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return Info(pct, isCharging, plugged)
    }

    /**
     * 返回间隔倍率：
     * - 充电中 → 0.5x（更频繁）
     * - 电量 > 50% → 1x（正常）
     * - 电量 20-50% → 2x（减半频率）
     * - 电量 < 20% → 3x（大幅降低）
     * - 电量 < 10% → 5x（极低功耗）
     */
    fun intervalMultiplier(info: Info): Double = when {
        info.isCharging -> 0.5
        info.level < 0 -> 1.0
        info.level < 10 -> 5.0
        info.level < 20 -> 3.0
        info.level < 50 -> 2.0
        else -> 1.0
    }
}
