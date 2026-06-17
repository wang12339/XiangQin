package com.xiangqin.app.monitor

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.CallEntity
import com.xiangqin.app.server.EventBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 📞 实时通话状态监听器
 * 使用 PhoneStateListener / TelephonyCallback 实时监听通话状态
 */
class CallStateMonitor(private val context: Context) {

    companion object {
        private const val TAG = "CallStateMonitor"
        const val CALL_STATE_IDLE = 0
        const val CALL_STATE_RINGING = 1
        const val CALL_STATE_OFFHOOK = 2
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastState = CALL_STATE_IDLE
    private var lastNumber: String? = null
    private var callStartTime: Long = 0
    private var ringingStartTime: Long = 0
    private var registered = false

    private val telephonyManager: TelephonyManager?
        get() = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun register() {
        if (registered || !hasPermission()) return
        val tm = telephonyManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.registerTelephonyCallback(context.mainExecutor, object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateChange(state)
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                tm.listen(object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        lastNumber = phoneNumber
                        handleCallStateChange(state)
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
            registered = true
            Log.d(TAG, "CallStateMonitor registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register call state listener", e)
        }
    }

    fun unregister() {
        if (!registered) return
        try {
            val tm = telephonyManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // TelephonyCallback unregistration requires keeping reference
            } else {
                @Suppress("DEPRECATION")
                tm.listen(null, PhoneStateListener.LISTEN_NONE)
            }
            registered = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister call state listener", e)
        }
    }

    private fun handleCallStateChange(state: Int) {
        val now = System.currentTimeMillis()
        val number = lastNumber
        lastState = state

        when (state) {
            CALL_STATE_IDLE -> {
                // 通话结束
                if (callStartTime > 0) {
                    val duration = (now - callStartTime) / 1000
                    if (duration > 0) {
                        scope.launch {
                            saveCall(number, 2 /* outgoing */, duration.toInt(), callStartTime)
                        }
                    }
                    callStartTime = 0
                }
                ringingStartTime = 0
            }
            CALL_STATE_RINGING -> {
                // 来电响铃
                ringingStartTime = now
            }
            CALL_STATE_OFFHOOK -> {
                // 接通
                if (ringingStartTime > 0) {
                    // 来电接通
                    callStartTime = now
                    scope.launch {
                        saveCall(number, 1 /* incoming */, 0, now)
                    }
                    ringingStartTime = 0
                } else if (callStartTime == 0L) {
                    // 去电接通（PROCESS_OUTGOING_CALLS 广播会先触发）
                    callStartTime = now
                }
            }
        }
    }

    private suspend fun saveCall(number: String?, type: Int, duration: Int, time: Long) {
        try {
            val db = XiangQinApp.instance.database
            // Check for duplicates
            val existing = db.callDao().getCalls(limit = 5)
            val isDuplicate = existing.any {
                it.phoneNumber == (number ?: "unknown") &&
                it.callType == type &&
                kotlin.math.abs(it.callTime - time) < 5000
            }
            if (!isDuplicate) {
                db.callDao().insertAll(
                    listOf(
                        CallEntity(
                            phoneNumber = number ?: "unknown",
                            callerName = null,
                            callType = type,
                            durationSeconds = duration,
                            callTime = time
                        )
                    )
                )
                Log.d(TAG, "Saved call: type=$type num=$number dur=${duration}s")
                EventBroadcaster.broadcast("new_call", """{"type":$type,"number":"${number ?: "unknown"}"}""")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save call", e)
        }
    }
}

/**
 * 📞 去电广播接收器
 * 监听 PROCESS_OUTGOING_CALLS 获取去电号码
 */
class OutgoingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_NEW_OUTGOING_CALL) return

        val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: return
        if (number.isBlank()) return

        val now = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                XiangQinApp.instance.database.callDao().insertAll(
                    listOf(
                        CallEntity(
                            phoneNumber = number,
                            callerName = null,
                            callType = 2, // outgoing
                            durationSeconds = 0, // will be updated when call ends
                            callTime = now
                        )
                    )
                )
                Log.d("OutgoingCall", "Saved outgoing call to $number")
                EventBroadcaster.broadcast("new_call", """{"type":2,"number":"$number"}""")
            } catch (e: Exception) {
                Log.e("OutgoingCall", "Failed to save outgoing call", e)
            }
        }
    }
}
