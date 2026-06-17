package com.xiangqin.app.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.CalendarEventEntity

/**
 * 📅 日历事件采集器
 * 读取 Calendar ContentProvider，增量写入 Room
 * 按 syncId 去重
 */
class CalendarMonitor(private val context: Context) {

    companion object {
        /** 列投影 — 只取需要的字段 */
        private val PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY
        )
    }

    /** 是否有读取日历权限 */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 同步日历事件 */
    suspend fun sync() {
        if (!hasPermission()) return

        val db = XiangQinApp.instance.database
        val lastSync = db.calendarEventDao().getRecent(1)
            .firstOrNull()?.startTime ?: 0L

        val selection = "${CalendarContract.Events.DTSTART} > ?"
        val selectionArgs = arrayOf(lastSync.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            PROJECTION,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            while (it.moveToNext()) {
                val syncId = it.getLong(
                    it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                )

                // 按 syncId 去重
                if (syncId != 0L && db.calendarEventDao().getBySyncId(syncId) != null) {
                    continue
                }

                val event = CalendarEventEntity(
                    calendarTitle = it.getString(
                        it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                    ),
                    eventTitle = it.getString(
                        it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                    ) ?: "",
                    eventDescription = it.getString(
                        it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
                    ),
                    eventLocation = it.getString(
                        it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                    ),
                    startTime = it.getLong(
                        it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                    ),
                    endTime = if (it.isNull(
                            it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                        )
                    ) null
                    else it.getLong(
                        it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                    ),
                    allDay = it.getInt(
                        it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                    ) != 0,
                    syncId = syncId
                )

                db.calendarEventDao().insert(event)
            }
        }
    }
}
