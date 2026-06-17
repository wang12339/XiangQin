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

        return try {
            startBackgroundThread()
            takePicture()
        } catch (e: Exception) {
            logError("拍照异常: ${e.message}")
            null
        } finally {
            stopBackgroundThread()
        }
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
                                resultBitmap.set(imageToBitmap(image))
                                image.close()
                            } else {
                                errorMsg.set("ImageReader 返回空图片")
                            }
                            latch.countDown()
                        }, backgroundHandler)

                        val surface = imageReader!!.surface

                        val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureRequestBuilder.addTarget(surface)
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90)

                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    captureSession = session
                                    try {
                                        session.capture(captureRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(
                                                session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: android.hardware.camera2.TotalCaptureResult
                                            ) {
                                                Thread {
                                                    Thread.sleep(3000)
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
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val matrix = Matrix()
        matrix.postRotate(90f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
