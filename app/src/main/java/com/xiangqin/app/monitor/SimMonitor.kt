package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 📱 SIM 卡监控器
 *
 * 监听 SIM 卡状态变化，检测 SIM 卡拔出、插入/更换等操作。
 * 核心安全功能 — SIM 卡被拔出或更换时触发告警。
 */
class SimMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance

    /** 是否有 READ_PHONE_STATE 权限 */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取当前 SIM 卡信息（需要 READ_PHONE_STATE 权限）
     * 返回 SIM 信息字符串，用于比对是否发生变化
     */
    suspend fun getSimInfo(): SimCardInfo? {
        if (!hasPermission()) return null

        return withContext(Dispatchers.IO) {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    ?: return@withContext null

                val simState = tm.simState
                if (simState != TelephonyManager.SIM_STATE_READY) {
                    return@withContext SimCardInfo(
                        state = "absent",
                        operator = null,
                        countryIso = null,
                        simSerial = null
                    )
                }

                val operator = try { tm.simOperatorName ?: tm.networkOperatorName } catch (_: Exception) { null }
                val countryIso = try { tm.simCountryIso } catch (_: Exception) { null }
                // 获取 SIM 序列号（部分设备和 ROM 可能抛出各种异常）
                val simSerial = try { tm.simSerialNumber } catch (_: Exception) { null }

                SimCardInfo(
                    state = "ready",
                    operator = operator,
                    countryIso = countryIso,
                    simSerial = simSerial
                )
            } catch (_: Exception) { null }
        }
    }

    /** 检查 SIM 卡是否发生变化并与存储的信息比对 */
    suspend fun checkSimChange(): SimChangeResult {
        val currentInfo = getSimInfo() ?: return SimChangeResult.NoChange
        val storedInfo = app.dataStore.getStoredSimInfo()

        // 没有存储信息（首次启动/刚重置）→ 存起来不告警
        if (storedInfo == null) {
            app.dataStore.setStoredSimInfo(currentInfo)
            return SimChangeResult.NoChange
        }

        // 状态从 ready → absent：SIM 被拔出
        if (storedInfo.state == "ready" && currentInfo.state == "absent") {
            return SimChangeResult.SimRemoved(storedInfo)
        }

        // 状态从 absent → ready：SIM 被插入
        if (storedInfo.state == "absent" && currentInfo.state == "ready") {
            app.dataStore.setStoredSimInfo(currentInfo)
            return SimChangeResult.SimInserted(currentInfo)
        }

        // 都是 ready 但运营商/序列号不同：SIM 卡被更换
        if (currentInfo.state == "ready" && storedInfo.state == "ready") {
            val serialChanged = currentInfo.simSerial != null &&
                    storedInfo.simSerial != null &&
                    currentInfo.simSerial != storedInfo.simSerial
            val operatorChanged = currentInfo.operator != null &&
                    storedInfo.operator != null &&
                    currentInfo.operator != storedInfo.operator

            if (serialChanged || operatorChanged) {
                app.dataStore.setStoredSimInfo(currentInfo)
                return SimChangeResult.SimReplaced(storedInfo, currentInfo)
            }
        }

        return SimChangeResult.NoChange
    }

    /** 初始绑定 SIM 信息存储（首次启动时调用） */
    suspend fun initSimInfo() {
        val info = getSimInfo() ?: return
        if (app.dataStore.getStoredSimInfo() == null) {
            app.dataStore.setStoredSimInfo(info)
        }
    }
}

/** SIM 卡信息 */
data class SimCardInfo(
    val state: String,        // "ready" / "absent"
    val operator: String?,    // 运营商名称
    val countryIso: String?,  // 国家代码
    val simSerial: String?    // 序列号（部分设备可用）
)

/** SIM 卡变化结果 */
sealed class SimChangeResult {
    data object NoChange : SimChangeResult()
    data class SimRemoved(val oldInfo: SimCardInfo) : SimChangeResult()
    data class SimInserted(val newInfo: SimCardInfo) : SimChangeResult()
    data class SimReplaced(val oldInfo: SimCardInfo, val newInfo: SimCardInfo) : SimChangeResult()
}
