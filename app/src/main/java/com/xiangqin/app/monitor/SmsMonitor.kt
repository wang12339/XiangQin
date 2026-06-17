package com.xiangqin.app.monitor

import android.content.Context
import android.provider.Telephony
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.SmsEntity

/**
 * 短信采集器
 * 读取 SMS ContentProvider，增量写入 Room
 */
class SmsMonitor(private val context: Context) {

    suspend fun sync() {
        val db = XiangQinApp.instance.database
        val lastSync = db.smsDao().getLastSyncTime() ?: 0L

        // Read inbox
        syncFromUri(
            Telephony.Sms.Inbox.CONTENT_URI,
            Telephony.Sms.Inbox.DATE,
            lastSync,
            smsType = 1,
            db
        )

        // Read sent
        syncFromUri(
            Telephony.Sms.Sent.CONTENT_URI,
            Telephony.Sms.Sent.DATE,
            lastSync,
            smsType = 2,
            db
        )
    }

    private suspend fun syncFromUri(
        uri: android.net.Uri,
        dateColumn: String,
        lastSync: Long,
        smsType: Int,
        db: com.xiangqin.app.data.db.AppDatabase
    ) {
        val cursor = context.contentResolver.query(
            uri,
            null,
            "$dateColumn > ?",
            arrayOf(lastSync.toString()),
            "$dateColumn ASC"
        )

        cursor?.use {
            val smsList = mutableListOf<SmsEntity>()
            while (it.moveToNext()) {
                smsList.add(
                    SmsEntity(
                        phoneNumber = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "",
                        senderName = null,
                        body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
                        smsType = smsType,
                        receivedTime = it.getLong(it.getColumnIndexOrThrow(dateColumn))
                    )
                )
            }
            if (smsList.isNotEmpty()) {
                db.smsDao().insertAll(smsList)
            }
        }
    }
}
