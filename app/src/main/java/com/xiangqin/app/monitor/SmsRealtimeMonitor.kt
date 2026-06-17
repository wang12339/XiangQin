package com.xiangqin.app.monitor

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.SmsDao
import com.xiangqin.app.data.db.SmsEntity
import com.xiangqin.app.server.EventBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * 📨 SMS 分类器
 * 参考 360 手机卫士的短信分类逻辑
 */
object SmsClassifier {

    // 验证码关键词
    private val VERIFICATION_PATTERNS = listOf(
        Pattern.compile("验证码|校验码|动态码|安全码|验证代码|确认码", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(\\d{4,8})\\b.*验证|验证.*\\b(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("code|verification|captcha|otp|2fa|mfa", Pattern.CASE_INSENSITIVE),
        Pattern.compile("你的.*码.*\\d{4,}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("登录验证|注册验证|身份验证|短信验证", Pattern.CASE_INSENSITIVE)
    )

    // 银行/金融
    private val BANK_PATTERNS = listOf(
        Pattern.compile("银行|信用卡|储蓄卡|借记卡|贷款|还款|账单|消费|支出|收入|转账", Pattern.CASE_INSENSITIVE),
        Pattern.compile("icbc|ccb|abc|boc|cmb|psbc|ceb|cib|spdb|pingan|中信|光大|招商|民生|浦发", Pattern.CASE_INSENSITIVE),
        Pattern.compile("支付宝|余额宝|花呗|借呗|微信支付|财付通", Pattern.CASE_INSENSITIVE),
        Pattern.compile("交易|扣款|到账|退款|支付|付款|收款", Pattern.CASE_INSENSITIVE)
    )

    // 营销推广
    private val PROMO_PATTERNS = listOf(
        Pattern.compile("优惠|折扣|促销|特价|限时|秒杀|抢购|大促", Pattern.CASE_INSENSITIVE),
        Pattern.compile("会员|VIP|积分|兑换|抽奖|福利|礼包|红包", Pattern.CASE_INSENSITIVE),
        Pattern.compile("订阅|退订|回复TD|回复T", Pattern.CASE_INSENSITIVE),
        Pattern.compile("广告|推广|营销|推荐|好物|精选", Pattern.CASE_INSENSITIVE),
        Pattern.compile("【.*广告.*】|【.*推荐.*】", Pattern.CASE_INSENSITIVE)
    )

    // 通知/提醒
    private val NOTIFICATION_PATTERNS = listOf(
        Pattern.compile("提醒|通知|通告|公告|温馨提示", Pattern.CASE_INSENSITIVE),
        Pattern.compile("快递|物流|配送|发货|签收|取件", Pattern.CASE_INSENSITIVE),
        Pattern.compile("预约|挂号|就诊|取药|检查", Pattern.CASE_INSENSITIVE),
        Pattern.compile("欠费|缴费|充值|余额|套餐|流量", Pattern.CASE_INSENSITIVE)
    )

    fun classify(body: String, sender: String?): String {
        if (body.isBlank()) return "unknown"

        // 先检查是否是验证码（优先级最高）
        if (VERIFICATION_PATTERNS.any { it.matcher(body).find() }) return "verification"

        // 银行
        if (BANK_PATTERNS.any { it.matcher(body).find() }) return "bank"

        // 通知
        if (NOTIFICATION_PATTERNS.any { it.matcher(body).find() }) return "notification"

        // 营销推广
        if (PROMO_PATTERNS.any { it.matcher(body).find() }) return "promo"

        return "personal"
    }
}

/**
 * 📨 实时短信接收 BroadcastReceiver
 * 拦截收到的短信并立即存入数据库
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pdus = intent.getParcelableArrayExtra("pdus") ?: return
        val smsList = mutableListOf<SmsEntity>()
        val now = System.currentTimeMillis()

        for (pdu in pdus) {
            try {
                val msg = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    ?: continue

                // 拼接长短信
                val fullBody = msg.joinToString("") { it.messageBody ?: "" }
                val sender = msg.firstOrNull()?.originatingAddress ?: "unknown"

                val category = SmsClassifier.classify(fullBody, sender)

                smsList.add(
                    SmsEntity(
                        phoneNumber = sender,
                        senderName = null,
                        body = fullBody,
                        smsType = 1, // inbox
                        receivedTime = now,
                        smsCategory = category
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse SMS PDU", e)
            }
        }

        if (smsList.isNotEmpty()) {
            scope.launch {
                try {
                    XiangQinApp.instance.database.smsDao().insertAll(smsList)
                    Log.d(TAG, "Saved ${smsList.size} real-time SMS (receive)")
                    EventBroadcaster.broadcast("new_sms", """{"count":${smsList.size}}""")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save SMS", e)
                }
            }
        }
    }
}

/**
 * 📨 发件箱 ContentObserver
 * 监控已发送短信的 ContentProvider
 */
class SentSmsObserver(
    private val contentResolver: ContentResolver,
    private val smsDao: SmsDao
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastSyncTime = System.currentTimeMillis()

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        scope.launch {
            try {
                val cursor = contentResolver.query(
                    Telephony.Sms.Sent.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE
                    ),
                    "${Telephony.Sms.DATE} > ?",
                    arrayOf(lastSyncTime.toString()),
                    "${Telephony.Sms.DATE} ASC"
                ) ?: return@launch

                val smsList = mutableListOf<SmsEntity>()
                while (cursor.moveToNext()) {
                    val body = cursor.getString(1) ?: continue
                    val time = cursor.getLong(2)
                    if (time <= lastSyncTime) continue
                    smsList.add(
                        SmsEntity(
                            phoneNumber = cursor.getString(0) ?: "",
                            senderName = null,
                            body = body,
                            smsType = 2, // sent
                            receivedTime = time,
                            smsCategory = SmsClassifier.classify(body, null)
                        )
                    )
                    if (time > lastSyncTime) lastSyncTime = time
                }
                cursor.close()
                if (smsList.isNotEmpty()) {
                    smsDao.insertAll(smsList)
                    Log.d("SentSmsObserver", "Saved ${smsList.size} sent SMS")
                }
            } catch (e: Exception) {
                Log.e("SentSmsObserver", "Failed to observe sent SMS", e)
            }
        }
    }
}
