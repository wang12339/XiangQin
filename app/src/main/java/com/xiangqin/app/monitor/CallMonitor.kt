package com.xiangqin.app.monitor

import android.content.Context
import android.provider.CallLog
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.CallEntity

/**
 * 通话记录采集器
 * 读取 CallLog ContentProvider，增量写入 Room
 */
class CallMonitor(private val context: Context) {

    suspend fun sync() {
        val db = XiangQinApp.instance.database
        val lastSync = db.callDao().getLastSyncTime() ?: 0L

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            "${CallLog.Calls.DATE} > ?",
            arrayOf(lastSync.toString()),
            "${CallLog.Calls.DATE} ASC"
        )

        cursor?.use {
            val calls = mutableListOf<CallEntity>()
            while (it.moveToNext()) {
                calls.add(
                    CallEntity(
                        phoneNumber = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: "",
                        callerName = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)),
                        callType = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE)),
                        durationSeconds = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.DURATION)),
                        callTime = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    )
                )
            }
            if (calls.isNotEmpty()) {
                db.callDao().insertAll(calls)
            }
        }
    }
}
