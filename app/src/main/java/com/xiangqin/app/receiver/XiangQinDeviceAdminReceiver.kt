package com.xiangqin.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.widget.Toast

/**
 * 设备管理器 Receiver
 *
 * ### 作用
 * 1. **防卸载** — 激活后，卸载前必须先停用设备管理器，多一层保护
 * 2. **远程锁屏** — 配合 WebServer 暴露的 API，支持远程锁屏
 * 3. **取消激活拦截** — 弹出警告，增加停用难度
 *
 * ### MIUI 适配
 * - MIUI 14/HyperOS 对设备管理器有特殊处理，停用时系统会自动弹警告
 * - 配合「安全 → 应用锁」锁定设置页面效果更佳
 *
 * ### 使用方法
 * 用户需手动到「设置 → 安全 → 设备管理器」或通过 APP 内引导激活
 */
class XiangQinDeviceAdminReceiver : DeviceAdminReceiver() {

    /**
     * 用户试图停用设备管理器时触发
     * 返回一个 CharSequence 作为警告文字（不为 null 就会弹对话框）
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "停用设备管理器后，应用将可以被卸载。\n\n" +
               "请确认您真的要停用吗？\n" +
               "如果只是想停止监控，请使用 APP 内的「停止监控」按钮。"
    }

    /**
     * 设备管理器已激活
     */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "✅ 设备管理器已激活，防卸载保护已启用", Toast.LENGTH_LONG).show()
    }

    /**
     * 设备管理器已停用
     */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "⚠️ 设备管理器已停用，防卸载保护已关闭", Toast.LENGTH_LONG).show()
    }

    /**
     * 密码变更
     */
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
    }

    /**
     * 密码失败
     */
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
    }

    companion object {
        /**
         * 获取设备管理器组件名
         */
        @JvmStatic
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, XiangQinDeviceAdminReceiver::class.java)
        }

        /**
         * 是否已激活
         */
        @JvmStatic
        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(getComponentName(context))
        }

        /**
         * 打开激活页面
         */
        @JvmStatic
        fun openActivation(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = getComponentName(context)

            if (dpm.isAdminActive(componentName)) return

            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "激活设备管理器后：\n" +
                    "1️⃣ 防止应用被误卸载\n" +
                    "2️⃣ 支持远程锁屏功能\n" +
                    "3️⃣ 提升后台服务稳定性\n\n" +
                    "此功能不会收集您的任何隐私信息，仅用于安全防护。"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /**
         * 远程锁屏 — 通过 Web API 调用
         */
        @JvmStatic
        fun lockScreen(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                if (dpm.isAdminActive(getComponentName(context))) {
                    dpm.lockNow()
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * 设置锁屏密码（可选）
         */
        @JvmStatic
        fun setLockPassword(context: Context, password: String): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                if (dpm.isAdminActive(getComponentName(context))) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        dpm.resetPassword(password, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)
                    } else {
                        dpm.resetPassword(password, 0)
                    }
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
