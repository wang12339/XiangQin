package com.xiangqin.app.monitor

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 实时 ContentObserver 监听通话记录和短信变化
 * 替代 30 秒轮询，更加省电且实时
 */
class CallSmsContentObserver(
    private val context: Context,
    private val onCallChanged: suspend () -> Unit,
    private val onSmsChanged: suspend () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // 去抖：避免短时间内重复触发
    private var lastCallSync = 0L
    private var lastSmsSync = 0L
    private val debounceMs = 2_000L

    private val callObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            val now = System.currentTimeMillis()
            if (now - lastCallSync < debounceMs) return
            lastCallSync = now
            scope.launch {
                delay(500) // 等待数据库写入完成
                onCallChanged()
            }
        }
    }

    private val smsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            val now = System.currentTimeMillis()
            if (now - lastSmsSync < debounceMs) return
            lastSmsSync = now
            scope.launch {
                delay(500)
                onSmsChanged()
            }
        }
    }

    fun register() {
        try {
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callObserver
            )
        } catch (_: SecurityException) {
            // 没有权限
        }

        try {
            context.contentResolver.registerContentObserver(
                Telephony.Sms.Inbox.CONTENT_URI,
                true,
                smsObserver
            )
            context.contentResolver.registerContentObserver(
                Telephony.Sms.Sent.CONTENT_URI,
                true,
                smsObserver
            )
        } catch (_: SecurityException) {}
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(callObserver)
            context.contentResolver.unregisterContentObserver(smsObserver)
        } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
    }
}
