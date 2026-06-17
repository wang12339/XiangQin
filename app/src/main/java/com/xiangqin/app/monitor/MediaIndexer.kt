package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.MediaFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🖼️ 媒体文件索引器
 * 扫描 ContentResolver (MediaStore) 中的图片、视频、音频，
 * 增量写入 Room 数据库用于本地搜索和统计。
 */
class MediaIndexer(private val context: Context) {

    private val app get() = XiangQinApp.instance

    // ── Permission ──────────────────────────────────────────────

    /** 检查是否拥有读取媒体文件所需的全部权限 */
    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** 检查单一媒体类型的权限 */
    fun hasPermissionFor(mediaType: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (mediaType) {
                "image" -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                "video" -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                "audio" -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                else -> hasPermission()
            }
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ── Sync (full scan) ────────────────────────────────────────

    /**
     * 全量扫描 MediaStore 中新增的图片、视频、音频，写入数据库。
     * 只插入数据库中尚不存在的文件路径（增量）。
     */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext SyncResult(0, 0, 0, emptyList())
        }

        val dao = app.database.mediaFileDao()
        val errors = mutableListOf<String>()

        val imagesInserted = scanAndInsert(MediaType.IMAGE, dao, errors)
        val videosInserted = scanAndInsert(MediaType.VIDEO, dao, errors)
        val audioInserted = scanAndInsert(MediaType.AUDIO, dao, errors)

        SyncResult(imagesInserted, videosInserted, audioInserted, errors)
    }

    /**
     * 增量扫描 — 只处理 dateAdded 大于最后一条记录的媒体。
     */
    suspend fun syncIncremental(): SyncResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext SyncResult(0, 0, 0, emptyList())
        }

        val dao = app.database.mediaFileDao()
        val errors = mutableListOf<String>()

        val imagesInserted = scanAndInsert(MediaType.IMAGE, dao, errors, incremental = true)
        val videosInserted = scanAndInsert(MediaType.VIDEO, dao, errors, incremental = true)
        val audioInserted = scanAndInsert(MediaType.AUDIO, dao, errors, incremental = true)

        SyncResult(imagesInserted, videosInserted, audioInserted, errors)
    }

    // ── Stats ────────────────────────────────────────────────────

    /** 获取各类型媒体数量 */
    suspend fun getCounts(): MediaCounts = withContext(Dispatchers.IO) {
        val dao = app.database.mediaFileDao()
        MediaCounts(
            images = dao.countByType("image"),
            videos = dao.countByType("video"),
            audio = dao.countByType("audio")
        )
    }

    // ── Internals ────────────────────────────────────────────────

    private suspend fun scanAndInsert(
        type: MediaType,
        dao: com.xiangqin.app.data.db.MediaFileDao,
        errors: MutableList<String>,
        incremental: Boolean = false
    ): Int {
        if (!hasPermissionFor(type.mediaName)) return 0

        val uri = type.uri
        val projection = type.projection
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} ASC"

        // 增量模式：只取 dateAdded > 最新记录的媒体
        val selection: String? = if (incremental) {
            // 获取当前库中该类型的最大 dateAdded
            val lastDate = getLastDateForType(type.mediaName, dao)
            if (lastDate != null) "${MediaStore.MediaColumns.DATE_ADDED} > ?" else null
        } else {
            null
        }
        val selectionArgs: Array<String>? = if (selection != null) {
            arrayOf(getLastDateForType(type.mediaName, dao)?.toString() ?: "0")
        } else {
            null
        }

        val cursor = try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
        } catch (e: SecurityException) {
            errors.add("SecurityException querying ${type.mediaName}: ${e.message}")
            return 0
        } catch (e: Exception) {
            errors.add("Error querying ${type.mediaName}: ${e.message}")
            return 0
        }

        cursor?.use { c ->
            val list = mutableListOf<MediaFileEntity>()

            val colPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val colName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val colSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val colMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val colAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val colModified = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            // 可选列 - 根据类型获取 duration 列索引
            val colDuration = when (type) {
                MediaType.VIDEO -> c.getColumnIndex(MediaStore.Video.Media.DURATION)
                MediaType.AUDIO -> c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                else -> -1  // IMAGE 类型没有 duration 列
            }
            val colLatitude = c.getColumnIndex("latitude")
            val colLongitude = c.getColumnIndex("longitude")

            while (c.moveToNext()) {
                val filePath = c.getString(colPath) ?: continue
                // 跳过空路径或无效文件
                if (filePath.isBlank()) continue

                val entity = MediaFileEntity(
                    filePath = filePath,
                    fileName = c.getString(colName) ?: "unknown",
                    fileSize = c.getLong(colSize),
                    mimeType = c.getString(colMime),
                    dateAdded = c.getLong(colAdded) * 1000L,  // 秒 → 毫秒
                    dateModified = c.getLong(colModified) * 1000L,
                    mediaType = type.mediaName,
                    durationMs = if (colDuration >= 0) {
                        val raw = c.getLong(colDuration)
                        if (raw > 0) raw else null
                    } else null,
                    latitude = if (colLatitude >= 0) {
                        val lat = c.getDouble(colLatitude)
                        if (!lat.isNaN()) lat else null
                    } else null,
                    longitude = if (colLongitude >= 0) {
                        val lon = c.getDouble(colLongitude)
                        if (!lon.isNaN()) lon else null
                    } else null
                )
                list.add(entity)

                // 批量写入，避免 OOM
                if (list.size >= BATCH_SIZE) {
                    try {
                        dao.insertAll(list)
                    } catch (_: Exception) {
                        // ignore duplicate / constraint violations
                    }
                    list.clear()
                }
            }

            // 写入剩余
            if (list.isNotEmpty()) {
                try {
                    dao.insertAll(list)
                } catch (e: Exception) { android.util.Log.e("XiangQin", "Error", e) }
            }
            return c.count // 返回扫描到的条数（不含去重）
        }

        return 0
    }

    /** 获取某类型最新记录的 dateAdded（毫秒） */
    private suspend fun getLastDateForType(type: String, dao: com.xiangqin.app.data.db.MediaFileDao): Long? {
        return try {
            val recent = dao.getByType(type, limit = 1)
            recent.firstOrNull()?.dateAdded
        } catch (_: Exception) {
            null
        }
    }

    // ── Data classes ─────────────────────────────────────────────

    /** 单次同步结果 */
    data class SyncResult(
        val imagesInserted: Int,
        val videosInserted: Int,
        val audioInserted: Int,
        val errors: List<String>
    ) {
        val totalInserted: Int get() = imagesInserted + videosInserted + audioInserted

        override fun toString(): String =
            "SyncResult(images=$imagesInserted, videos=$videosInserted, audio=$audioInserted, errors=${errors.size})"
    }

    /** 各类型媒体数量统计 */
    data class MediaCounts(
        val images: Int,
        val videos: Int,
        val audio: Int
    ) {
        val total: Int get() = images + videos + audio
    }

    // ── Constants ────────────────────────────────────────────────

    companion object {
        private const val BATCH_SIZE = 200

        /** 媒体类型枚举 — 封装投影列和 URI */
        private enum class MediaType(val mediaName: String) {
            IMAGE("image"),
            VIDEO("video"),
            AUDIO("audio");

            val uri: android.net.Uri get() = when (this) {
                IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection: Array<String> get() = when (this) {
                IMAGE -> arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    "latitude",
                    "longitude"
                )
                VIDEO -> arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.Video.Media.DURATION,
                    "latitude",
                    "longitude"
                )
                AUDIO -> arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.Audio.Media.DURATION
                )
            }
        }
    }
}
