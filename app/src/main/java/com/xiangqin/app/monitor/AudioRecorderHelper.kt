package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 🎤 远程音频录制器
 *
 * 使用 MediaRecorder 录制来自 MIC 的音频，输出 AAC 格式，
 * 文件自动保存在应用内部文件目录下。
 *
 * 使用方式：
 *   val helper = AudioRecorderHelper()
 *   val path = helper.start(context)   // 返回文件路径 or null
 *   helper.stop()                       // 返回 AudioResult or null
 */
class AudioRecorderHelper {

    // ===================== 状态 =====================

    /** 当前是否正在录制 */
    @Volatile
    var isRecording: Boolean = false
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    private var startTimeMs: Long = 0L

    // ===================== 数据类 =====================

    /**
     * 录制结果。
     * @param filePath 录制文件的绝对路径
     * @param durationMs 录制时长（毫秒）
     * @param fileSize 文件大小（字节）
     */
    data class AudioResult(
        val filePath: String,
        val durationMs: Long,
        val fileSize: Long
    )

    // ===================== 权限检查 =====================

    /**
     * 检查是否拥有 RECORD_AUDIO 权限。
     */
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ===================== 启动录制 =====================

    /**
     * 开始录制音频。
     *
     * 自动在 [context.filesDir]/audio/ 下生成带时间戳的文件名。
     * 若缺少 RECORD_AUDIO 权限或初始化失败，静默返回 null。
     *
     * @param context 应用 Context（用于权限检查及确定内部文件目录）
     * @return 录制文件路径，失败返回 null
     */
    fun start(context: Context): String? {
        if (isRecording) {
            stop()
        }

        if (!hasPermission(context)) {
            return null
        }

        return try {
            // 确保目录存在
            val audioDir = File(context.filesDir, "audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }

            // 生成带时间戳的文件名
            val timestamp = System.currentTimeMillis()
            val outputFile = File(audioDir, "recording_${timestamp}.m4a")
            val outputPath = outputFile.absolutePath

            val recorder = MediaRecorder()
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputPath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentFilePath = outputPath
            startTimeMs = System.currentTimeMillis()
            isRecording = true

            outputPath
        } catch (_: Exception) {
            // 清理失败的 recorder
            try {
                mediaRecorder?.reset()
                mediaRecorder?.release()
            } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
            mediaRecorder = null
            currentFilePath = null
            isRecording = false
            null
        }
    }

    // ===================== 停止录制 =====================

    /**
     * 停止录制并返回结果。
     *
     * 若当前未在录制，返回 null。
     * 所有异常静默捕获，确保 MediaRecorder 资源被释放。
     *
     * @return [AudioResult] 包含文件路径、时长（毫秒）和文件大小（字节），失败返回 null
     */
    fun stop(): AudioResult? {
        if (!isRecording) {
            return null
        }

        val filePath = currentFilePath
        val elapsed = System.currentTimeMillis() - startTimeMs

        return try {
            // 停止录制
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                    // stop() 可能在无有效数据时抛异常，静默忽略
                }
                reset()
                release()
            }
            mediaRecorder = null
            isRecording = false
            currentFilePath = null

            // 构建结果
            if (filePath != null) {
                val file = File(filePath)
                val fileSize = if (file.exists()) file.length() else 0L
                AudioResult(
                    filePath = filePath,
                    durationMs = elapsed,
                    fileSize = fileSize
                )
            } else {
                null
            }
        } catch (_: Exception) {
            // 兜底清理
            try {
                mediaRecorder?.release()
            } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
            mediaRecorder = null
            isRecording = false
            currentFilePath = null
            null
        }
    }

    // ===================== 工具方法 =====================

    /**
     * 停止录制并丢弃结果（不返回 [AudioResult]）。
     * 适用于主动取消录制等场景。
     */
    fun cancel() {
        stop()
    }
}
