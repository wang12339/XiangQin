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
import com.xiangqin.app.data.db.SensorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 💪 传感器数据采集器
 *
 * 读取步数计数器 (TYPE_STEP_COUNTER) 和光线传感器 (TYPE_LIGHT) 数据。
 * 这两种传感器无需 BODY_SENSORS 权限即可读取。
 *
 * 提供两种模式：
 * - sync()：一次性采集并持久化
 * - start*Listener()：持续监听回调
 */
class SensorMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // ===================== 权限检查 =====================

    /**
     * 检查身体传感器权限。
     * TYPE_STEP_COUNTER 和 TYPE_LIGHT 无需此权限即可读取，
     * 但保留以备未来接入心率等敏感传感器时使用。
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ===================== 一次性采集 =====================

    /**
     * 采集所有支持的传感器数据并持久化到 Room 数据库。
     *
     * 当前采集：
     * - step_counter：步数计数器（累计步数，自上次开机以来）
     * - light：环境光线强度（lux）
     */
    suspend fun sync(): List<SensorEntity> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<SensorEntity>()

            // 步数计数器（无需 BODY_SENSORS 权限）
            captureOneShot(Sensor.TYPE_STEP_COUNTER, "step_counter")?.let { entity ->
                try {
                    app.database.sensorDao().insert(entity)
                    results.add(entity)
                } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
            }

            // 光线传感器（无需 BODY_SENSORS 权限）
            captureOneShot(Sensor.TYPE_LIGHT, "light")?.let { entity ->
                try {
                    app.database.sensorDao().insert(entity)
                    results.add(entity)
                } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
            }

            results
        }
    }

    /**
     * 通过注册一次性监听器获取单个传感器的当前值。
     *
     * 在 [timeoutMs] 内未收到事件则返回 null。
     */
    private fun captureOneShot(
        sensorType: Int,
        typeName: String,
        timeoutMs: Long = 1000
    ): SensorEntity? {
        return try {
            val sensor = sensorManager.getDefaultSensor(sensorType) ?: return null
            val listener = OneShotListener()
            sensorManager.registerListener(
                listener, sensor, SensorManager.SENSOR_DELAY_NORMAL
            )
            val event = listener.await(timeoutMs)
            sensorManager.unregisterListener(listener)

            event ?: return null
            SensorEntity(
                sensorType = typeName,
                value = event.values[0],
                recordedTime = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    // ===================== 持续监听 =====================

    private var activeStepListener: SensorEventListener? = null
    private var activeLightListener: SensorEventListener? = null

    /** 开始持续监听步数变化，每次变化回调 [onStepChanged] */
    fun startStepListener(onStepChanged: (steps: Float) -> Unit) {
        stopStepListener()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onStepChanged(event.values[0])
            }

            override fun onAccuracyChanged(s: Sensor, accuracy: Int) {}
        }
        activeStepListener = listener
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /** 停止步数监听 */
    fun stopStepListener() {
        activeStepListener?.let { sensorManager.unregisterListener(it) }
        activeStepListener = null
    }

    /** 开始持续监光线变化，每次变化回调 [onLightChanged] */
    fun startLightListener(onLightChanged: (lux: Float) -> Unit) {
        stopLightListener()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onLightChanged(event.values[0])
            }

            override fun onAccuracyChanged(s: Sensor, accuracy: Int) {}
        }
        activeLightListener = listener
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /** 停止光线监听 */
    fun stopLightListener() {
        activeLightListener?.let { sensorManager.unregisterListener(it) }
        activeLightListener = null
    }

    /** 停止所有传感器监听 */
    fun stopAllListeners() {
        stopStepListener()
        stopLightListener()
    }

    // ===================== 内部工具 =====================

    /**
     * 一次性 [SensorEventListener]，通过 wait/notify 阻塞当前线程
     * 直到收到首个 [onSensorChanged] 回调或超时。
     *
     * 线程安全：回调在主线程，await 在 IO 线程，通过 synchronized + volatile 协调。
     */
    private class OneShotListener : SensorEventListener {

        @Volatile
        private var event: SensorEvent? = null

        override fun onSensorChanged(e: SensorEvent) {
            synchronized(this) {
                if (event == null) {
                    event = e
                    (this as Object).notifyAll()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

        /**
         * 阻塞等待传感器事件，最多等待 [timeoutMs] 毫秒。
         * @return 收到的 [SensorEvent]，超时则返回 null
         */
        fun await(timeoutMs: Long): SensorEvent? {
            synchronized(this) {
                if (event != null) return event
                try {
                    (this as Object).wait(timeoutMs)
                } catch (_: InterruptedException) {
                    return null
                }
                return event
            }
        }
    }
}
