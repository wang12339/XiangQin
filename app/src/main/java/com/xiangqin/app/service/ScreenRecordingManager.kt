package com.xiangqin.app.service

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.io.File

object ScreenRecordingManager {
    private var service: ScreenRecordingService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as ScreenRecordingService.LocalBinder).getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    fun start(context: Context, resultCode: Int, data: Intent) {
        // 先绑定服务
        val intent = Intent(context, ScreenRecordingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        
        // 启动服务
        val startIntent = Intent(context, ScreenRecordingService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("resultData", data)
        }
        context.startService(startIntent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP
        }
        context.startService(intent)
        
        if (bound) {
            context.unbindService(connection)
            bound = false
        }
    }

    fun isRecording(): Boolean = service?.isRecording == true

    fun getOutputFile(): File? = service?.outputFile
}
