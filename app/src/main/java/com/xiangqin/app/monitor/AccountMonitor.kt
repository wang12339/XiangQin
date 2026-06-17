package com.xiangqin.app.monitor

import android.Manifest
import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.xiangqin.app.XiangQinApp
import com.xiangqin.app.data.db.AccountEntity

/**
 * 账户信息采集器
 * 通过 AccountManager 读取设备上所有已登录账户
 */
class AccountMonitor(private val context: Context) {

    private val app get() = XiangQinApp.instance

    /** 是否有 GET_ACCOUNTS 权限 */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.GET_ACCOUNTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 采集并存储设备账户信息 */
    suspend fun sync() {
        if (!hasPermission()) return

        val db = app.database
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.accounts ?: return

        val now = System.currentTimeMillis()

        for (account in accounts) {
            val existing = db.accountDao().getByKey(account.name, account.type)
            if (existing != null) {
                // 账户已存在，更新 lastSeen
                db.accountDao().update(existing.copy(lastSeen = now))
            } else {
                // 新账户，插入记录
                db.accountDao().insert(
                    AccountEntity(
                        accountName = account.name,
                        accountType = account.type,
                        firstSeen = now,
                        lastSeen = now
                    )
                )
            }
        }
    }
}
