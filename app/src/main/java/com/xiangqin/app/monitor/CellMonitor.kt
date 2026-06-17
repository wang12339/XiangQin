package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.SystemLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 📡 基站变化监控器
 *
 * 监听手机基站变化，当基站切换时记录。
 * 使用 TelephonyManager 获取运营商和基站 ID。
 */
class CellMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance

    /** 是否有 READ_PHONE_STATE 权限 */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 获取当前基站信息（简化版本，只获取运营商和大致位置） */
    suspend fun getCellInfo(): CellTowerInfo? {
        if (!hasPermission()) return null

        return withContext(Dispatchers.IO) {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    ?: return@withContext null

                val operator = try { tm.networkOperatorName ?: tm.simOperatorName } catch (_: Exception) { "unknown" }
                val countryIso = try { tm.networkCountryIso } catch (_: Exception) { null }

                // 尝试获取 Cell ID（通过 getAllCellInfo 或 getCellLocation）
                var cellId: Long? = null
                var tech: String? = null

                // 方法1: getAllCellInfo (API 17+)
                try {
                    val allInfo = tm.allCellInfo
                    if (!allInfo.isNullOrEmpty()) {
                        for (info in allInfo) {
                            val identity = info.cellIdentity ?: continue
                            // 通过反射获取 ci/nci 等字段
                            try {
                                val ciMethod = identity.javaClass.getMethod("getCi")
                                cellId = (ciMethod.invoke(identity) as? Int)?.toLong()
                            } catch (_: NoSuchMethodException) {
                                try {
                                    val nciMethod = identity.javaClass.getMethod("getNci")
                                    cellId = (nciMethod.invoke(identity) as? Long)
                                } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
                            }
                            tech = info.javaClass.simpleName.removePrefix("CellInfo")
                            if (cellId != null) break
                        }
                    }
                } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }

                // 方法2: getCellLocation (备用)
                if (cellId == null) {
                    try {
                        @Suppress("DEPRECATION", "PrivateApi")
                        val cellLocation = tm.cellLocation
                        if (cellLocation is android.telephony.gsm.GsmCellLocation) {
                            @Suppress("DEPRECATION")
                            val cid = cellLocation.cid
                            cellId = cid?.toLong()
                            if (cellId == 0L) cellId = null
                            tech = "Gsm"
                        }
                    } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
                }

                CellTowerInfo(
                    operator = operator,
                    countryIso = countryIso,
                    cellId = cellId,
                    tech = tech
                )
            } catch (_: Exception) { null }
        }
    }

    /** 检查基站变化并记录 */
    suspend fun checkCellChange(): Boolean {
        val current = getCellInfo() ?: return false
        val storedCellId = app.dataStore.getStoredCellId()

        // 首次记录
        if (storedCellId == null) {
            app.dataStore.setCellInfo(current)
            return false
        }

        // Cell ID 变化
        if (current.cellId != null && current.cellId != storedCellId) {
            app.dataStore.setCellInfo(current)
            try {
                app.database.systemLogDao().insert(
                    SystemLogEntity(
                        logType = "cell_changed",
                        message = "基站切换: ${current.operator ?: "未知"}, CID=${current.cellId}, 技术=${current.tech ?: "未知"}",
                        createdTime = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
            return true
        }

        return false
    }
}

/** 基站信息 */
data class CellTowerInfo(
    val operator: String?,
    val countryIso: String? = null,
    val cellId: Long?,
    val tech: String?
)
