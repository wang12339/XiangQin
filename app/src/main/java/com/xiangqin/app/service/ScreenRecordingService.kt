package com.xiangqin.app.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import com.xiangqin.app.MainActivity
import com.xiangqin.app.R
import com.xiangqin.app.XiangQinApp
import kotlinx.coroutines.*
import java.io.File

class ScreenRecordingService : Service() {

    companion object {
        private const val TAG = "XiangQin/ScreenRecord"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_recording"
        
        const val ACTION_START = "com.xiangqin.app.START_RECORDING"
        const val ACTION_STOP = "com.xiangqin.app.STOP_RECORDING"
        
        var instance: ScreenRecordingService? = null
        
        fun isRecording(): Boolean = instance?.isRecording == true
        
        fun getOutputFile(): File? = instance?.outputFile
    }

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var encoderSurface: android.view.Surface? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    var isRecording = false
    var outputFile: File? = null
    private var videoTrackIndex = -1
    private var muxerStarted = false
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordingService = this@ScreenRecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            "START" -> {
                val resultCode = intent.getIntExtra("resultCode", -1)
                val resultData = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("resultData", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("resultData")
                }
                if (resultCode != -1 && resultData != null) {
                    startRecording(resultCode, resultData)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "屏幕录制", NotificationManager.IMPORTANCE_LOW).apply {
                description = "屏幕录制中"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕录制中")
            .setContentText("点击停止录制")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", createStopPendingIntent())
            .setOngoing(true)
            .build()
    }

    private fun createStopPendingIntent(): PendingIntent {
        val intent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    fun startRecording(resultCode: Int, resultData: Intent) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        try {
            // 创建通知
            startForeground(NOTIFICATION_ID, createNotification())

            // 初始化 MediaProjection
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped")
                    stopRecording()
                }
            }, null)

            // 创建输出文件
            val dir = File(filesDir, "recordings")
            if (!dir.exists()) dir.mkdirs()
            val fileName = "recording_${System.currentTimeMillis()}.mp4"
            outputFile = File(dir, fileName)

            // 启动编码
            startEncoding()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            stopRecording()
        }
    }

    private fun startEncoding() {
        try {
            // 创建 MediaMuxer
            mediaMuxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 创建 MediaFormat
            val width = 720
            val height = 1280
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 2000000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            // 创建 MediaCodec
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = mediaCodec!!.createInputSurface()
            mediaCodec!!.start()

            // 创建 VirtualDisplay
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "ScreenRecorder",
                width, height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                encoderSurface,
                null,
                backgroundHandler
            )

            isRecording = true

            // 启动编码线程
            scope.launch { encodeFrame() }

            Log.i(TAG, "Recording started: ${outputFile!!.absolutePath}")
            
            // 保存到数据库
            try {
                scope.launch {
                    XiangQinApp.instance.database.systemLogDao().insert(
                        com.xiangqin.app.data.db.SystemLogEntity(
                            logType = "screen_record",
                            message = "录屏开始: ${outputFile!!.absolutePath}",
                            createdTime = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) { Log.e(TAG, "记录录屏开始失败: ${e.message}") }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start encoding: ${e.message}")
            stopRecording()
        }
    }

    private suspend fun encodeFrame() = withContext(Dispatchers.IO) {
        val bufferInfo = MediaCodec.BufferInfo()
        
        while (isRecording) {
            try {
                val outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000) ?: break
                
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        delay(10)
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            val newFormat = mediaCodec?.outputFormat
                            videoTrackIndex = mediaMuxer?.addTrack(newFormat!!) ?: -1
                            mediaMuxer?.start()
                            muxerStarted = true
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val encodedData = mediaCodec?.getOutputBuffer(outputBufferIndex)
                        if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                        mediaCodec?.releaseOutputBuffer(outputBufferIndex, false)
                        
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Encode error: ${e.message}")
                break
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        
        isRecording = false

        try {
            virtualDisplay?.release()
            virtualDisplay = null

            encoderSurface?.release()
            encoderSurface = null

            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null

            if (muxerStarted) {
                mediaMuxer?.stop()
            }
            mediaMuxer?.release()
            mediaMuxer = null

            mediaProjection?.stop()
            mediaProjection = null

            muxerStarted = false
            videoTrackIndex = -1
            
            Log.i(TAG, "Recording stopped: ${outputFile?.absolutePath}")
            
            // 保存到数据库
            try {
                scope.launch {
                    XiangQinApp.instance.database.systemLogDao().insert(
                        com.xiangqin.app.data.db.SystemLogEntity(
                            logType = "screen_record",
                            message = "录屏结束: ${outputFile?.absolutePath}, 大小: ${outputFile?.length()}",
                            createdTime = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) { Log.e(TAG, "记录录屏结束失败: ${e.message}") }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
