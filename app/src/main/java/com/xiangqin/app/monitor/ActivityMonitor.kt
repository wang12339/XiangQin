package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.ActivityEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🏃 活动识别采集器
 * 基于加速度传感器粗略判断活动状态：
 * - 无明显加速度 → still
 * - 低频率震荡 → walking
 * - 高频率震荡 → running
 * - 平稳移动 → in_vehicle
 *
 * 也可以尝试通过 Play Services 的 ActivityRecognition API，
 * 但本版本使用传感器方案，无需额外依赖
 */
class ActivityMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance

    /** 是否有活动识别权限 */
    fun hasPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 10 以下不需要运行时权限
        }
    }

    /**
     * 采集一次活动状态
     * 通过加速度传感器的短期样本粗略判断运动状态
     */
    suspend fun capture(): ActivityEntity? {
        if (!hasPermission()) return null

        return withContext(Dispatchers.IO) {
            try {
                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    ?: return@withContext null

                // 采集加速度样本
                val samples = mutableListOf<FloatArray>()
                val latch = java.util.concurrent.CountDownLatch(1)

                val listener = object : SensorEventListener {
                    var count = 0
                    override fun onSensorChanged(event: SensorEvent) {
                        if (count < 8) { // 采集8个样本（原20个，省电）
                            samples.add(event.values.clone())
                            count++
                        } else {
                            latch.countDown()
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                sensorManager.registerListener(
                    listener, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
                )

                // 等待样本采集完成或超时
                latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                sensorManager.unregisterListener(listener)

                if (samples.isEmpty()) return@withContext null

                // 分析加速度变化
                val magnitudeChanges = samples.map { vals ->
                    Math.sqrt((vals[0] * vals[0] + vals[1] * vals[1] + vals[2] * vals[2]).toDouble())
                }

                val avgMagnitude = magnitudeChanges.average()
                val variance = magnitudeChanges.map { (it - avgMagnitude) * (it - avgMagnitude) }.average()
                val stdDev = Math.sqrt(variance)

                // 判断活动类型（基于经验阈值）
                val activityType = when {
                    stdDev < 0.3 -> "still"
                    stdDev < 1.0 -> "walking"
                    stdDev < 2.5 -> "running"
                    avgMagnitude > 10.0 && stdDev < 0.8 -> "in_vehicle"
                    else -> "unknown"
                }

                val confidence = when (activityType) {
                    "still" -> minOf(100, (100 - (stdDev * 100)).toInt())
                    "walking" -> minOf(90, (stdDev * 80).toInt() + 20)
                    "running" -> minOf(85, (stdDev * 30).toInt() + 30)
                    "in_vehicle" -> 60
                    else -> 40
                }

                ActivityEntity(
                    activityType = activityType,
                    confidence = confidence.coerceIn(0, 100),
                    recordedTime = System.currentTimeMillis()
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /** 采集并存储 */
    suspend fun sync(): ActivityEntity? {
        val activity = capture() ?: return null
        try {
            app.database.activityDao().insert(activity)
        } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
        return activity
    }
}
